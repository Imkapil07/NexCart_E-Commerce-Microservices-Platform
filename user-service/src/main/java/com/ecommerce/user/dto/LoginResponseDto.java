package com.ecommerce.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO after successful login: JWT token and user info (custom login only).
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private String name;
    /** User role e.g. ROLE_ADMIN, ROLE_USER */
    private String role;
}
