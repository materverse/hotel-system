package com.cqucc.hotel.dao;

import com.cqucc.hotel.entity.KnowledgeChunk;
import com.cqucc.hotel.entity.KnowledgeDoc;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 知识库数据访问：文档与片段（含向量）。
 */
public class KnowledgeDao {

    private final JdbcTemplate jdbc;

    public KnowledgeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeDoc> listDocs(String category) {
        if (category == null || category.isBlank()) {
            return jdbc.query("SELECT * FROM knowledge_doc ORDER BY id",
                    new BeanPropertyRowMapper<>(KnowledgeDoc.class));
        }
        return jdbc.query("SELECT * FROM knowledge_doc WHERE category = ? ORDER BY id",
                new BeanPropertyRowMapper<>(KnowledgeDoc.class), category);
    }

    public KnowledgeDoc findDoc(Long id) {
        List<KnowledgeDoc> list = jdbc.query("SELECT * FROM knowledge_doc WHERE id = ?",
                new BeanPropertyRowMapper<>(KnowledgeDoc.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Long insertDoc(KnowledgeDoc doc) {
        CrudDao<KnowledgeDoc> dao = new CrudDao<>(jdbc, "knowledge_doc", KnowledgeDoc.class);
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("title", doc.getTitle());
        values.put("category", doc.getCategory());
        values.put("content", doc.getContent());
        values.put("source", doc.getSource());
        values.put("chunk_count", 0);
        values.put("status", "READY");
        return dao.insertReturnKey(values);
    }

    public int updateDoc(Long id, String title, String category, String content, String source, Integer chunkCount) {
        return jdbc.update("UPDATE knowledge_doc SET title = ?, category = ?, content = ?, source = ?, chunk_count = ? "
                + "WHERE id = ?", title, category, content, source, chunkCount, id);
    }

    public int deleteDoc(Long id) {
        return jdbc.update("DELETE FROM knowledge_doc WHERE id = ?", id);
    }

    public int deleteChunksByDoc(Long docId) {
        return jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id = ?", docId);
    }

    public int deleteAllChunks() {
        return jdbc.update("DELETE FROM knowledge_chunk");
    }

    public int insertChunk(Long docId, int index, String content, int tokenCount, String vectorJson, double norm) {
        return jdbc.update("INSERT INTO knowledge_chunk (doc_id, chunk_index, content, token_count, vector_json, vector_norm) "
                + "VALUES (?,?,?,?,?,?)", docId, index, content, tokenCount, vectorJson, norm);
    }

    /** 读取全部片段及其文档标题，用于内存向量检索。 */
    public List<KnowledgeChunk> listAllChunks() {
        return jdbc.query("SELECT kc.*, kd.title AS doc_title, kd.source AS doc_source FROM knowledge_chunk kc "
                + "JOIN knowledge_doc kd ON kd.id = kc.doc_id WHERE kd.status = 'READY' ORDER BY kc.doc_id, kc.chunk_index",
                new BeanPropertyRowMapper<>(KnowledgeChunk.class));
    }

    public long countChunks() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Long.class);
        return c == null ? 0 : c;
    }

    public long countDocs() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc", Long.class);
        return c == null ? 0 : c;
    }
}
