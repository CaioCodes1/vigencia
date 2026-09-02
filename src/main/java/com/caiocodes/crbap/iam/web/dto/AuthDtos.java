package com.caiocodes.crbap.iam.web.dto;

import com.caiocodes.crbap.iam.application.AuthResult;
import com.caiocodes.crbap.iam.application.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTOs de entrada e saída do módulo de autenticação.
 *
 * <p>Agrupados num arquivo só porque são pequenos e mudam juntos. Repare que o
 * request de login <b>não</b> tem campo de papel nem de permissão: o que o
 * cliente pode mandar é decidido aqui, e não pelo formato da tabela.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Schema(example = "admin@crbap.local") String email,
            @NotBlank @Schema(example = "troque-esta-senha") String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** O refresh é opcional: dá para encerrar só o access token atual. */
    public record LogoutRequest(String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user) {

        public static AuthResponse from(AuthResult result) {
            return new AuthResponse(result.accessToken(), result.refreshToken(), "Bearer",
                    result.expiresInSeconds(), UserResponse.from(result.user()));
        }
    }

    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            boolean active,
            Set<String> roles,
            Set<String> permissions,
            boolean mustChangePassword,
            Instant lastLoginAt) {

        public static UserResponse from(UserProfile profile) {
            return new UserResponse(profile.id(), profile.email(), profile.fullName(),
                    profile.active(), profile.roles(), profile.permissions(),
                    profile.mustChangePassword(), profile.lastLoginAt());
        }
    }
}
