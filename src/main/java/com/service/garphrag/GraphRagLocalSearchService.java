package com.service.garphrag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.rag.VectorStoreService;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.entity.graphrag.GraphTextUnit;
import com.mapper.GraphEntityMapper;
import com.mapper.GraphRelationshipMapper;
import com.mapper.GraphTextUnitMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraphRagLocalSearchService {
  private final GraphEntityMapper entityMapper;
  private final GraphRelationshipMapper relationshipMapper;
  private final GraphTextUnitMapper textUnitMapper;
  private final VectorStoreService vectorStoreService;

  public List<String> search(Long userId, String query) {
    return search(userId, query, null);
  }

  public List<String> search(Long userId, String query, RetrievalBudget budget) {
    if (budget != null && !budget.consumeGraphHop()) {
      return List.of();
    }

    List<String> context = new ArrayList<>();
    List<GraphRagEntity> entities = entityMapper.searchByName(userId, query);
    for (GraphRagEntity entity : entities) {
      if (!appendWithinBudget(context, "实体：" + entity.getName()
          + "，说明：" + entity.getDescription(), budget)) {
        return context.stream().distinct().toList();
      }
      List<GraphRagRelationship> rels = relationshipMapper.selectBySourceEntityId(entity.getId());
      for (GraphRagRelationship rel : rels) {
        GraphRagEntity target = entityMapper.selectById(
            rel.getTargetEntityId());
        if (target != null) {
          if (!appendWithinBudget(context,
              "关系："
                  + entity.getName()
                  + " -"
                  + rel.getRelationType()
                  + "-> "
                  + target.getName()
                  + "，说明："
                  + rel.getDescription(),
              budget)) {
            return context.stream().distinct().toList();
          }
        }
        GraphTextUnit unit = textUnitMapper.selectById(
            rel.getTextUnitId());
        if (unit != null) {
          if (!appendWithinBudget(context,
              "来源片段："
                  + unit.getContent(),
              budget)) {
            return context.stream().distinct().toList();
          }
        }
      }
    }
    List<String> vectorChunks = vectorStoreService.search(
        userId,
        query,
        5);
    for (String vectorChunk : vectorChunks) {
      if (!appendWithinBudget(context, vectorChunk, budget)) {
        break;
      }
    }

    return context.stream()
        .distinct()
        .limit(12)
        .toList();
  }

  private boolean appendWithinBudget(List<String> context, String content, RetrievalBudget budget) {
    if (budget != null && !budget.consumeChunk(1)) {
      return false;
    }
    context.add(content);
    return true;
  }
}
