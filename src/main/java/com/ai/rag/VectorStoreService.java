package com.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class VectorStoreService {

    private final EmbeddingModel embeddingModel;
    private final String url;
    private final String username;
    private final String password;
    private final String table;
    private final int dimension;
    private final double minScore;
    private final boolean initializeSchema;
    /** false = 完全关闭向量检索：不建表、不调 embedding、不写库（本机没有 pgvector 时用）。 */
    private final boolean enabled;

    public VectorStoreService(
            EmbeddingModel embeddingModel,
            @Value("${vector-store.pg.url}") String url,
            @Value("${vector-store.pg.username}") String username,
            @Value("${vector-store.pg.password}") String password,
            @Value("${vector-store.pg.table:memory_vector}") String table,
            @Value("${vector-store.pg.dimension:1024}") int dimension,
            @Value("${vector-store.pg.min-score:0.6}") double minScore,
            @Value("${vector-store.pg.initialize-schema:true}") boolean initializeSchema,
            @Value("${vector-store.pg.enabled:true}") boolean enabled) {
        this.embeddingModel = embeddingModel;
        this.url = url;
        this.username = username;
        this.password = password;
        this.table = validateTableName(table);
        this.dimension = dimension;
        this.minScore = minScore;
        this.initializeSchema = initializeSchema;
        this.enabled = enabled;
    }

    @PostConstruct
    public void initSchema() {
        if (!enabled) {
            log.info("向量检索已关闭（vector-store.pg.enabled=false）：不建表，记忆只落 MySQL");
            return;
        }
        if (!initializeSchema) {
            return;
        }

        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id BIGSERIAL PRIMARY KEY,
                        memory_chunk_id BIGINT NOT NULL UNIQUE,
                        user_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        embedding VECTOR(%d) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """.formatted(table, dimension));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_user_id ON %s(user_id)"
                    .formatted(indexName(table), table));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_%s_embedding ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
                    .formatted(indexName(table), table));
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 pgvector 表失败：" + e.getMessage(), e);
        }
    }

    public void addMemoryText(Long userId, Long memoryChunkId, String content) {
        if (!enabled) {
            return;
        }
        if (userId == null || memoryChunkId == null || content == null || content.isBlank()) {
            return;
        }

        Embedding embedding = embeddingModel.embed(content).content();
        ensureEmbeddingDimension(embedding);

        String sql = """
                INSERT INTO %s(memory_chunk_id, user_id, content, embedding)
                VALUES (?, ?, ?, ?::vector)
                ON CONFLICT (memory_chunk_id)
                DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding
                """.formatted(table);

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, memoryChunkId);
            statement.setLong(2, userId);
            statement.setString(3, content);
            statement.setString(4, toVectorLiteral(embedding.vector()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("写入 pgvector 失败：" + e.getMessage(), e);
        }
    }

    public List<String> search(Long userId, String query, int maxResults) {
        if (!enabled) {
            return List.of();
        }
        if (userId == null || query == null || query.isBlank() || maxResults <= 0) {
            return List.of();
        }

        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            ensureEmbeddingDimension(queryEmbedding);
            return searchByEmbedding(userId, queryEmbedding, maxResults);
        } catch (Exception e) {
            log.warn("pgvector 检索失败，返回空向量召回结果：{}", e.getMessage());
            return List.of();
        }
    }

    private List<String> searchByEmbedding(Long userId, Embedding queryEmbedding, int maxResults) throws SQLException {
        String vector = toVectorLiteral(queryEmbedding.vector());
        String sql = """
                SELECT content
                FROM (
                    SELECT content, 1 - (embedding <=> ?::vector) AS score
                    FROM %s
                    WHERE user_id = ?
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                ) matched
                WHERE score >= ?
                ORDER BY score DESC
                """.formatted(table);

        List<String> results = new ArrayList<>();
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vector);
            statement.setLong(2, userId);
            statement.setString(3, vector);
            statement.setInt(4, maxResults);
            statement.setDouble(5, minScore);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(resultSet.getString("content"));
                }
            }
        }
        return results;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    private void ensureEmbeddingDimension(Embedding embedding) {
        if (embedding.dimension() != dimension) {
            throw new IllegalStateException(
                    "Embedding 维度不匹配：配置为 %d，实际为 %d".formatted(dimension, embedding.dimension()));
        }
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[i]));
        }
        return builder.append(']').toString();
    }

    private String validateTableName(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法 pgvector 表名：" + value);
        }
        return value;
    }

    private String indexName(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
