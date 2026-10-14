package com.cqucc.hotel.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切分器。
 * <p>RAG 第一步：把知识库文档按句子边界切成大小可控、语义完整的片段，
 * 相邻片段保留一定重叠，避免答案被切断在片段边界。</p>
 */
@Component
public class TextChunker {

    private static final String[] SENTENCE_END = {"。", "！", "？", "；", "\n", ".", "!", "?", ";"};

    /**
     * 切分文档。
     *
     * @param text      文档正文
     * @param chunkSize 目标片段长度（字符）
     * @param overlap   相邻片段重叠长度（字符）
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        List<String> sentences = splitSentences(normalized);
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() > chunkSize && buffer.length() > 0) {
                chunks.add(buffer.toString().trim());
                String tail = tail(buffer.toString(), overlap);
                buffer.setLength(0);
                buffer.append(tail);
            }
            buffer.append(sentence);
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString().trim());
        }
        // 合并过短的片段（小于 25 字且不是唯一片段）
        List<String> merged = new ArrayList<>();
        for (String c : chunks) {
            if (!merged.isEmpty() && c.length() < 25) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + c);
            } else {
                merged.add(c);
            }
        }
        return merged;
    }

    private List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            if (isSentenceEnd(c)) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private boolean isSentenceEnd(char c) {
        for (String s : SENTENCE_END) {
            if (s.charAt(0) == c) {
                return true;
            }
        }
        return false;
    }

    private String tail(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return "";
        }
        return text.substring(text.length() - overlap);
    }
}
