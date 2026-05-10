package com.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dto.graph.*;
import com.entity.KnowledgeNode;
import com.entity.KnowledgeRelation;
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
    List<KnowledgeNode> nodes = knowledgeNodeMapper.selectList(
        new LambdaQueryWrapper<KnowledgeNode>()
            .eq(KnowledgeNode::getUserId, userId));
    List<KnowledgeRelation> relations = knowledgeRelationMapper.selectList(
        new LambdaQueryWrapper<KnowledgeRelation>()
            .eq(KnowledgeRelation::getUserId, userId));
    List<GraphNodeDTO> nodeDTOs = new ArrayList<>();
    for (KnowledgeNode node : nodes) {

      GraphNodeDTO dto = new GraphNodeDTO();

      dto.setId(node.getId());
      dto.setName(node.getTitle());
      dto.setType(node.getNodeType());
      dto.setMasteryLevel(node.getMasteryLevel());
      dto.setImportanceScore(node.getImportanceScore());

      nodeDTOs.add(dto);
    }

    List<GraphLinkDTO> linkDTOs = new ArrayList<>();

    for (KnowledgeRelation relation : relations) {

      GraphLinkDTO dto = new GraphLinkDTO();

      dto.setSource(
          relation.getSourceNodeId());

      dto.setTarget(
          relation.getTargetNodeId());

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
