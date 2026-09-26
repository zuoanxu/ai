package com.example.demo.controller;

import com.example.demo.tool.ToolCallLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.service.AssistantService;
import com.example.demo.service.DocumentService;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final DocumentService documentService;
    private final ToolCallLogger toolCallLogger;   // ★ 新增

    public AssistantController(AssistantService assistantService, DocumentService documentService, ToolCallLogger toolCallLogger) {
        this.assistantService = assistantService;
        this.documentService = documentService;
        this.toolCallLogger = toolCallLogger;   // ★ 新增
    }

    // 喂资料：POST /assistant/knowledge   body: {"text": "..."}
    @PostMapping("/knowledge")
    public String addKnowledge(@RequestBody java.util.Map<String, String> body) {
        assistantService.addKnowledge(body.get("text"));
        return "已存入向量库";
    }

    // 提问：GET /assistant/ask?question=...
    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return assistantService.ask(question);
    }
    // 提问（POST 版）：POST /assistant/ask   body: {"question": "..."}
    @PostMapping("/ask")
    public String askByPost(@RequestBody Map<String, String> body) {
        String question = body.get("question");   // 从 JSON 里取出 question 字段
        return assistantService.askWithSystemPrompt(question);  // 想用哪个服务方法就填哪个
    }
    // 上传文件：POST /assistant/upload   （用表单的 file 字段上传）
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {

        // ① 校验：文件不能为空
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "文件不能为空"));
        }

        String filename = file.getOriginalFilename();

        // ② 校验：只允许常见的文档格式（白名单）
        if (filename == null || !isAllowedType(filename)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "不支持的文件类型：" + filename));
        }

        // ③ 交给 DocumentService 提取并入库（包一层 try-catch，出错时友好返回）
        try {
            int chunkCount = documentService.ingestFile(file.getResource());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "filename", filename,
                    "size", file.getSize(),
                    "chunks", chunkCount,
                    "message", "文件已提取并存入向量库"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "处理文件失败：" + e.getMessage()));
        }
    }//Spring AI Tool定义	在AI项目里写@Tool注解类，封装订单接口。注册到ChatClient

    // 判断文件后缀是否在允许列表里
    private boolean isAllowedType(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf")
                || lower.endsWith(".doc")  || lower.endsWith(".docx")
                || lower.endsWith(".xls")  || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt")  || lower.endsWith(".pptx")
                || lower.endsWith(".txt")  || lower.endsWith(".md");
    }
    // 手动检索版提问：GET /assistant/ask2?question=...
    @GetMapping("/ask2")
    public String ask2(@RequestParam String question) {
        return assistantService.askWithRetrieval(question);
    }
    // System Prompt 版提问：GET /assistant/ask3?question=...
    @GetMapping("/ask3")
    public String ask3(@RequestParam String question) {
        return assistantService.askWithSystemPrompt(question);
    }
    // 流式提问：GET /assistant/ask/stream?question=...
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestParam String question) {
        return assistantService.askStreamWithSystemPrompt(question);
    }
    // 工具调用可视化：提问 + 返回答案和本次工具调用记录
    @GetMapping("/ask-tools")
    public Map<String, Object> askTools(@RequestParam String question) {
        toolCallLogger.clear();   // ★ 先清空，保证返回的只有本次提问的工具调用
        String answer = assistantService.askWithTools(question);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("answer", answer);
        resp.put("toolCalls", toolCallLogger.getAll());
        return resp;
    }
    // 清空工具调用历史
    @DeleteMapping("/tools/history")
    public Map<String, Object> clearToolHistory() {
        toolCallLogger.clear();
        return Map.of("success", true);
    }
}