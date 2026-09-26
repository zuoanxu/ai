package com.example.demo.config;

import com.example.demo.tool.OrderTools;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiConfig {

    /**
     * 向量库：负责存"资料片段 -> 向量"。
     * 这里用 PgVectorStore 把向量存进 PostgreSQL（pgvector 扩展），重启不丢、可持久化。
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)          // bge-m3 输出 1024 维，必须和模型一致
                .initializeSchema(true)    // 首次启动自动建表 + 启用 pgvector 扩展
                .build();
    }

    /**
     * ChatClient：Spring AI 的高层 API，用它来发起对话。
     * 它的 Builder 是 Spring AI 自动配置好的，我们只需要 build 出来注册成 Bean。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, OrderTools orderTools) {
        return builder
                .defaultTools(orderTools)   // ← 把 OrderTools 里的 3 个 @Tool 方法注册进来
                .build();
    }

    /**
     * 对话记忆：负责记住用户和助手的多轮对话历史。
     * MessageWindowChatMemory 是"滑动窗口"记忆：只保留最近 N 条，超出的自动丢弃，避免无限膨胀。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)   // 最多记 20 条消息（约 10 轮，一轮 = 一问一答 2 条）
                .build();
    }
}
