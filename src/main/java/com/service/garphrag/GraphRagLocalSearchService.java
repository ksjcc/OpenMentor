package com.service.garphrag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ai.rag.HydeQueryExpansionService;
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
  private final HydeQueryExpansionService hydeQueryExpansionService;

  public List<String> search(Long userId, String query) {
    List<String> context = new ArrayList<>();
    String hydeQuery = hydeQueryExpansionService.expand(query);
    List<GraphRagEntity> entities = entityMapper.searchByName(userId, hydeQuery);
    for (GraphRagEntity entity : entities) {
      context.add("实体：" + entity.getName()
          + "，说明：" + entity.getDescription());
      List<GraphRagRelationship> rels = relationshipMapper.selectBySourceEntityId(entity.getId());
      for (GraphRagRelationship rel : rels) {
        GraphRagEntity target = entityMapper.selectById(
            rel.getTargetEntityId());
        if (target != null) {

          context.add(
              "关系："
                  + entity.getName()
                  + " -"
                  + rel.getRelationType()
                  + "-> "
                  + target.getName()
                  + "，说明："
                  + rel.getDescription());
        }
        GraphTextUnit unit = textUnitMapper.selectById(
            rel.getTextUnitId());
        if (unit != null) {
          context.add(
              "来源片段："
                  + unit.getContent());
        }
      }
    }
    List<String> vectorChunks = vectorStoreService.search(
        userId,
        hydeQuery,
        5);

    context.addAll(vectorChunks);

    return context.stream()
        .distinct()
        .limit(12)
        .toList();
  }
}
