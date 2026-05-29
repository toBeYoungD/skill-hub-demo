package com.skillhub.repository;

import com.skillhub.domain.PublishRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishRequestRepository extends JpaRepository<PublishRequest, Long> {
    List<PublishRequest> findBySkillIdOrderByCreatedAtDesc(Long skillId);
    List<PublishRequest> findByStatusOrderByCreatedAtDesc(PublishRequest.RequestStatus status);
    List<PublishRequest> findByApplicantOrderByCreatedAtDesc(String applicant);
}