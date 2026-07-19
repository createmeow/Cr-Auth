package com.crskin.db;

/**
 * 玩家映射数据模型
 */
public class PlayerMapping {
    public String crskinUuid;
    public String originalUuid;
    public String source;
    public String sourceApiUrl;
    public String originalUsername;
    public String mappedUsername;
    public String mergedIntoUuid;
    public String createdAt;
    public String updatedAt;

    public PlayerMapping() {}

    public PlayerMapping(String crskinUuid, String originalUuid, String source,
                         String sourceApiUrl, String originalUsername, String mappedUsername) {
        this.crskinUuid = crskinUuid;
        this.originalUuid = originalUuid;
        this.source = source;
        this.sourceApiUrl = sourceApiUrl;
        this.originalUsername = originalUsername;
        this.mappedUsername = mappedUsername;
    }
}
