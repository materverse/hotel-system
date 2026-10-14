package com.cqucc.hotel.ai.prompt;

/**
 * 结构化输出的 JSON Schema 定义。
 * <p>用于约束模型输出格式，并在服务端做二次校验（不依赖模型自觉）。</p>
 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    /** 住宿报价单 Schema（与 QuoteValidator 的校验逻辑保持一致，金额单位为元）。 */
    public static final String QUOTE_SCHEMA = """
            {
              "type": "object",
              "required": ["quoteTitle", "typeName", "roomCount", "checkIn", "checkOut", "nights",
                           "nightlyBreakdown", "totalRoomAmount", "serviceFee", "discountAmount",
                           "depositAmount", "totalAmount", "validUntil", "notes", "riskNotice"],
              "properties": {
                "quoteTitle":  { "type": "string", "description": "报价单名称，例如 标准大床房 2 晚住宿报价" },
                "guestName":   { "type": "string", "description": "客人称呼（脱敏形式，如 张*明），可不填" },
                "typeName":    { "type": "string", "description": "房型名称，必须来自给定房型信息" },
                "roomCount":   { "type": "integer", "description": "房间数 1-10" },
                "checkIn":     { "type": "string", "description": "入住日期 yyyy-MM-dd" },
                "checkOut":    { "type": "string", "description": "离店日期 yyyy-MM-dd" },
                "nights":      { "type": "integer", "description": "间夜数（晚数）1-30" },
                "nightlyBreakdown": {
                  "type": "array",
                  "description": "每晚房价明细，长度必须等于 nights",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "required": ["date", "unitPrice", "priceSource"],
                    "properties": {
                      "date":        { "type": "string", "description": "日期 yyyy-MM-dd" },
                      "weekday":     { "type": "string", "description": "星期，如 周五" },
                      "unitPrice":   { "type": "string", "description": "该晚单价（元，两位小数）" },
                      "priceSource": { "type": "string", "description": "价格来源，如 门市价/会员价/周末价" }
                    }
                  }
                },
                "totalRoomAmount": { "type": "string", "description": "房费合计（元）" },
                "serviceFee":      { "type": "string", "description": "服务费或加床等附加费用（元），没有则写 0.00" },
                "discountAmount":  { "type": "string", "description": "优惠金额（元），没有则写 0.00" },
                "depositAmount":   { "type": "string", "description": "押金（元），按房型标准填写" },
                "totalAmount":     { "type": "string", "description": "应付合计（元）" },
                "breakfastIncluded": { "type": "boolean", "description": "是否含早餐" },
                "validUntil":      { "type": "string", "description": "报价有效期，例如 当日有效" },
                "notes":           { "type": "array", "items": { "type": "string" }, "description": "入住须知、退改政策、结算方式等" },
                "riskNotice":      { "type": "string", "description": "必须填写的提示，例如最终以入住当日房价策略为准" }
              }
            }
            """;

    /** OpenAI 兼容接口的 response_format 参数。 */
    public static final String JSON_OBJECT_RESPONSE_FORMAT = "{\"type\":\"json_object\"}";
}
