package com.skillhub.service;

import com.skillhub.exception.SkillPackageValidationException;
import org.springframework.web.multipart.MultipartFile;

public interface SkillPackageValidator {
    void validate(MultipartFile file) throws SkillPackageValidationException;
}