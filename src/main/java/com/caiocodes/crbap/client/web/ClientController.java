package com.caiocodes.crbap.client.web;

import com.caiocodes.crbap.client.application.ClientCommands;
import com.caiocodes.crbap.client.application.ClientDetail;
import com.caiocodes.crbap.client.application.ClientSummary;
import com.caiocodes.crbap.client.application.CreateClientUseCase;
import com.caiocodes.crbap.client.application.DeactivateClientUseCase;
import com.caiocodes.crbap.client.application.GetClientUseCase;
import com.caiocodes.crbap.client.application.UpdateClientUseCase;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientStatus;
import com.caiocodes.crbap.client.web.dto.ClientDtos.ContactRequest;
import com.caiocodes.crbap.client.web.dto.ClientDtos.CreateClientRequest;
import com.caiocodes.crbap.client.web.dto.ClientDtos.UpdateClientRequest;
import com.caiocodes.crbap.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clientes.
 *
 * <p>Nenhum endpoint recebe o id do gestor como filtro: o escopo de carteira é
 * decidido no caso de uso, a partir de quem está autenticado. Trocar valores na
 * query string não muda o que o vendedor enxerga.
 */
@Tag(name = "Clientes")
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final CreateClientUseCase createClient;
    private final UpdateClientUseCase updateClient;
    private final DeactivateClientUseCase deactivateClient;
    private final GetClientUseCase getClient;

    @Operation(summary = "Cadastra um cliente")
    @PostMapping
    @PreAuthorize("hasAuthority('client:create')")
    public ResponseEntity<ClientDetail> create(@Valid @RequestBody CreateClientRequest body) {
        ClientDetail created = createClient.execute(body.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id())).body(created);
    }

    @Operation(summary = "Lista e busca clientes (dentro da carteira de quem chama)")
    @GetMapping
    @PreAuthorize("hasAuthority('client:read')")
    public PageResponse<ClientSummary> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(getClient.search(search, status, page, size));
    }

    @Operation(summary = "Detalha um cliente")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('client:read')")
    public ClientDetail byId(@PathVariable UUID id) {
        return getClient.byId(ClientId.of(id));
    }

    @Operation(summary = "Atualiza os dados cadastrais (o documento é imutável)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('client:update')")
    public ClientDetail update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateClientRequest body) {
        return updateClient.execute(new ClientCommands.UpdateClient(ClientId.of(id),
                body.legalName(), body.tradeName(), body.email(), body.phone(),
                body.address() == null ? null : body.address().toDomain(), body.notes()));
    }

    @Operation(summary = "Adiciona um contato")
    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasAuthority('client:update')")
    public ClientDetail addContact(@PathVariable UUID id,
                                   @Valid @RequestBody ContactRequest body) {
        return updateClient.addContact(
                new ClientCommands.AddContact(ClientId.of(id), body.toCommand()));
    }

    @Operation(summary = "Desativa o cliente (exclusão lógica)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('client:delete')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        deactivateClient.deactivate(ClientId.of(id));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reativa um cliente desativado")
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('client:update')")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        deactivateClient.reactivate(ClientId.of(id));
        return ResponseEntity.noContent().build();
    }
}
