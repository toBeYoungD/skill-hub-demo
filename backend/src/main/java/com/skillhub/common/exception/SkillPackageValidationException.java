package com.skillhub.common.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class SkillPackageValidationException extends RuntimeException {
    private final List<String> errors;

    public SkillPackageValidationException(List<String> errors) {
        super("技能包格式校验失败: " + String.join("; ", errors));
        this.errors = errors;
    }
}