package com.skillhub.repository;

import com.skillhub.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long>, JpaSpecificationExecutor<Skill> {

    @Modifying
    @Query("UPDATE Skill s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :id")
    void incrementDownloadCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Skill s SET s.useCount = s.useCount + 1 WHERE s.id = :id")
    void incrementUseCount(@Param("id") Long id);

    List<Skill> findByStatus(Skill.SkillStatus status);

    Optional<Skill> findByIdAndStatus(Long id, Skill.SkillStatus status);

    boolean existsByName(String name);

    Optional<Skill> findByName(String name);

    List<Skill> findByStatusAndDelistedFalse(Skill.SkillStatus status);

    List<Skill> findByDeveloperAndDelistedFalse(String developer);
}