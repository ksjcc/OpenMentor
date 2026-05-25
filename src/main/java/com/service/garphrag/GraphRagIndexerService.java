package com.service.garphrag;

import com.ai.agent.GraphExtractionAgent;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.graphrag.*;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.entity.graphrag.GraphTextUnit;
import com.mapper.*;
import com.service.JsonParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GraphRAG 索引服务
 *
 * 对应 Microsoft GraphRAG 的 Indexing 阶段：
 * TextUnit -> Entity -> Relationship
 */
@Service
@RequiredArgsConstructor
public class GraphRagIndexerService {

  private static final int MAX_TEXT_UNIT_LENGTH = 1200;
  private static final int TEXT_UNIT_OVERLAP = 120;

  private final GraphExtractionAgent graphExtractionAgent;
  private final JsonParseService jsonParseService;

  private final GraphTextUnitMapper textUnitMapper;
  private final GraphEntityMapper entityMapper;
  private final GraphRelationshipMapper relationshipMapper;

  @Transactional(rollbackFor = Exception.class)
  public void indexText(Long userId, String sourceType, Long sourceId, String content) {
    validateInput(userId, sourceType, content);

    String normalizedContent = normalizeText(content);
    List<String> textUnits = splitTextUnits(normalizedContent);
    for (int index = 0; index < textUnits.size(); index++) {
      String chunk = textUnits.get(index);
      GraphTextUnit textUnit = persistTextUnit(userId, sourceType, sourceId, chunk, index);
      GraphExtractionResult result = extractGraph(chunk);
      if (result == null) {
        continue;
      }

      if (result.getEntities() != null) {
        for (GraphEntityDTO dto : result.getEntities()) {
          createOrUpdateEntity(userId, dto, textUnit.getId());
        }
      }

      if (result.getRelationships() != null) {
        for (GraphRelationDTO dto : result.getRelationships()) {
          createRelationship(userId, dto, textUnit.getId());
        }
      }
    }
  }

  private GraphTextUnit persistTextUnit(
      Long userId,
      String sourceType,
      Long sourceId,
      String content,
      int index) {
    GraphTextUnit textUnit = new GraphTextUnit();
    textUnit.setUserId(userId);
    textUnit.setSourceType(sourceType.trim().toUpperCase(Locale.ROOT));
    textUnit.setSourceId(sourceId);
    textUnit.setContent(content);
    textUnit.setChunkIndex(index);
    textUnit.setTokenCount(estimateTokenCount(content));
    textUnitMapper.insert(textUnit);
    return textUnit;
  }

  private GraphExtractionResult extractGraph(String content) {
    String json = graphExtractionAgent.extract(content);
    return jsonParseService.parseGraphExtraction(json);
  }

  private GraphRagEntity createOrUpdateEntity(
      Long userId,
      GraphEntityDTO dto,
      Long textUnitId) {
    if (dto == null) {
      return null;
    }

    String name = normalizeEntityName(dto.getName());
    if (name == null) {
      return null;
    }

    String type = normalizeType(dto.getType());
    String description = normalizeOptionalText(dto.getDescription());
    GraphRagEntity old = entityMapper.selectByUserAndName(userId, name);

    if (old != null) {
      old.setOccurrenceCount(safeCount(old.getOccurrenceCount()) + 1);
      if (isBlank(old.getType()) && type != null) {
        old.setType(type);
      }
      if (shouldReplaceDescription(old.getDescription(), description)) {
        old.setDescription(description);
      }
      entityMapper.updateById(old);
      return old;
    }

    GraphRagEntity entity = new GraphRagEntity();
    entity.setUserId(userId);
    entity.setName(name);
    entity.setType(type);
    entity.setDescription(description);
    entity.setTextUnitId(textUnitId);
    entity.setOccurrenceCount(1);
    entityMapper.insert(entity);
    return entity;
  }

