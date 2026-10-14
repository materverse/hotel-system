package com.cqucc.hotel.ai.agent;

/**
 * 工具定义。
 *
 * @param name        工具名（暴露给模型）
 * @param description 工具说明
 * @param parameters  参数的 JSON Schema
 * @param executor    执行逻辑
 */
public record ToolDefinition(String name, String description, String parameters, ToolExecutor executor) {

    /** 工具执行函数：入参为解析后的 JSON 字符串，返回给模型的可读文本。 */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String argumentsJson);
    }
}
