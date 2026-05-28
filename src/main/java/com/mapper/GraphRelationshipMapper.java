package com.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.entity.graphrag.GraphRagRelationship;

public interface GraphRelationshipMapper extends BaseMapper<GraphRagRelationship> {

  @Select("""
      SELECT *
      FROM graph_relationship
      WHERE source_entity_id = #{sourceEntityId}
      ORDER BY weight DESC, id DESC
      """)
  List<GraphRagRelationship> selectBySourceEntityId(@Param("sourceEntityId") Long sourceEntityId);

  @Select("""
      SELECT *
      FROM graph_rag_relationship
      WHERE source_entity_id = #{entityId}
      OR target_entity_id = #{entityId}
      """)
  List<GraphRagRelationship> selectByEntityId(
      @Param("entityId") Long entityId);

  @Select("""
      SELECT *
      FROM graph_relationship
      WHERE user_id = #{userId}
        AND (source_entity_id = #{entityId} OR target_entity_id = #{entityId})
      ORDER BY weight DESC, id DESC
      """)
  List<GraphRagRelationship> selectByUserAndEntityId(
      @Param("userId") Long userId,
      @Param("entityId") Long entityId);

  @Select("""
      SELECT *
      FROM graph_relationship
      WHERE user_id = #{userId}
        AND source_entity_id = #{sourceEntityId}
        AND target_entity_id = #{targetEntityId}
        AND relation_type = #{relationType}
      LIMIT 1
      """)
  GraphRagRelationship selectExisting(
      @Param("userId") Long userId,
      @Param("sourceEntityId") Long sourceEntityId,
      @Param("targetEntityId") Long targetEntityId,
      @Param("relationType") String relationType);

  @Select("""
      SELECT *
      FROM graph_relationship
      WHERE user_id = #{userId}
      ORDER BY weight DESC, id DESC
      """)
  List<GraphRagRelationship> selectByUserId(@Param("userId") Long userId);

}
