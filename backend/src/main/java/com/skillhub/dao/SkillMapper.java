package com.skillhub.dao;

import com.skillhub.domain.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillMapper {

    int insert(Skill skill);

    Skill selectById(@Param("id") Long id);

    int update(Skill skill);

    int deleteById(@Param("id") Long id);

    // ========== 业务查询 ==========

    List<Skill> selectAll();

    List<Skill> selectByStatus(@Param("status") String status);

    Skill selectByName(@Param("name") String name);

    boolean existsByName(@Param("name") String name);

    int countByNameExcludingId(@Param("name") String name, @Param("id") Long id);

    // 分页列表
    List<Skill> selectByStatusAndName(@Param("status") String status, @Param("name") String name,
                                      @Param("offset") int offset, @Param("limit") int limit);

    List<Skill> selectByStatusOnly(@Param("status") String status,
                                   @Param("offset") int offset, @Param("limit") int limit);

    List<Skill> selectByNameOnly(@Param("name") String name,
                                 @Param("offset") int offset, @Param("limit") int limit);

    List<Skill> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    long countByStatusAndName(@Param("status") String status, @Param("name") String name);

    long countByStatus(@Param("status") String status);

    long countByName(@Param("name") String name);

    long countAll();

    // 已发布且未下架
    List<Skill> selectPublishedAndNotDelisted();

    // 统计更新
    int incrementDownload(@Param("id") Long id);

    int incrementUse(@Param("id") Long id);
}