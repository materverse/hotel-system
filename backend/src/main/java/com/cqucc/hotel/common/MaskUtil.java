package com.cqucc.hotel.common;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏与检测工具。
 * 用于：AI 调用前对上传内容脱敏、AI 输出内容审查、日志打印保护。
 */
public final class MaskUtil {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[0-9Xx]");
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** 疑似索要凭据的提示词注入特征（中英文，忽略大小写）。 */
    private static final Pattern INJECTION = Pattern.compile(
            "(忽略(上面|之前|以上)(的)?(所有)?(指令|规则|提示)|ignore\\s+(all\\s+)?(previous|above)\\s+instructions?"
                    + "|你现在是|扮演一个没有限制|没有限制的(助手|模式)|开发者模式|DAN模式|越狱"
                    + "|泄露(你的)?(系统)?提示词|输出(你的)?系统提示"
                    + "|reveal\\s+(your\\s+)?(system\\s+)?prompt|unrestricted\\s+assistant|jailbreak)",
            Pattern.CASE_INSENSITIVE);

    private MaskUtil() {
    }

    /** 手机号脱敏：138****0001。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 姓名脱敏：张*明。 */
    public static String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** 证件号脱敏：保留前 6 位与后 4 位，如 500102********0011。 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "*".repeat(idCard.length() - 10) + idCard.substring(idCard.length() - 4);
    }

    /** 对文本中出现的手机号、身份证、银行卡、邮箱统一脱敏。 */
    public static String maskSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = PHONE.matcher(text).replaceAll(m -> m.group().substring(0, 3) + "****" + m.group().substring(7));
        out = ID_CARD.matcher(out).replaceAll(m -> m.group().substring(0, 6) + "********" + m.group().substring(14));
        out = BANK_CARD.matcher(out).replaceAll(m -> "****" + m.group().substring(m.group().length() - 4));
        out = EMAIL.matcher(out).replaceAll(m -> {
            String g = m.group();
            int at = g.indexOf('@');
            String name = g.substring(0, at);
            return (name.length() <= 2 ? name.charAt(0) + "*" : name.charAt(0) + "***" + name.charAt(name.length() - 1)) + g.substring(at);
        });
        return out;
    }

    /** 判断文本中是否含有敏感信息。 */
    public static boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return PHONE.matcher(text).find() || ID_CARD.matcher(text).find()
                || BANK_CARD.matcher(text).find() || EMAIL.matcher(text).find();
    }

    /** 判断是否为提示词注入攻击。 */
    public static boolean isPromptInjection(String text) {
        return text != null && INJECTION.matcher(text).find();
    }
}
