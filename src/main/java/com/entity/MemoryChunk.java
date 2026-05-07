package com.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("memory_chunk")
public class MemoryChunk {

    private Long id;
    private Long userId;     // 属于哪个用户
    private String sourceType; // 来源（面试/知识/闪卡）
    private Long sourceId;   // 来源ID
    private String content;  // 文本内容
    private String embedding; // 向量（JSON存储或外部DB）
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
    public String getEmbedding() {
        return embedding;
    }
    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }
    public String getSourceType() {
        return sourceType;
    }
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
    public Long getSourceId() {
        return sourceId;
    }
    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

}
