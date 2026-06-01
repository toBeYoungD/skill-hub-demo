package com.skillhub.dao;

import com.skillhub.domain.SkillVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillVersionMapper {

    int insert(SkillVersion version);

    SkillVersion selectById(@Param("id") Long id);

    int update(SkillVersion version);

    int deleteById(@Param("id") Long id);

    List<SkillVersion> selectBySkillId(@Param("skillId") Long skillId);

    List<SkillVersion> selectBySkillIdOrderByCreatedAtDesc(@Param("skillId") Long skillId);

    SkillVersion selectBySkillIdAndVersion(@Param("skillId") Long skillId, @Param("version") String version);

    /** 取消某技能所有版本的 is_latest 标记 */
    int unsetLatest(@Param("skillId") Long skillId);
}