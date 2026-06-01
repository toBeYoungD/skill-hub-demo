package com.skillhub.dao;

import com.skillhub.domain.SkillChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SkillChangeLogMapper {

    int insert(SkillChangeLog log);

    List<SkillChangeLog> selectByCreatedAtAfter(@Param("since") LocalDateTime since);
}