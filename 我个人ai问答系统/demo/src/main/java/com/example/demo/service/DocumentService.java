package com.example.demo.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 文档服务：负责把文件（PDF、Word、Excel…）用 Tika 提取成文本，
 * 切成小片段后存入向量库，供后面的问答检索使用。
 */
@Service
public class DocumentService {

    private final VectorStore vectorStore;

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 处理一个文件：提取文本 -> 切分 -> 存入向量库。
     *
     * @param resource 要处理的文件（本地文件、或上传的 MultipartFile 转来的 Resource）
     */
    public int ingestFile(Resource resource) {
        // 第 1 步：用 Tika 提取文本，得到 Spring AI 的 Document 列表（含正文 + 元数据）
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        // 第 2 步：切成小片段，避免一段太长影响检索效果
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        // 第 2.5 步：给每个片段编上"第几段"的序号，存进元数据，
        //           这样回答时检索到片段，就能知道它是简历的第几段
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("paragraph", i + 1);   // 从 1 开始：第 1 段、第 2 段……
        }

        // 第 3 步：存进向量库（内部会自动算向量）
        vectorStore.add(chunks);

        // 第 4 步：返回存进去的片段数量，让 Controller 能告诉用户结果
        return chunks.size();
    }
}