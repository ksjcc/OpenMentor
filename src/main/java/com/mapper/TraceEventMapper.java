package com.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.entity.Trace.TraceEventEntity;

@Mapper

public interface TraceEventMapper extends BaseMapper<TraceEventEntity> {

}
