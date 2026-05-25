package com.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.entity.graphrag.GraphRagEntity;

public interface GraphEntityMapper extends BaseMapper<GraphRagEntity> {

  @Select("""
      SELECT *
      FROM graph_entity
      WHERE user_id = #{userId}
        AND name = #{name}
      LIMIT 1
      """)
  GraphRagEntity selectByUserAndName(@Param("userId") Long userId, @Param("name") String name);

  @Select("""
      SELECT *
      FROM graph_entity
      WHERE user_id = #{userId}
      ORDER BY occurrence_count DESC, id DESC
      """)
  List<GraphRagEntity> selectByUserId(@Param("userId") Long userId);

  @Select("""
      SELECT *
      FROM graph_entity
      WHERE user_id = #{userId}
        AND (
          name LIKE CONCAT('%', #{query}, '%')
          OR description LIKE CONCAT('%', #{query}, '%')
          OR type LIKE CONCAT('%', #{query}, '%')
        )
      ORDER BY occurrence_count DESC, importance_score DESC, id DESC
      LIMIT 10
      """)
  List<GraphRagEntity> searchByName(@Param("userId") Long userId, @Param("query") String query);

}
