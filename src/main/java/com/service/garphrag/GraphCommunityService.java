package com.service.garphrag;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.agent.CommunityReportAgent;
import com.common.BizException;
import com.common.ErrorCode;
import com.dto.graphrag.CommunityReportResult;
import com.entity.graphrag.GraphCommunity;
import com.entity.graphrag.GraphCommunityEntity;
import com.entity.graphrag.GraphCommunityReport;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.GraphCommunityEntityMapper;
import com.mapper.GraphCommunityMapper;
import com.mapper.GraphCommunityReportMapper;
import com.mapper.GraphEntityMapper;
import com.mapper.GraphRelationshipMapper;
import com.service.JsonParseService;
import lombok.RequiredArgsConstructor;

/**
 * GraphTextUnit
 * ↓ 提取
 * GraphRagEntity
 * ↓ 连接
 * GraphRelationship
 * ↓ 聚类
 * GraphCommunity
 * ↓ 总结
 * GraphCommunityReport
 */
@Service
@RequiredArgsConstructor
public class GraphCommunityService {
  private static final int COMMUNITY_ITERATIONS = 10;

  private final GraphEntityMapper entityMapper;
  private final GraphRelationshipMapper relationshipMapper;
  private final GraphCommunityMapper communityMapper;
  private final GraphCommunityEntityMapper communityEntityMapper;
  private final GraphCommunityReportMapper reportMapper;
  private final CommunityReportAgent communityReportAgent;
  private final JsonParseService jsonParseService;
  private final ObjectMapper objectMapper;

  /**
   * 重建当前用户的社区快照。
   * 当前表结构未包含 build/version 字段，因此使用清理旧快照再重建的策略。
   */
  @Transactional(rollbackFor = Exception.class)
  public void rebuildCommunities(Long userId) {
    if (userId == null) {
      throw new BizException(ErrorCode.BAD_REQUEST, "GraphRAG 社区构建用户ID不能为空");
    }

    clearCurrentCommunities(userId);

    List<GraphRagEntity> entities = entityMapper.selectByUserId(userId);
    if (entities == null || entities.isEmpty()) {
      return;
    }

    List<GraphRagRelationship> relationships = relationshipMapper.selectByUserId(userId);
    Map<Long, GraphRagEntity> entitiesById = entities.stream()
        .filter(entity -> entity.getId() != null)
        .collect(Collectors.toMap(GraphRagEntity::getId, Function.identity()));

    List<List<GraphRagEntity>> communities = detectCommunities(entities, relationships, entitiesById);
    for (List<GraphRagEntity> members : communities) {
      List<GraphRagRelationship> internalRelationships = internalRelationships(members, relationships);
      String name = inferCommunityName(members);
      createCommunityAndReport(userId, name, members, internalRelationships);
    }
  }

  private void clearCurrentCommunities(Long userId) {
    reportMapper.delete(new LambdaQueryWrapper<GraphCommunityReport>()
        .eq(GraphCommunityReport::getUserId, userId));
    communityEntityMapper.delete(new LambdaQueryWrapper<GraphCommunityEntity>()
        .eq(GraphCommunityEntity::getUserId, userId));
    communityMapper.delete(new LambdaQueryWrapper<GraphCommunity>()
        .eq(GraphCommunity::getUserId, userId));
  }

  private String inferCommunityName(List<GraphRagEntity> entities) {
    GraphRagEntity lead = entities.stream()
        .max(Comparator
            .comparing((GraphRagEntity entity) -> safeInt(entity.getOccurrenceCount()))
            .thenComparing(entity -> safeInt(entity.getImportanceScore())))
        .orElse(entities.get(0));
    String name = lead.getName() == null ? "" : lead.getName();
    String type = lead.getType();
    if (name.contains("Redis") || name.contains("缓存"))
      return "Redis能力社区";
    if (name.contains("JVM") || name.contains("GC"))
      return "JVM能力社区";
    if (name.contains("MySQL") || name.contains("索引") || name.contains("事务"))
      return "MySQL能力社区";
    if (name.contains("项目") || name.contains("表达"))
      return "项目表达社区";
    return type == null || type.isBlank() ? name + "相关社区" : type + "社区";
  }

  private void createCommunityAndReport(
      Long userId,
      String name,
      List<GraphRagEntity> entities,
      List<GraphRagRelationship> relationships) {
    double rankScore = calculateRankScore(entities, relationships);
    GraphCommunity community = new GraphCommunity();
    community.setUserId(userId);
    community.setName(name);
    community.setLevel(0);
    community.setEntityCount(entities.size());
    community.setRankScore(rankScore);
    community.setDescription("基于实体关系图聚类生成");
    communityMapper.insert(community);

    for (GraphRagEntity entity : entities) {
      GraphCommunityEntity ce = new GraphCommunityEntity();
      ce.setUserId(userId);
      ce.setCommunityId(community.getId());
      ce.setEntityId(entity.getId());
      ce.setRankScore(entityRankScore(entity, relationships));
      communityEntityMapper.insert(ce);
    }

    String input = buildReportInput(entities, relationships);
    String json = communityReportAgent.generateReport(input);

    CommunityReportResult report = jsonParseService.parseCommunityReport(json);

    GraphCommunityReport dbReport = new GraphCommunityReport();
    dbReport.setUserId(userId);
    dbReport.setCommunityId(community.getId());
    dbReport.setTitle(isBlank(report.getTitle()) ? name : report.getTitle());
    dbReport.setSummary(report.getSummary());
    dbReport.setFindingsJson(toJson(report.getFindings()));
    dbReport.setRankScore(report.getRankScore() == null ? rankScore : report.getRankScore());
    reportMapper.insert(dbReport);
  }

