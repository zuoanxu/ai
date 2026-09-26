package com.example.demo.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OrderTools {

    /** listOrders 最多返回的订单条数（后端有 8000+ 条，全量会撑爆模型上下文，这里只回最新 N 条摘要） */
    private static final int MAX_LIST_SIZE = 20;

    private final RestClient restClient;
    private final ToolCallLogger logger;
    // 这里不注入 Spring 的 ObjectMapper（这个环境没有自动装配该 Bean），直接 new 一个即可
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderTools(ToolCallLogger logger) {
        this.logger = logger;
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }

    /** 工具0：查订单列表（只回摘要，避免把 8000+ 条全塞给模型） */
    @Tool(description = "查询订单列表，返回最近若干条订单的摘要（订单ID、订单号、金额、状态）。当用户想查看或取消'我的订单'却不知道订单ID时，先调用本工具查出真实订单ID，再用 getOrderStatus / cancelOrder 操作。status 含义：0待支付、1已支付、2已取消")
    public String listOrders() {
        String result = execute(() -> {
            String json = restClient.get()
                    .uri("/api/orders")
                    .retrieve()
                    .body(String.class);
            return summarizeOrders(json);
        });
        logger.log("listOrders", "{}", result);
        return result;
    }

    /** 工具1：查订单状态 */
    @Tool(description = "查询订单状态：传入订单ID或订单号(如 ORD1790062219162494)，返回订单信息。结果里的 status 字段表示状态：0待支付、1已支付、2已取消")
    public String getOrderStatus(@ToolParam(description = "订单ID或订单号") String orderKey) {
        String result = execute(() -> {
            long orderId = resolveOrderId(orderKey);
            if (orderId < 0) {
                return "查询失败：订单 " + orderKey + " 不存在，请先通过 listOrders 查出真实的订单号或订单ID。";
            }
            return restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .retrieve()
                    .body(String.class);
        });
        logger.log("getOrderStatus", "{\"orderKey\": \"" + orderKey + "\"}", result);
        return result;
    }

    /** 工具2：查库存 */
    @Tool(description = "查询商品库存：传入商品ID，返回商品信息。结果里的 stock 字段是剩余库存数量")
    public String getProductStock(@ToolParam(description = "商品ID") long productId) {
        String result = execute(() -> restClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(String.class));
        logger.log("getProductStock", "{\"productId\": " + productId + "}", result);
        return result;
    }

    /** 工具3：取消订单（取消前先校验，避免"取消不存在的订单还返回成功"） */
    @Tool(description = "取消订单：传入订单号(如 ORD1790062219162494)或订单ID，取消一个待支付的订单并自动回补库存。只有待支付(status=0)的订单能取消")
    public String cancelOrder(@ToolParam(description = "订单号或订单ID") String orderKey) {
        String result = execute(() -> {
            // ① 先把「订单号 / 订单ID」统一解析成订单ID
            long orderId = resolveOrderId(orderKey);
            if (orderId < 0) {
                return "取消失败：订单 " + orderKey + " 不存在，请先通过 listOrders 查出真实的订单号或订单ID。";
            }
            // ② 先查订单，确认它存在、且状态是"待支付(0)"。
            //    后端 /cancel 对不存在的订单、已支付/已取消的订单统统返回 success，直接透传会误导模型。
            String orderJson = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .retrieve()
                    .body(String.class);
            String checkError = validateCancellable(orderId, orderJson);
            if (checkError != null) {
                return checkError;
            }
            // ③ 校验通过后再真正取消
            return restClient.post()
                    .uri("/api/orders/{id}/cancel", orderId)
                    .retrieve()
                    .body(String.class);
        });
        logger.log("cancelOrder", "{\"orderKey\": \"" + orderKey + "\"}", result);
        return result;
    }

    /**
     * 把「订单号(如 ORD1790062219162494) 或 订单ID(纯数字)」统一解析成订单ID。
     * - 纯数字 -> 直接当作订单ID
     * - 其它   -> 当作订单号，去订单列表里查出对应 ID
     * 查不到返回 -1。
     */
    private long resolveOrderId(String orderKey) {
        if (orderKey == null || orderKey.isBlank()) {
            return -1;
        }
        String key = orderKey.trim();
        // 纯数字 = 订单ID
        if (key.matches("\\d+")) {
            return Long.parseLong(key);
        }
        // 否则当作订单号，遍历订单列表找匹配项（后端没有按订单号查询的接口，只能列表里查）
        try {
            String json = restClient.get()
                    .uri("/api/orders")
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return -1;
            }
            for (JsonNode order : data) {
                if (key.equals(order.path("orderNo").asText())) {
                    return order.path("id").asLong();
                }
            }
        } catch (Exception e) {
            // 解析失败按"查不到"处理
        }
        return -1;
    }

    /**
     * 校验订单是否可取消：
     * - 订单不存在 -> 返回错误文案
     * - 状态不是待支付(0) -> 返回错误文案
     * - 可以取消 -> 返回 null
     */
    private String validateCancellable(long orderId, String orderJson) {
        try {
            JsonNode root = objectMapper.readTree(orderJson);
            // 订单不存在时后端返回 {"code":400,"message":"订单不存在","data":null}
            if (root.path("code").asInt(200) != 200 || root.path("data").isNull() || root.path("data").isMissingNode()) {
                return "取消失败：订单 " + orderId + " 不存在，请先通过 listOrders 查出真实订单ID。";
            }
            int status = root.path("data").path("status").asInt(-1);
            if (status != 0) {
                String statusText = switch (status) {
                    case 1 -> "已支付";
                    case 2 -> "已取消";
                    default -> "未知状态(" + status + ")";
                };
                return "取消失败：订单 " + orderId + " 当前状态是「" + statusText + "」，只有待支付(0)的订单才能取消。";
            }
            return null;
        } catch (Exception e) {
            return "取消失败：无法解析订单信息：" + e.getMessage();
        }
    }

    /**
     * 把后端返回的全量订单列表（8000+ 条）摘成最新 N 条的关键字段，
     * 避免把整包数据塞给模型导致上下文爆炸。
     */
    private String summarizeOrders(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return "查询订单列表失败：接口返回异常：" + json;
            }
            List<Map<String, Object>> orders = new ArrayList<>();
            int total = data.size();
            for (JsonNode order : data) {
                if (orders.size() >= MAX_LIST_SIZE) {
                    break;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderId", order.path("id").asLong());
                m.put("orderNo", order.path("orderNo").asText());
                m.put("totalAmount", order.path("totalAmount").asDouble());
                m.put("status", order.path("status").asInt());
                m.put("createTime", order.path("createTime").asText());
                orders.add(m);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total", total);          // 订单总数
            summary.put("shown", orders.size());  // 实际返回条数
            summary.put("orders", orders);        // 摘要列表（后端按最新在前排序）
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "查询订单列表失败：" + e.getMessage();
        }
    }

    /**
     * 统一包一层 try-catch：工具调用成功就返回结果，失败也把错误信息记下来。
     * Supplier 就是一个"能返回一个值"的小函数，这里用它把要执行的调用传进来。
     */
    private String execute(Supplier<String> call) {
        try {
            return call.get();
        } catch (Exception e) {
            return "调用失败：" + e.getMessage();
        }
    }
}
