package com.cqucc.hotel.service;

import com.cqucc.hotel.ai.rag.TextChunker;
import com.cqucc.hotel.ai.rag.TextVectorizer;
import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.dao.KnowledgeDao;
import com.cqucc.hotel.entity.KnowledgeChunk;
import com.cqucc.hotel.entity.KnowledgeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知识库（RAG）服务。
 * <p>完整链路：文档入库 → 按句切分 → 本地向量化 → 相似度检索 → 带来源引用返回。
 * 检索结果不足以回答时，明确返回"无答案"，交由上层提示用户，避免模型编造房价与政策。</p>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeDao knowledgeDao;
    private final TextChunker chunker;
    private final TextVectorizer vectorizer;

    @Value("${rag.chunk-size:220}")
    private int chunkSize;
    @Value("${rag.chunk-overlap:40}")
    private int chunkOverlap;
    @Value("${rag.top-k:4}")
    private int topK;
    @Value("${rag.score-threshold:0.08}")
    private double scoreThreshold;
    @Value("${rag.reindex-on-startup:true}")
    private boolean reindexOnStartup;

    private volatile List<IndexedChunk> index = List.of();

    public KnowledgeBaseService(KnowledgeDao knowledgeDao, TextChunker chunker, TextVectorizer vectorizer) {
        this.knowledgeDao = knowledgeDao;
        this.chunker = chunker;
        this.vectorizer = vectorizer;
    }

    /** 检索命中项。 */
    public record Hit(Long docId, String docTitle, String source, int chunkIndex, String content, double score) {
    }

    private record IndexedChunk(Long docId, String docTitle, String source, int chunkIndex,
                                String content, TextVectorizer.SparseVector vector) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        try {
            if (reindexOnStartup) {
                reindex();
            } else {
                loadIndex();
            }
        } catch (Exception e) {
            log.warn("知识库初始化失败（不影响系统其它功能）：{}", e.getMessage());
        }
    }

    /** 重建全部片段与向量（管理员在知识库管理中触发）。 */
    @Transactional
    public int reindex() {
        List<KnowledgeDoc> docs = knowledgeDao.listDocs(null);
        knowledgeDao.deleteAllChunks();
        List<Object[]> pending = new ArrayList<>();
        List<String> allTexts = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            List<String> parts = chunker.split(doc.getContent(), chunkSize, chunkOverlap);
            for (int i = 0; i < parts.size(); i++) {
                pending.add(new Object[]{doc.getId(), i, parts.get(i)});
                allTexts.add(parts.get(i));
            }
            knowledgeDao.updateDoc(doc.getId(), doc.getTitle(), doc.getCategory(), doc.getContent(),
                    doc.getSource(), parts.size());
        }
        vectorizer.buildCorpus(allTexts);
        for (Object[] row : pending) {
            String content = (String) row[2];
            TextVectorizer.SparseVector vector = vectorizer.vectorize(content);
            knowledgeDao.insertChunk((Long) row[0], (Integer) row[1], content, content.length(),
                    vectorizer.toJson(vector), vector.norm());
        }
        loadIndex();
        log.info("知识库重建完成：文档 {} 篇，片段 {} 个", docs.size(), pending.size());
        return pending.size();
    }

    /** 从数据库加载向量索引到内存。 */
    public void loadIndex() {
        List<KnowledgeChunk> chunks = knowledgeDao.listAllChunks();
        List<IndexedChunk> list = new ArrayList<>(chunks.size());
        List<String> texts = new ArrayList<>(chunks.size());
        for (KnowledgeChunk c : chunks) {
            texts.add(c.getContent());
            list.add(new IndexedChunk(c.getDocId(), c.getDocTitle(), c.getDocSource(), c.getChunkIndex(),
                    c.getContent(), vectorizer.fromJson(c.getVectorJson(),
                    c.getVectorNorm() == null ? 0 : c.getVectorNorm().doubleValue())));
        }
        if (vectorizer.corpusSize() != texts.size()) {
            vectorizer.buildCorpus(texts);
        }
        this.index = list;
        log.info("知识库索引加载完成：{} 个片段", list.size());
    }

    /**
     * 检索知识库。
     *
     * @param question 用户问题
     * @param k        返回条数
     * @return 按相似度倒序的命中列表；全部低于阈值时返回空列表（无答案）
     */
    public List<Hit> search(String question, Integer k) {
        if (index.isEmpty()) {
            loadIndex();
        }
        int limit = k == null || k <= 0 ? topK : k;
        TextVectorizer.SparseVector queryVector = vectorizer.vectorize(question);
        List<Hit> hits = new ArrayList<>();
        for (IndexedChunk c : index) {
            double score = vectorizer.cosine(queryVector, c.vector());
            if (score > 0) {
                hits.add(new Hit(c.docId(), c.docTitle(), c.source(), c.chunkIndex(), c.content(), round(score)));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        List<Hit> top = hits.stream().limit(limit).toList();
        if (top.isEmpty() || top.get(0).score() < scoreThreshold) {
            return List.of();
        }
        return top.stream().filter(h -> h.score() >= scoreThreshold).toList();
    }

    private double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // ---------------- 文档管理（管理端） ----------------

    public List<KnowledgeDoc> listDocs(String category) {
        return knowledgeDao.listDocs(category);
    }

    @Transactional
    public Long createDoc(String title, String category, String content, String source) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("文档标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("文档内容不能为空");
        }
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title.trim());
        doc.setCategory(category == null || category.isBlank() ? "通用" : category.trim());
        doc.setContent(content.trim());
        doc.setSource(source == null ? "手工录入" : source.trim());
        Long id = knowledgeDao.insertDoc(doc);
        reindex();
        return id;
    }

    @Transactional
    public void updateDoc(Long id, String title, String category, String content, String source) {
        KnowledgeDoc exist = knowledgeDao.findDoc(id);
        if (exist == null) {
            throw new BusinessException("知识库文档不存在");
        }
        knowledgeDao.updateDoc(id, title, category,
                content == null || content.isBlank() ? exist.getContent() : content,
                source, exist.getChunkCount());
        reindex();
    }

    @Transactional
    public void deleteDoc(Long id) {
        knowledgeDao.deleteChunksByDoc(id);
        knowledgeDao.deleteDoc(id);
        reindex();
    }

    public long countDocs() {
        return knowledgeDao.countDocs();
    }

    public long countChunks() {
        return knowledgeDao.countChunks();
    }

    public int indexSize() {
        return index.size();
    }

    public int topK() {
        return topK;
    }

    public double scoreThreshold() {
        return scoreThreshold;
    }
}
