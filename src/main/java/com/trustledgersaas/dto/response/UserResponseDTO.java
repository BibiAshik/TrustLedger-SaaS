package com.trustledgersaas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserResponseDTO — Carries basic user data for UI display.
 *
 * Used after login to tell the frontend who is logged in, their role,
 * and where to redirect them. Also used internally when showing user info.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private boolean isFirstLogin;
}
