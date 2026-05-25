package com.service.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.graph.GraphReasoningResult;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.mapper.KnowledgeNodeMapper;
import com.mapper.KnowledgeRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 图谱推理服务
 *
 * 核心职责：
 * 根据知识图谱：
 * 1. 推断用户潜在薄弱点
 * 2. 推荐下一步追问
 * 3. 推荐学习路径
 *
 * 当前版本：
 * 基于规则推理
 */
@Service
@RequiredArgsConstructor
public class GraphReasoningService {
  private final KnowledgeNodeMapper knowledgeNodeMapper;
  private final KnowledgeRelationMapper knowledgeRelationMapper;

  /**
   * 获取推荐追问知识点
   *
   * 推理逻辑：
   * 如果当前知识点很弱，
   * 则继续沿图谱向下追问。
   */
  public List<String> recommendFollowUptopics(Long userId, String weakTopic) {
    List<String> result = new ArrayList<>();
    GraphRagEntity weakNode = knowledgeNodeMapper.selectOne(
        new LambdaQueryWrapper<GraphRagEntity>()
            .eq(GraphRagEntity::getUserId, userId)
            .eq(GraphRagEntity::getName, weakTopic)
            .last("LIMIT 1"));
    if (weakNode == null) {
      return null;
    }
    List<GraphRagRelationship> relations = knowledgeRelationMapper.selectList(
        new LambdaQueryWrapper<GraphRagRelationship>()
            .eq(
                GraphRagRelationship::getSourceEntityId,
                weakNode.getId()));
    for (GraphRagRelationship relation : relations) {
      GraphRagEntity target = knowledgeNodeMapper.selectById(relation.getTargetEntityId());
      if (target == null) {
        continue;
      }
      if (target.getImportanceScore() != null && target.getImportanceScore() < 60) {

        result.add(
            target.getName());
      }
    }
    return result;
  }

  /**
   * 推荐学习路径
   * 例如：
   * Redis
   * ↓
   * 缓存一致性
   * ↓
   * 延迟双删
   */
  public List<String> recommendLearningPath(Long userId, String topic) {
    List<String> path = new ArrayList<>();
    GraphRagEntity node = knowledgeNodeMapper.selectOne(
        new LambdaQueryWrapper<GraphRagEntity>()
            .eq(GraphRagEntity::getUserId, userId)
            .eq(GraphRagEntity::getName, topic)
            .last("LIMIT 1"));
    if (node == null) {
      return path;
    }
    path.add(node.getName());
    List<GraphRagRelationship> relations = knowledgeRelationMapper.selectList(
        new LambdaQueryWrapper<GraphRagRelationship>()
            .eq(
                GraphRagRelationship::getSourceEntityId,
                node.getId()));
    for (GraphRagRelationship relation : relations) {
      GraphRagEntity next = knowledgeNodeMapper.selectById(
          relation.getTargetEntityId());

      if (next != null) {

        path.add(
            next.getName());
      }
    }
    return path;
  }

  /**
   * 图谱推理
   *
   * 根据当前 weakPoint：
   * 1. 推断下一步追问知识点
   * 2. 推断学习路径
   */
  public GraphReasoningResult reasonWeakness(
      Long userId,
      String weakTopic) {

    GraphReasoningResult result = new GraphReasoningResult();

    result.setWeakTopic(weakTopic);

    // 推荐追问知识点
    List<String> followUpTopics = recommendFollowUptopics(
        userId,
        weakTopic);

    result.setFollowUpTopics(
        followUpTopics);

    // 推荐学习路径
    List<String> learningPath = recommendLearningPath(
        userId,
        weakTopic);

    result.setLearningPath(
        learningPath);

    // 推理说明
    result.setReasoning(
        """
            系统发现用户在 [%s] 上较薄弱，
            根据知识图谱关系，
            推荐继续深挖相关知识点：
            %s
            """.formatted(
            weakTopic,
            String.join(
                "、",
                followUpTopics)));

    return result;
  }
}
