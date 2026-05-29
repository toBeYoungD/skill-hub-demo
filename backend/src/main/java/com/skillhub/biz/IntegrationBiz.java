package com.skillhub.biz;

import com.skillhub.dto.BatchCheckRequest;
import java.time.LocalDateTime;
import java.util.Map;

public interface IntegrationBiz {
    Map<String, Object> batchCheck(BatchCheckRequest request);
    Map<String, Object> changelog(LocalDateTime since);
}