  private List<List<GraphRagEntity>> detectCommunities(
      List<GraphRagEntity> entities,
      List<GraphRagRelationship> relationships,
      Map<Long, GraphRagEntity> entitiesById) {
    Map<Long, Map<Long, Double>> adjacency = new HashMap<>();
    for (GraphRagEntity entity : entities) {
      if (entity.getId() != null) {
        adjacency.put(entity.getId(), new HashMap<>());
      }
    }
    if (relationships != null) {
      for (GraphRagRelationship relationship : relationships) {
        Long sourceId = relationship.getSourceEntityId();
        Long targetId = relationship.getTargetEntityId();
        if (!entitiesById.containsKey(sourceId) || !entitiesById.containsKey(targetId)
            || sourceId.equals(targetId)) {
          continue;
        }
        double weight = relationship.getWeight() == null ? 1.0 : relationship.getWeight();
        adjacency.get(sourceId).merge(targetId, weight, Double::sum);
        adjacency.get(targetId).merge(sourceId, weight, Double::sum);
      }
    }

    Map<Long, Long> labels = entitiesById.keySet().stream()
        .collect(Collectors.toMap(Function.identity(), Function.identity()));
    List<Long> sortedIds = entitiesById.keySet().stream().sorted().toList();
    for (int iteration = 0; iteration < COMMUNITY_ITERATIONS; iteration++) {
      boolean changed = false;
      for (Long entityId : sortedIds) {
        Map<Long, Double> neighbours = adjacency.get(entityId);
        if (neighbours == null || neighbours.isEmpty()) {
          continue;
        }
        Map<Long, Double> labelScores = new HashMap<>();
        neighbours.forEach((neighbourId, weight) ->
            labelScores.merge(labels.get(neighbourId), weight, Double::sum));
        Long bestLabel = labelScores.entrySet().stream()
            .max(Comparator.<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue)
                .thenComparing(entry -> -entry.getKey()))
            .map(Map.Entry::getKey)
            .orElse(labels.get(entityId));
        if (!bestLabel.equals(labels.get(entityId))) {
          labels.put(entityId, bestLabel);
          changed = true;
        }
      }
      if (!changed) {
        break;
      }
    }

    Map<Long, List<GraphRagEntity>> grouped = new LinkedHashMap<>();
    for (Long entityId : sortedIds) {
      grouped.computeIfAbsent(labels.get(entityId), ignored -> new ArrayList<>())
          .add(entitiesById.get(entityId));
    }
    return grouped.values().stream()
        .sorted(Comparator.<List<GraphRagEntity>>comparingInt(List::size).reversed())
        .toList();
  }

  private List<GraphRagRelationship> internalRelationships(
      List<GraphRagEntity> entities,
      List<GraphRagRelationship> relationships) {
    if (relationships == null || relationships.isEmpty()) {
      return List.of();
    }
    Set<Long> entityIds = entities.stream().map(GraphRagEntity::getId).collect(Collectors.toSet());
    return relationships.stream()
        .filter(relationship -> entityIds.contains(relationship.getSourceEntityId())
            && entityIds.contains(relationship.getTargetEntityId()))
        .toList();
  }

  private String buildReportInput(
      List<GraphRagEntity> entities,
      List<GraphRagRelationship> relationships) {
    StringBuilder sb = new StringBuilder();

    sb.append("实体列表：\n");
    for (GraphRagEntity e : entities) {
      sb.append("- ")
          .append(e.getName())
          .append("：")
          .append(e.getDescription())
          .append("\n");
    }
    sb.append("\n关系列表：\n");
    for (GraphRagRelationship relationship : relationships) {
      sb.append("- ")
          .append(relationship.getSourceEntityId())
          .append(" -")
          .append(relationship.getRelationType())
          .append("-> ")
          .append(relationship.getTargetEntityId())
          .append("：")
          .append(relationship.getDescription())
          .append("\n");
    }

    return sb.toString();
  }

  private double calculateRankScore(
      List<GraphRagEntity> entities,
      List<GraphRagRelationship> relationships) {
    double edgeScore = relationships.stream()
        .mapToDouble(relationship -> relationship.getWeight() == null ? 1.0 : relationship.getWeight())
        .sum();
    int occurrences = entities.stream()
        .mapToInt(entity -> safeInt(entity.getOccurrenceCount()))
        .sum();
    return edgeScore + occurrences;
  }

  private double entityRankScore(
      GraphRagEntity entity,
      List<GraphRagRelationship> relationships) {
    return safeInt(entity.getOccurrenceCount())
        + relationships.stream()
            .filter(relationship -> entity.getId().equals(relationship.getSourceEntityId())
                || entity.getId().equals(relationship.getTargetEntityId()))
            .mapToDouble(relationship -> relationship.getWeight() == null ? 1.0 : relationship.getWeight())
            .sum();
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      return "[]";
    }
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
