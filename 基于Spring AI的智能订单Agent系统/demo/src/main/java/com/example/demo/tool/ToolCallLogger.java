package com.example.demo.tool;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * 工具调用记录器：把 AI 每次调用工具（工具名、参数、返回结果）记下来，
 * 供前端「工具调用可视化」页面展示。
 */
@Component
public class ToolCallLogger {

    // CopyOnWriteArrayList：线程安全，防止并发调用工具时出问题
    private final List<Map<String, Object>> records = new CopyOnWriteArrayList<>();

    /** 记录一次工具调用（最新的放在最前面） */
    public void log(String toolName, String arguments, String result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", toolName);
        record.put("arguments", arguments);
        record.put("result", result);
        record.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        records.add(0, record);
        // 最多保留 50 条，避免无限增长
        if (records.size() > 50) {
            records.remove(records.size() - 1);
        }
    }

    /** 拿到全部记录 */
    public List<Map<String, Object>> getAll() {
        return records;
    }

    /** 清空记录 */
    public void clear() {
        records.clear();
    }
}