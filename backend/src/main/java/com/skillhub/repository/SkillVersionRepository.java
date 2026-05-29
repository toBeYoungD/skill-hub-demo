package com.skillhub.repository;

import com.skillhub.domain.SkillVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillVersionRepository extends JpaRepository<SkillVersion, Long> {

    List<SkillVersion> findBySkillIdOrderByCreatedAtDesc(Long skillId);

    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);
}