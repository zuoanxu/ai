package com.example.demo.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AssistantService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ChatMemory chatMemory;   // ★ 新增：对话记忆

    /** 相似度阈值：从配置文件 application.yml 读取，低于该值的片段会被过滤掉 */
    @Value("${app.similarity-threshold:0.4}")
    private double similarityThreshold;

    public AssistantService(ChatClient chatClient, VectorStore vectorStore, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;      // ★ 新增
    }

    /**
     * 把你的资料（一段文字）存进向量库。
     * 流程：切分成小片段 -> 每条算向量 -> 存进 VectorStore。
     */
    public void addKnowledge(String text) {
        // 1. 切成小片段（避免一段太长）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> documents = splitter.apply(List.of(new Document(text)));

        // 1.5. 给每个片段编号，和文件上传那条路保持一致
        for (int i = 0; i < documents.size(); i++) {
            documents.get(i).getMetadata().put("paragraph", i + 1);
        }

        // 2. 存进向量库（内部会自动算向量）
        vectorStore.add(documents);
    }

    /**
     * 带上下文地回答：先从向量库检索相关片段，再连同问题一起交给模型。
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())  // ← 关键：自动检索
                .call()
                .content();
    }
    /**
     * 手动检索版：自己调 similaritySearch 拿到资料，再拼进提示词问模型。
     * 这样你能清楚地看到"检索 -> 拼上下文 -> 问模型"三步。
     */
    public String askWithRetrieval(String question) {

        // 第 1 步：检索 —— 从向量库找出和问题最相关的片段
        List<Document> docs = vectorStore.similaritySearch(question);

        // 第 2 步：拼上下文 —— 把找到的片段文字用换行连成一大段
        String context = docs.stream()
                .map(Document::getText)      // 取出每条片段的文字
                .collect(Collectors.joining("\n"));   // 用换行拼接

        // 第 3 步：把"资料 + 问题"一起塞给模型
        String prompt = "请根据以下资料回答问题，资料里没有的就回答不知道。\n\n"
                + "【资料】\n" + context + "\n\n"
                + "【问题】" + question;

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
    /**
     * System Prompt 版：把检索到的资料放进系统提示词，
     * 用户问题单独作为 user 消息。
     */
    public String askWithSystemPrompt(String question) {

        // 第 1 步：检索 —— 带上相似度阈值，只保留相似度 >= 0.7 的片段
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(4)                  // 最多返回 4 段
                .similarityThreshold(similarityThreshold) // ★ 低于阈值（可配置）的片段直接过滤掉
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);

        // 第 1.5 步：★ 一条都没命中 → 说明最相关的相似度也 < 0.7，直接拒绝作答
        if (docs.isEmpty()) {
            return "抱歉，简历里没有足够相关的信息来回答这个问题，换个问法试试吧。";
        }

        // 第 2 步：拼上下文 —— 和上次一样
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 第 3 步：构建 System Prompt —— 这里是重点！
        //         前面写"人设和规则"，后面把资料拼进去
        String systemPrompt = "你是一个严谨的问答助手，请基于下面的参考资料回答用户问题。\n"
                + "规则：如果参考资料里没有答案，就明确说\"资料中没有相关信息\"，不要编造。\n\n"
                + "【参考资料】\n" + context;

        // 第 4 步：system 放资料，user 只放问题；advisors 里挂上对话记忆，实现多轮对话
        String answer = chatClient.prompt()
                .system(systemPrompt)   // ← 资料和规则放这里
                .user(question)         // ← 用户问题单独放这里
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, "default")   // ★ 单用户固定一个会话 id
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()))
                .call()
                .content();

        // 第 5 步：★ 后端拼引用（不依赖模型，保证格式），追加到答案末尾
        return answer + buildCitation(docs);
    }
    /**
     * 流式版：和 askWithSystemPrompt 逻辑一样，
     * 区别是返回 Flux<String>，模型边生成边往外吐字。
     */
    public Flux<String> askStreamWithSystemPrompt(String question) {

        // 第 1 步：检索 —— 带上相似度阈值，只保留相似度 >= 0.7 的片段
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(4)                  // 最多返回 4 段
                .similarityThreshold(similarityThreshold) // ★ 低于阈值（可配置）的片段直接过滤掉
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);

        // 第 1.5 步：★ 一条都没命中 → 说明最相关的相似度也 < 0.7，直接拒绝作答
        if (docs.isEmpty()) {
            return Flux.just("抱歉，简历里没有足够相关的信息来回答这个问题，换个问法试试吧。");
        }

        // 第 2 步：拼上下文
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 第 3 步：构建 System Prompt
        String systemPrompt = "你是一个严谨的问答助手，请基于下面的参考资料回答用户问题。\n"
                + "规则：如果参考资料里没有答案，就明确说\"资料中没有相关信息\"，不要编造。\n\n"
                + "【参考资料】\n" + context;

        // 第 4 步：重点！用 .stream() 流式返回（比原来的 .call() 多了一个 stream）
        Flux<String> answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, "default")   // ★ 单用户固定一个会话 id
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build()))
                .stream()          // ← 关键改动：原来这里是 .call()
                .content();

        // 第 5 步：★ 回答流结束后，再补一句引用文案（后端自己拼，保证格式）
        return answer.concatWith(Flux.just(buildCitation(docs)));
    }

    /**
     * 把检索到的片段拼成一段"引用原文"，让用户直接看到答案参考的是简历哪部分。
     * 例如：
     * "\n\n—— 参考了以下简历内容：\n[1] 张三，5 年 Java 开发……\n[2] 主导过 XX 项目……"
     */
    private String buildCitation(List<Document> docs) {
        if (docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n—— 参考了以下简历内容：");
        for (int i = 0; i < docs.size(); i++) {
            String text = docs.get(i).getText();
            // 片段太长就截断到 100 字，避免整段刷屏（想改显示长度就调这个 100）
            String shown = text.length() > 100 ? text.substring(0, 100) + "…" : text;
            sb.append("\n[").append(i + 1).append("] ").append(shown);
        }
        return sb.toString();
    }
}