package com.caiocodes.crbap.iam.web;

import com.caiocodes.crbap.iam.application.ChangePasswordUseCase;
import com.caiocodes.crbap.iam.application.GetUserProfileUseCase;
import com.caiocodes.crbap.iam.application.LoginCommand;
import com.caiocodes.crbap.iam.application.LoginUseCase;
import com.caiocodes.crbap.iam.application.LogoutCommand;
import com.caiocodes.crbap.iam.application.LogoutUseCase;
import com.caiocodes.crbap.iam.application.RefreshAccessTokenUseCase;
import com.caiocodes.crbap.iam.application.RefreshCommand;
import com.caiocodes.crbap.iam.infrastructure.security.AuthenticatedUser;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.AuthResponse;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.ChangePasswordRequest;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.LoginRequest;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.LogoutRequest;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.RefreshRequest;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Autenticação.
 *
 * <p>O controller não decide nada: traduz HTTP para comando e comando para HTTP.
 * Nenhum {@code if} de negócio mora aqui — se morasse, o dia em que um job ou um
 * consumidor de fila precisasse da mesma regra, ela teria que ser duplicada.
 */
@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase login;
    private final RefreshAccessTokenUseCase refresh;
    private final LogoutUseCase logout;
    private final ChangePasswordUseCase changePassword;
    private final GetUserProfileUseCase profiles;

    @Operation(summary = "Autentica por e-mail e senha")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return AuthResponse.from(login.execute(new LoginCommand(
                body.email(), body.password(),
                request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr())));
    }

    @Operation(summary = "Renova a sessão, com rotação do refresh token")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest body,
                                HttpServletRequest request) {
        return AuthResponse.from(refresh.execute(new RefreshCommand(
                body.refreshToken(),
                request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr())));
    }

    @Operation(summary = "Encerra a sessão: revoga o refresh e invalida o access token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest body) {
        AuthenticatedUser current = AuthenticatedUser.current();
        logout.execute(new LogoutCommand(
                body == null ? null : body.refreshToken(),
                current.tokenId(),
                current.tokenExpiresAt()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dados do usuário autenticado, com papéis e permissões")
    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(profiles.byId(AuthenticatedUser.current().userId()));
    }

    @Operation(summary = "Troca a própria senha e encerra todas as sessões")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        changePassword.execute(new ChangePasswordUseCase.ChangePasswordCommand(
                AuthenticatedUser.current().userId(), body.currentPassword(), body.newPassword()));
        return ResponseEntity.noContent().build();
    }
}
