package com.caiocodes.vigencia.contract.web;

import com.caiocodes.vigencia.contract.application.ActivateContractUseCase;
import com.caiocodes.vigencia.contract.application.CancelContractUseCase;
import com.caiocodes.vigencia.contract.application.ContractChain;
import com.caiocodes.vigencia.contract.application.ContractCommands;
import com.caiocodes.vigencia.contract.application.ContractDetail;
import com.caiocodes.vigencia.contract.application.ContractSummary;
import com.caiocodes.vigencia.contract.application.CreateContractUseCase;
import com.caiocodes.vigencia.contract.application.ExpiringContracts;
import com.caiocodes.vigencia.contract.application.GetContractUseCase;
import com.caiocodes.vigencia.contract.application.RenewContractUseCase;
import com.caiocodes.vigencia.contract.application.SuspendContractUseCase;
import com.caiocodes.vigencia.contract.application.UpdateContractUseCase;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractStatus;
import com.caiocodes.vigencia.contract.web.dto.ContractDtos.CreateContractRequest;
import com.caiocodes.vigencia.contract.web.dto.ContractDtos.ReasonRequest;
import com.caiocodes.vigencia.contract.web.dto.ContractDtos.RenewContractRequest;
import com.caiocodes.vigencia.contract.web.dto.ContractDtos.UpdateContractRequest;
import com.caiocodes.vigencia.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contratos.
 *
 * <p>As ações que não são CRUD viram sub-recurso ({@code POST
 * /contracts/{id}/renew}), e não um campo {@code action} no corpo: assim cada
 * operação tem sua própria permissão, seu próprio log e seu próprio código de
 * status.
 *
 * <p>Nenhum endpoint aceita o gestor como filtro — o escopo de carteira sai de
 * quem está autenticado, dentro do caso de uso.
 */
@Tag(name = "Contratos")
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final CreateContractUseCase createContract;
    private final UpdateContractUseCase updateContract;
    private final ActivateContractUseCase activateContract;
    private final RenewContractUseCase renewContract;
    private final CancelContractUseCase cancelContract;
    private final SuspendContractUseCase suspendContract;
    private final GetContractUseCase getContract;

    @Operation(summary = "Cria um contrato em rascunho")
    @PostMapping
    @PreAuthorize("hasAuthority('contract:create')")
    public ResponseEntity<ContractDetail> create(@Valid @RequestBody CreateContractRequest body) {
        ContractDetail created = createContract.execute(body.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/contracts/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Lista e busca contratos (dentro da carteira de quem chama)")
    @GetMapping
    @PreAuthorize("hasAuthority('contract:read')")
    public PageResponse<ContractSummary> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endingUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                getContract.search(search, status, clientId, endingUntil, page, size));
    }

    /**
     * A tela que substitui a planilha.
     *
     * <p>Mapeada <b>antes</b> de {@code /{id}} não por acaso: com o Spring, a
     * rota literal vence a variável de caminho de qualquer forma, mas manter a
     * ordem no arquivo evita a discussão em code review.
     */
    @Operation(summary = "Contratos vencendo na janela, com resumo por faixa")
    @GetMapping("/expiring")
    @PreAuthorize("hasAuthority('contract:read')")
    public ExpiringContracts expiring(
            @RequestParam(defaultValue = "30") int daysAhead,
            @RequestParam(required = false) UUID clientId) {
        return getContract.expiring(daysAhead, clientId);
    }

    @Operation(summary = "Detalha um contrato")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:read')")
    public ContractDetail byId(@PathVariable UUID id) {
        return getContract.byId(ContractId.of(id));
    }

    @Operation(summary = "Cadeia completa de renovações a partir de qualquer elo")
    @GetMapping("/{id}/chain")
    @PreAuthorize("hasAuthority('contract:read')")
    public ContractChain chain(@PathVariable UUID id) {
        return getContract.chain(ContractId.of(id));
    }

    @Operation(summary = "Edita o contrato — só em rascunho")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:update')")
    public ContractDetail update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdateContractRequest body) {
        return updateContract.execute(body.toCommand(ContractId.of(id)));
    }

    @Operation(summary = "Ativa o contrato (DRAFT → ACTIVE)")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('contract:activate')")
    public ContractDetail activate(@PathVariable UUID id) {
        return activateContract.execute(ContractId.of(id));
    }

    /**
     * Renovação.
     *
     * <p>Responde <b>201</b> quando criou o sucessor e <b>200</b> quando a
     * mesma {@code Idempotency-Key} já tinha criado — a diferença importa para
     * o cliente HTTP saber se aquele contrato nasceu nesta chamada.
     */
    @Operation(summary = "Renova o contrato criando o sucessor (201) ou devolve o já criado (200)")
    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('contract:renew')")
    public ResponseEntity<ContractDetail> renew(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RenewContractRequest body) {

        RenewContractUseCase.RenewalResult result =
                renewContract.execute(body.toCommand(ContractId.of(id), idempotencyKey));

        if (result.repeated()) {
            return ResponseEntity.ok(result.contract());
        }
        return ResponseEntity.created(URI.create("/api/v1/contracts/" + result.contract().id()))
                .body(result.contract());
    }

    @Operation(summary = "Cancela o contrato — motivo obrigatório")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('contract:cancel')")
    public ContractDetail cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return cancelContract.execute(
                new ContractCommands.CancelContract(ContractId.of(id), body.reason()));
    }

    @Operation(summary = "Suspende o contrato (ACTIVE → SUSPENDED)")
    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('contract:update')")
    public ContractDetail suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return suspendContract.suspend(
                new ContractCommands.SuspendContract(ContractId.of(id), body.reason()));
    }

    @Operation(summary = "Retoma o contrato (SUSPENDED → ACTIVE)")
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('contract:update')")
    public ContractDetail resume(@PathVariable UUID id) {
        return suspendContract.resume(ContractId.of(id));
    }
}
