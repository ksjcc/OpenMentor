package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.graph.*;
import com.entity.graphrag.GraphRagEntity;
import com.entity.graphrag.GraphRagRelationship;
import com.mapper.KnowledgeNodeMapper;
import com.mapper.KnowledgeRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱服务
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {
  private final KnowledgeNodeMapper knowledgeNodeMapper;
  private final KnowledgeRelationMapper knowledgeRelationMapper;

  public GraphDTO getGraph(Long userId) {
    GraphDTO graph = new GraphDTO();
    List<GraphRagEntity> nodes = knowledgeNodeMapper.selectList(
        new LambdaQueryWrapper<GraphRagEntity>()
            .eq(GraphRagEntity::getUserId, userId));
    List<GraphRagRelationship> relations = knowledgeRelationMapper.selectList(
        new LambdaQueryWrapper<GraphRagRelationship>()
            .eq(GraphRagRelationship::getUserId, userId));
    List<GraphNodeDTO> nodeDTOs = new ArrayList<>();
    for (GraphRagEntity node : nodes) {

      GraphNodeDTO dto = new GraphNodeDTO();

      dto.setId(node.getId());
      dto.setName(node.getName());
      dto.setType(node.getType());
      dto.setMasteryLevel(node.getImportanceScore());
      dto.setImportanceScore(node.getImportanceScore());

      nodeDTOs.add(dto);
    }

    List<GraphLinkDTO> linkDTOs = new ArrayList<>();

    for (GraphRagRelationship relation : relations) {

      GraphLinkDTO dto = new GraphLinkDTO();

      dto.setSource(
          relation.getSourceEntityId());

      dto.setTarget(
          relation.getTargetEntityId());

      dto.setRelationType(
          relation.getRelationType());

      dto.setWeight(
          relation.getWeight());

      linkDTOs.add(dto);
    }

    graph.setNodes(nodeDTOs);
    graph.setLinks(linkDTOs);

    return graph;
  }

}
