package com.skillhub.util;

import org.springframework.stereotype.Component;

@Component
public class VersionUtil {

    public enum VersionType {
        PATCH
    }

    public String generateNextVersion(String currentVersion, VersionType versionType) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return "1";
        }
        try {
            int v = Integer.parseInt(currentVersion);
            return String.valueOf(v + 1);
        } catch (NumberFormatException e) {
            return "1";
        }
    }

    public int compareVersions(String v1, String v2) {
        try {
            return Integer.parseInt(v2) - Integer.parseInt(v1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}