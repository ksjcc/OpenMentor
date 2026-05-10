package com.dto.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.extraction.KnowledgeExtractionResult;
import com.dto.extraction.StrengthDTO;
import com.dto.extraction.WeaknessDTO;
import com.entity.KnowledgeNode;
import com.entity.KnowledgeRelation;
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
    List<KnowledgeNode> allNode = new ArrayList<>();
    if (extraction.getStrengths() != null) {
      for (StrengthDTO strength : extraction.getStrengths()) {
        KnowledgeNode node = createOrUpdateNode(userId, strength.getName(), "TECH", 80, 70);
        allNode.add(node);
      }
    }
    if (extraction.getWeaknesses() != null) {
      for (WeaknessDTO weakness : extraction.getWeaknesses()) {
        int mastery = Math.max(
            10,
            100 - weakness.getLevel() * 20);

        KnowledgeNode node = createOrUpdateNode(
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

  private KnowledgeNode createOrUpdateNode(Long userId, String name, String type, Integer mastery, Integer importance) {
    // 查询是否已存在
    KnowledgeNode exist = knowledgeNodeMapper.selectOne(
        new LambdaQueryWrapper<KnowledgeNode>()
            .eq(KnowledgeNode::getUserId, userId)
            .eq(KnowledgeNode::getTitle, name)
            .last("LIMIT 1"));
    // 已存在 → 更新
    if (exist != null) {

      exist.setOccurrenceCount(
          safeInt(exist.getOccurrenceCount()) + 1);

      // 取较低掌握度（保守策略）
      exist.setMasteryLevel(
          Math.min(
              safeInt(exist.getMasteryLevel()),
              mastery));

      knowledgeNodeMapper.updateById(exist);

      return exist;
    }

    // 不存在 → 创建
    KnowledgeNode node = new KnowledgeNode();

    node.setUserId(userId);
    node.setTitle(name);
    node.setNodeType(type);
    node.setMasteryLevel(mastery);
    node.setImportanceScore(importance);
    node.setOccurrenceCount(1);

    knowledgeNodeMapper.insert(node);

    return node;
  }

  /**
   * 自动建立知识关系
   */
  private void buildRelation(Long userId, List<KnowledgeNode> nodes) {
    if (nodes.size() < 2) {
      return;
    }
    for (int i = 0; i < nodes.size(); i++) {

      for (int j = i + 1; j < nodes.size(); j++) {

        KnowledgeNode source = nodes.get(i);

        KnowledgeNode target = nodes.get(j);

        // 避免重复关系
        boolean exists = relationExists(
            source.getId(),
            target.getId());

        if (exists) {
          continue;
        }

        KnowledgeRelation relation = new KnowledgeRelation();

        relation.setUserId(userId);

        relation.setSourceNodeId(
            source.getId());

        relation.setTargetNodeId(
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
        new LambdaQueryWrapper<KnowledgeRelation>()
            .eq(
                KnowledgeRelation::getSourceNodeId,
                sourceId)
            .eq(
                KnowledgeRelation::getTargetNodeId,
                targetId));

    return count > 0;
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : value;
  }

}
