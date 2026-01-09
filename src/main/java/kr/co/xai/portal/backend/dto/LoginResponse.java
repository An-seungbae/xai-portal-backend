package kr.co.xai.portal.backend.dto;

import java.util.List;

public class LoginResponse {

    private String tokenType;
    private String accessToken;
    private String username;
    private List<String> roles;

    public LoginResponse(String tokenType, String accessToken, String username, List<String> roles) {
        this.tokenType = tokenType;
        this.accessToken = accessToken;
        this.username = username;
        this.roles = roles;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getRoles() {
        return roles;
    }
}
