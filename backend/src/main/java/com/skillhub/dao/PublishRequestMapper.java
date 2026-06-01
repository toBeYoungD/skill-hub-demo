package com.skillhub.dao;

import com.skillhub.domain.PublishRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PublishRequestMapper {

    int insert(PublishRequest request);

    PublishRequest selectById(@Param("id") Long id);

    int update(PublishRequest request);

    List<PublishRequest> selectAll();

    List<PublishRequest> selectBySkillIdOrderByCreatedAtDesc(@Param("skillId") Long skillId);

    List<PublishRequest> selectByStatus(@Param("status") String status);

    /** 取消某技能所有 PENDING 的申请 */
    int cancelPendingBySkillId(@Param("skillId") Long skillId, @Param("reason") String reason,
                                 @Param("reviewedAt") java.time.LocalDateTime reviewedAt);
}