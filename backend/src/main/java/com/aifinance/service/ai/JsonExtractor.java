package com.aifinance.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 AI 的文本响应中健壮地提取 JSON 对象/数组。
 * 模型有时会用 ```json ... ``` 包裹, 或夹带额外说明文字。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static JsonNode extract(ObjectMapper mapper, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = stripCodeFence(text);

        // 先尝试直接解析
        JsonNode direct = tryParse(mapper, cleaned);
        if (direct != null) {
            return direct;
        }

        // 再尝试截取第一个 { ... } 或 [ ... ]
        String candidate = sliceJson(cleaned);
        if (candidate != null) {
            JsonNode node = tryParse(mapper, candidate);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private static String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
        }
        return t.trim();
    }

    private static String sliceJson(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start;
        char open;
        char close;
        if (objStart < 0 && arrStart < 0) {
            return null;
        }
        if (arrStart < 0 || (objStart >= 0 && objStart < arrStart)) {
            start = objStart;
            open = '{';
            close = '}';
        } else {
            start = arrStart;
            open = '[';
            close = ']';
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static JsonNode tryParse(ObjectMapper mapper, String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