  private void createRelationship(
      Long userId,
      GraphRelationDTO dto,
      Long textUnitId) {
    if (dto == null) {
      return;
    }

    String sourceName = normalizeEntityName(dto.getSource());
    String targetName = normalizeEntityName(dto.getTarget());
    if (sourceName == null || targetName == null || sourceName.equals(targetName)) {
      return;
    }

    GraphRagEntity source = entityMapper.selectByUserAndName(userId, sourceName);
    GraphRagEntity target = entityMapper.selectByUserAndName(userId, targetName);

    if (source == null || target == null) {
      return;
    }

    String relationType = normalizeRelationType(dto.getType());
    String description = normalizeOptionalText(dto.getDescription());
    Double weight = normalizeWeight(dto.getWeight());
    GraphRagRelationship existing = relationshipMapper.selectExisting(
        userId, source.getId(), target.getId(), relationType);
    if (existing != null) {
      existing.setWeight(Math.max(existing.getWeight() == null ? 0.0 : existing.getWeight(), weight));
      if (shouldReplaceDescription(existing.getDescription(), description)) {
        existing.setDescription(description);
      }
      relationshipMapper.updateById(existing);
      return;
    }

    GraphRagRelationship rel = new GraphRagRelationship();
    rel.setUserId(userId);
    rel.setSourceEntityId(source.getId());
    rel.setTargetEntityId(target.getId());
    rel.setRelationType(relationType);
    rel.setDescription(description);
    rel.setWeight(weight);
    rel.setTextUnitId(textUnitId);

    relationshipMapper.insert(rel);
  }

  private void validateInput(Long userId, String sourceType, String content) {
    if (userId == null) {
      throw new BizException(ErrorCode.BAD_REQUEST, "GraphRAG 索引用户ID不能为空");
    }
    if (isBlank(sourceType)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "GraphRAG 索引来源类型不能为空");
    }
    if (isBlank(content)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "GraphRAG 索引内容不能为空");
    }
  }

  private List<String> splitTextUnits(String content) {
    if (content.length() <= MAX_TEXT_UNIT_LENGTH) {
      return List.of(content);
    }

    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < content.length()) {
      int maxEnd = Math.min(start + MAX_TEXT_UNIT_LENGTH, content.length());
      int end = findChunkBoundary(content, start, maxEnd);
      String chunk = content.substring(start, end).trim();
      if (!chunk.isBlank()) {
        chunks.add(chunk);
      }
      if (end >= content.length()) {
        break;
      }
      start = Math.max(end - TEXT_UNIT_OVERLAP, start + 1);
    }
    return chunks;
  }

  private int findChunkBoundary(String content, int start, int maxEnd) {
    if (maxEnd == content.length()) {
      return maxEnd;
    }

    int minimumBoundary = start + MAX_TEXT_UNIT_LENGTH / 2;
    int paragraph = content.lastIndexOf("\n\n", maxEnd);
    if (paragraph >= minimumBoundary) {
      return paragraph;
    }
    int sentence = Math.max(
        content.lastIndexOf('。', maxEnd - 1),
        content.lastIndexOf('.', maxEnd - 1));
    return sentence >= minimumBoundary ? sentence + 1 : maxEnd;
  }

  private int estimateTokenCount(String text) {
    return text.codePointCount(0, text.length());
  }

  private String normalizeEntityName(String rawName) {
    String name = normalizeOptionalText(rawName);
    if (name == null) {
      return null;
    }
    name = name.replaceAll("^[\\p{P}\\s]+|[\\p{P}\\s]+$", "");
    if (name.isBlank()) {
      return null;
    }

    return switch (name.toLowerCase(Locale.ROOT)) {
      case "redis" -> "Redis";
      case "mysql" -> "MySQL";
      case "jvm" -> "JVM";
      case "spring boot", "springboot" -> "Spring Boot";
      default -> name;
    };
  }

  private String normalizeType(String type) {
    String normalized = normalizeOptionalText(type);
    if (normalized == null) {
      return "CONCEPT";
    }
    return normalized.matches("[A-Za-z_ ]+")
        ? normalized.replace(' ', '_').toUpperCase(Locale.ROOT)
        : normalized;
  }

  private String normalizeRelationType(String type) {
    String normalized = normalizeOptionalText(type);
    if (normalized == null) {
      return "RELATED";
    }
    return normalized.matches("[A-Za-z_ ]+")
        ? normalized.replace(' ', '_').toUpperCase(Locale.ROOT)
        : normalized;
  }

  private Double normalizeWeight(Double weight) {
    if (weight == null) {
      return 1.0;
    }
    return Math.max(0.0, Math.min(1.0, weight));
  }

  private String normalizeText(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n').trim();
  }

  private String normalizeOptionalText(String text) {
    if (isBlank(text)) {
      return null;
    }
    return text.trim().replaceAll("\\s+", " ");
  }

  private boolean shouldReplaceDescription(String current, String candidate) {
    return candidate != null && (isBlank(current) || candidate.length() > current.length());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private int safeCount(Integer value) {
    return value == null ? 0 : value;
  }
}
