package com.cqucc.hotel.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文本向量化器（本地稀疏向量模型）。
 * <p>设计说明：RAG 需要"文档切分 → 向量化 → 相似度检索"的完整链路。
 * 为了让系统在没有外部向量模型、没有网络的情况下也能稳定运行（答辩现场常无网络），
 * 这里实现一个本地 TF-IDF 稀疏向量模型：</p>
 * <ul>
 *   <li>特征：中文单字 + 相邻双字组合，兼顾中文分词缺失下的语义区分度；</li>
 *   <li>权重：TF 取对数平滑，IDF 由整个知识库语料统计，权重 = TF × IDF；</li>
 *   <li>相似度：余弦相似度（向量预先 L2 归一化，点积即为余弦值）。</li>
 * </ul>
 * <p>该实现与外部 embedding 接口解耦，后续只需把 {@code vectorize} 换成模型调用即可升级为稠密向量检索。</p>
 */
@Component
public class TextVectorizer {

    private final ObjectMapper json;

    /** 词项 → 文档频率。 */
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    /** 已统计的片段总数。 */
    private int documentCount = 0;
    /** 词项 → 逆文档频率。 */
    private final Map<String, Double> idfCache = new HashMap<>();

    public TextVectorizer(ObjectMapper json) {
        this.json = json;
    }

    /** 稀疏向量：词项 → 权重。 */
    public record SparseVector(Map<String, Double> weights, double norm) {
    }

    /**
     * 重建语料统计。索引重建时调用一次。
     *
     * @param docs 全部知识库片段文本
     */
    public synchronized void buildCorpus(List<String> docs) {
        documentFrequency.clear();
        idfCache.clear();
        documentCount = docs.size();
        for (String doc : docs) {
            Set<String> terms = new HashSet<>(terms(doc));
            for (String t : terms) {
                documentFrequency.merge(t, 1, Integer::sum);
            }
        }
    }

    public synchronized int corpusSize() {
        return documentCount;
    }

    /** 把文本转换为稀疏向量（含 L2 归一化）。 */
    public SparseVector vectorize(String text) {
        List<String> termList = terms(text);
        if (termList.isEmpty()) {
            return new SparseVector(Map.of(), 0);
        }
        Map<String, Integer> tf = new HashMap<>();
        for (String t : termList) {
            tf.merge(t, 1, Integer::sum);
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        double sumSquares = 0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double tfWeight = 1 + Math.log(e.getValue());
            double weight = tfWeight * idf(e.getKey());
            if (weight <= 0) {
                continue;
            }
            weights.put(e.getKey(), weight);
            sumSquares += weight * weight;
        }
        double norm = Math.sqrt(sumSquares);
        return new SparseVector(weights, norm);
    }

    private synchronized double idf(String term) {
        return idfCache.computeIfAbsent(term, t -> {
            int df = documentFrequency.getOrDefault(t, 0);
            return Math.log((documentCount + 1.0) / (df + 1.0)) + 1.0;
        });
    }

    /**
     * 文本特征提取：中文单字 + 双字组合，英文与数字整体保留。
     */
    public List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String cleaned = text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）《》【】…—·]+", " ")
                .trim();
        for (String part : cleaned.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.matches("[a-z0-9]+")) {
                result.add(part);
                continue;
            }
            for (int i = 0; i < part.length(); i++) {
                String single = String.valueOf(part.charAt(i));
                result.add(single);
                if (i + 1 < part.length()) {
                    result.add(part.substring(i, i + 2));
                }
            }
        }
        return result;
    }

    /** 余弦相似度：两个已归一化稀疏向量的点积。 */
    public double cosine(SparseVector a, SparseVector b) {
        if (a.norm() == 0 || b.norm() == 0) {
            return 0;
        }
        // 遍历词项较少的一方
        SparseVector small = a.weights().size() <= b.weights().size() ? a : b;
        SparseVector large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.weights().entrySet()) {
            Double other = large.weights().get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        return dot / (a.norm() * b.norm());
    }

    /** 向量序列化为 JSON，持久化到 knowledge_chunk.vector_json。 */
    public String toJson(SparseVector vector) {
        try {
            return json.writeValueAsString(vector.weights());
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 从数据库读回向量。 */
    public SparseVector fromJson(String vectorJson, double norm) {
        if (vectorJson == null || vectorJson.isBlank()) {
            return new SparseVector(Map.of(), 0);
        }
        try {
            Map<String, Double> map = json.readValue(vectorJson, new TypeReference<LinkedHashMap<String, Double>>() {
            });
            return new SparseVector(map, norm);
        } catch (Exception e) {
            return new SparseVector(Map.of(), 0);
        }
    }

    /** 用于调试与报告：展示权重最高的若干词项。 */
    public List<String> topTerms(SparseVector vector, int n) {
        return vector.weights().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }
}
