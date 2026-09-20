package com.example.demo.tool;

import java.util.function.Supplier;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderTools {

    private final RestClient restClient;
    private final ToolCallLogger logger;   // ★ 新增：记录工具调用

    public OrderTools(ToolCallLogger logger) {
        this.logger = logger;
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }

    /** 工具1：查订单状态 */
    @Tool(description = "查询订单状态：传入订单ID，返回订单信息。结果里的 status 字段表示状态：0待支付、1已支付、2已取消")
    public String getOrderStatus(@ToolParam(description = "订单ID") long orderId) {
        String result = execute(() -> restClient.get()
                .uri("/api/orders/{id}", orderId)
                .retrieve()
                .body(String.class));
        logger.log("getOrderStatus", "{\"orderId\": " + orderId + "}", result);
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

    /** 工具3：取消订单 */
    @Tool(description = "取消订单：传入订单ID，取消一个待支付的订单并自动回补库存。只有待支付(status=0)的订单能取消")
    public String cancelOrder(@ToolParam(description = "订单ID") long orderId) {
        String result = execute(() -> restClient.post()
                .uri("/api/orders/{id}/cancel", orderId)
                .retrieve()
                .body(String.class));
        logger.log("cancelOrder", "{\"orderId\": " + orderId + "}", result);
        return result;
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