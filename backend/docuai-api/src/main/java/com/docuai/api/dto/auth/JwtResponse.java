package com.docuai.api.dto.auth;

import com.docuai.api.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private String email;
    private List<String> roles;
    private List<String> permissions;
    private UserDTO user;

    public JwtResponse(String accessToken, String refreshToken, String email,
                        List<String> roles, List<String> permissions, UserDTO user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.email = email;
        this.roles = roles;
        this.permissions = permissions;
        this.user = user;
    }
}
