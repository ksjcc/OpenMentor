package com.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.entity.Resume;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
