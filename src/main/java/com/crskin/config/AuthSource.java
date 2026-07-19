package com.crskin.config;

/**
 * 认证源配置模型
 */
public class AuthSource {
    private final String name;
    private final String serverLink;
    private final String abbreviation;

    public AuthSource(String name, String serverLink, String abbreviation) {
        this.name = name;
        this.serverLink = serverLink;
        this.abbreviation = abbreviation;
    }

    public String getName() { return name; }
    public String getServerLink() { return serverLink; }
    public String getAbbreviation() { return abbreviation; }

    public String getApiRoot() {
        String url = serverLink;
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }
}
