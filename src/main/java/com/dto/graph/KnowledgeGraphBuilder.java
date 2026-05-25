package com.dto.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.StrengthDTO;
import com.dto.extraction.WeaknessDTO;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.mapper.KnowledgeNodeMapper;
import com.mapper.KnowledgeRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI知识图谱构建器
 *
 * 负责：
 * 1. 从 AI 提炼结果中抽取知识点
 * 2. 创建知识节点
 * 3. 创建知识关系
 * 4. 更新掌握程度
 */
@Component
@RequiredArgsConstructor
public class KnowledgeGraphBuilder {
  private final KnowledgeNodeMapper knowledgeNodeMapper;
  private final KnowledgeRelationMapper knowledgeRelationMapper;

  public void buildGraph(Long userId, KnowledgeExtractionResult extraction) {
    List<GraphRagEntity> allNode = new ArrayList<>();
    if (extraction.getStrengths() != null) {
      for (StrengthDTO strength : extraction.getStrengths()) {
        GraphRagEntity node = createOrUpdateNode(userId, strength.getName(), "TECH", 80, 70);
        allNode.add(node);
      }
    }
    if (extraction.getWeaknesses() != null) {
      for (WeaknessDTO weakness : extraction.getWeaknesses()) {
        int mastery = Math.max(
            10,
            100 - weakness.getLevel() * 20);

        GraphRagEntity node = createOrUpdateNode(
            userId,
            weakness.getName(),
            "TECH",
            mastery,
            90);

        allNode.add(node);
      }
    }
    buildRelation(userId, allNode);
  }

  private GraphRagEntity createOrUpdateNode(Long userId, String name, String type, Integer mastery, Integer importance) {
    // 查询是否已存在
    GraphRagEntity exist = knowledgeNodeMapper.selectOne(
        new LambdaQueryWrapper<GraphRagEntity>()
            .eq(GraphRagEntity::getUserId, userId)
            .eq(GraphRagEntity::getName, name)
            .last("LIMIT 1"));
    // 已存在 → 更新
    if (exist != null) {

      exist.setOccurrenceCount(
          safeInt(exist.getOccurrenceCount()) + 1);

      // 取较低掌握度（保守策略）
      exist.setImportanceScore(
          Math.min(
              safeInt(exist.getImportanceScore()),
              mastery));

      knowledgeNodeMapper.updateById(exist);

      return exist;
    }

    // 不存在 → 创建
    GraphRagEntity node = new GraphRagEntity();

    node.setUserId(userId);
    node.setName(name);
    node.setType(type);
    node.setImportanceScore(importance);
    node.setOccurrenceCount(1);

    knowledgeNodeMapper.insert(node);

    return node;
  }

  /**
   * 自动建立知识关系
   */
  private void buildRelation(Long userId, List<GraphRagEntity> nodes) {
    if (nodes.size() < 2) {
      return;
    }
    for (int i = 0; i < nodes.size(); i++) {

      for (int j = i + 1; j < nodes.size(); j++) {

        GraphRagEntity source = nodes.get(i);

        GraphRagEntity target = nodes.get(j);

        // 避免重复关系
        boolean exists = relationExists(
            source.getId(),
            target.getId());

        if (exists) {
          continue;
        }

        GraphRagRelationship relation = new GraphRagRelationship();

        relation.setUserId(userId);

        relation.setSourceEntityId(
            source.getId());

        relation.setTargetEntityId(
            target.getId());

        relation.setRelationType(
            "RELATED");

        relation.setWeight(0.7);

        knowledgeRelationMapper.insert(relation);
      }
    }
  }

  private boolean relationExists(
      Long sourceId,
      Long targetId) {

    Long count = knowledgeRelationMapper.selectCount(
        new LambdaQueryWrapper<GraphRagRelationship>()
            .eq(
                GraphRagRelationship::getSourceEntityId,
                sourceId)
            .eq(
                GraphRagRelationship::getTargetEntityId,
                targetId));

    return count > 0;
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : value;
  }

}
