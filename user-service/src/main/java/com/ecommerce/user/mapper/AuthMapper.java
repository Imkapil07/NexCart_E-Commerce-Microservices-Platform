package com.ecommerce.user.mapper;

import com.ecommerce.user.dto.LoginResponseDto;
import com.ecommerce.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Maps User + JWT token to login response DTO.
 **/
@Component
public class AuthMapper {
    /**
     * Builds login response with token and user id, email, name.
     **/
    public LoginResponseDto toLoginResponse(User user, String token) {
        return new LoginResponseDto(token, "Bearer", user.getId(), user.getEmail(), user.getName(), user.getRoles());
    }
}
