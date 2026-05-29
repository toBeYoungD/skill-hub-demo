package com.skillhub.repository;

import com.skillhub.entity.SkillChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SkillChangeLogRepository extends JpaRepository<SkillChangeLog, Long> {
    List<SkillChangeLog> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime since);
}