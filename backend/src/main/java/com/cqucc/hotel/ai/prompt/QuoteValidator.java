package com.cqucc.hotel.ai.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化输出校验器（住宿报价单）。
 * <p>模型输出必须通过本校验器才能写入数据库；校验不通过时触发一次"修复重试"，
 * 仍不通过则降级为人工处理（把原始输出交给前台确认），避免脏数据落库。</p>
 * <p>除字段完整性外，还做金额与日期的一致性校验：</p>
 * <ul>
 *   <li>每晚明细条数必须等于 nights，日期必须连续；</li>
 *   <li>房费合计必须等于各晚单价之和；</li>
 *   <li>应付合计必须等于房费 + 服务费 − 优惠。</li>
 * </ul>
 */
@Component
public class QuoteValidator {

    private final ObjectMapper json;

    public QuoteValidator(ObjectMapper json) {
        this.json = json;
    }

    /** 校验结果。 */
    public record Validation(boolean valid, String message, JsonNode node) {
    }

    public Validation validate(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new Validation(false, "模型未返回内容", null);
        }
        JsonNode root;
        try {
            root = json.readTree(stripCodeFence(rawOutput));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new Validation(false, "输出不是合法 JSON：" + e.getOriginalMessage(), null);
        } catch (Exception e) {
            return new Validation(false, "输出解析失败：" + e.getMessage(), null);
        }

        List<String> errors = new ArrayList<>();
        requireText(root, "quoteTitle", errors);
        requireText(root, "typeName", errors);
        requireText(root, "checkIn", errors);
        requireText(root, "checkOut", errors);
        requireText(root, "totalRoomAmount", errors);
        requireText(root, "totalAmount", errors);
        requireText(root, "validUntil", errors);
        requireText(root, "riskNotice", errors);
        requireInt(root, "roomCount", errors);
        requireInt(root, "nights", errors);

        JsonNode nightly = root.get("nightlyBreakdown");
        int nights = root.path("nights").asInt(0);
        if (nightly == null || !nightly.isArray() || nightly.isEmpty()) {
            errors.add("nightlyBreakdown 必须是非空数组");
        } else {
            if (nights > 0 && nightly.size() != nights) {
                errors.add("nightlyBreakdown 长度(" + nightly.size() + ")与 nights(" + nights + ")不一致");
            }
            BigDecimal sum = BigDecimal.ZERO;
            LocalDate previous = null;
            for (int i = 0; i < nightly.size(); i++) {
                JsonNode day = nightly.get(i);
                requireText(day, "date", errors, "nightlyBreakdown[" + i + "]");
                requireText(day, "unitPrice", errors, "nightlyBreakdown[" + i + "]");
                requireText(day, "priceSource", errors, "nightlyBreakdown[" + i + "]");
                BigDecimal price = decimalOrNull(day.path("unitPrice").asText(null));
                if (price == null) {
                    errors.add("nightlyBreakdown[" + i + "].unitPrice 必须是数字金额");
                } else {
                    sum = sum.add(price);
                }
                String dateText = day.path("date").asText("");
                try {
                    LocalDate date = LocalDate.parse(dateText);
                    if (previous != null && !date.equals(previous.plusDays(1))) {
                        errors.add("nightlyBreakdown 第 " + (i + 1) + " 条日期不连续");
                    }
                    previous = date;
                } catch (Exception e) {
                    errors.add("nightlyBreakdown[" + i + "].date 格式应为 yyyy-MM-dd");
                }
            }
            BigDecimal totalRoom = decimalOrNull(root.path("totalRoomAmount").asText(null));
            if (totalRoom != null && sum.compareTo(BigDecimal.ZERO) > 0
                    && totalRoom.subtract(sum).abs().compareTo(new BigDecimal("0.05")) > 0) {
                errors.add("totalRoomAmount(" + totalRoom + ")与各晚单价之和(" + sum + ")不一致");
            }
        }

        JsonNode notes = root.get("notes");
        if (notes == null || !notes.isArray() || notes.isEmpty()) {
            errors.add("notes 必须是非空数组（至少给出退改政策）");
        }
        BigDecimal room = decimalOrNull(root.path("totalRoomAmount").asText(null));
        BigDecimal service = decimalOrZero(root.path("serviceFee").asText(null));
        BigDecimal discount = decimalOrZero(root.path("discountAmount").asText(null));
        BigDecimal total = decimalOrNull(root.path("totalAmount").asText(null));
        if (room != null && total != null) {
            BigDecimal expected = room.add(service).subtract(discount);
            if (total.subtract(expected).abs().compareTo(new BigDecimal("0.05")) > 0) {
                errors.add("totalAmount(" + total + ")与 房费+服务费-优惠(" + expected + ")不一致");
            }
        }
        if (!errors.isEmpty()) {
            return new Validation(false, String.join("；", errors), root);
        }
        return new Validation(true, "校验通过", root);
    }

    private BigDecimal decimalOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replace("￥", "").replace("元", "").replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimalOrZero(String text) {
        BigDecimal v = decimalOrNull(text);
        return v == null ? BigDecimal.ZERO : v;
    }

    private void requireText(JsonNode node, String field, List<String> errors) {
        requireText(node, field, errors, "");
    }

    private void requireText(JsonNode node, String field, List<String> errors, String prefix) {
        JsonNode v = node.get(field);
        String path = prefix.isEmpty() ? field : prefix + "." + field;
        if (v == null || v.isNull() || v.asText().isBlank()) {
            errors.add(path + " 缺失或为空");
        }
    }

    private void requireInt(JsonNode node, String field, List<String> errors) {
        JsonNode v = node.get(field);
        if (v == null || !v.canConvertToInt() || v.asInt() <= 0) {
            errors.add(field + " 必须是正整数");
        }
    }

    /** 去掉模型可能添加的 markdown 代码块包裹。 */
    public String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstLine = t.indexOf('\n');
            if (firstLine > 0) {
                t = t.substring(firstLine + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
        }
        return t.trim();
    }
}
