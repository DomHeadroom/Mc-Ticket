package it.domheadroom.mc_ticket.dto;

import lombok.Getter;

@Getter
public class LoginResponse {

    private String token;
    private String email;
    private String role;
    private String fullName;

    public LoginResponse(String token, String email, String role, String fullName) {
        this.token = token;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
    }

}
