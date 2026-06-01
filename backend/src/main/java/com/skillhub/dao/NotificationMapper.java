package com.skillhub.dao;

import com.skillhub.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    int insert(Notification notification);

    List<Notification> selectByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    List<Notification> selectByUserIdAndReadFalse(@Param("userId") String userId);

    int updateRead(@Param("id") Long id, @Param("read") boolean read);

    int markAllRead(@Param("userId") String userId);
}