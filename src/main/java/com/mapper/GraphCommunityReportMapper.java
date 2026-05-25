package com.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.entity.graphrag.GraphCommunityReport;

public interface GraphCommunityReportMapper extends BaseMapper<GraphCommunityReport> {
    @Select("""
            SELECT * FROM graph_community_report
            WHERE user_id = #{userId}
            ORDER BY rank_score DESC, id DESC
            LIMIT #{limit}
            """)
    List<GraphCommunityReport> selectTopReports(@Param("userId") Long userId,
            @Param("limit") int limit);
}
