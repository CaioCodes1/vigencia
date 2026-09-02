package com.caiocodes.crbap.iam.web;

import com.caiocodes.crbap.iam.application.GetUserProfileUseCase;
import com.caiocodes.crbap.iam.domain.UserId;
import com.caiocodes.crbap.iam.web.dto.AuthDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de usuários.
 *
 * <p>A autorização é por <b>permissão</b> ({@code user:read}), nunca por papel.
 * No dia em que o financeiro precisar ver a lista, é uma linha em
 * {@code role_permissions} — sem recompilar, sem deploy, sem caçar
 * {@code hasRole} espalhado por 40 controllers.
 */
@Tag(name = "Usuários")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserProfileUseCase profiles;

    @Operation(summary = "Lista os usuários")
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public List<UserResponse> list() {
        return profiles.all().stream().map(UserResponse::from).toList();
    }

    @Operation(summary = "Detalha um usuário")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserResponse byId(@PathVariable UUID id) {
        return UserResponse.from(profiles.byId(UserId.of(id)));
    }
}
