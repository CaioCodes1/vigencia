package com.caiocodes.vigencia.reporting.web;

import com.caiocodes.vigencia.reporting.application.DashboardSummary;
import com.caiocodes.vigencia.reporting.application.DelinquentClient;
import com.caiocodes.vigencia.reporting.application.ExpiringBucket;
import com.caiocodes.vigencia.reporting.application.GetDashboardUseCase;
import com.caiocodes.vigencia.reporting.application.RenewalRate;
import com.caiocodes.vigencia.reporting.application.RevenuePoint;
import com.caiocodes.vigencia.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O painel.
 *
 * <p>Todos os endpoints exigem {@code dashboard:read} — o mínimo. Quem também
 * tem {@code dashboard:read_all} recebe a empresa inteira; quem não tem recebe
 * o mesmo JSON, com os números da própria carteira. <b>Não existe parâmetro de
 * carteira em rota nenhuma</b>, e é essa ausência que fecha o IDOR.
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final GetDashboardUseCase dashboard;

    @Operation(summary = "Resumo do painel — contratos, receita, renovação e inadimplência")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public DashboardSummary summary() {
        return dashboard.summary();
    }

    @Operation(summary = "Receita realizada x prevista, mês a mês")
    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public List<RevenuePoint> revenue(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboard.revenue(from, to);
    }

    @Operation(summary = "Taxa de renovação do período")
    @GetMapping("/renewal-rate")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public RenewalRate renewalRate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboard.renewalRate(from, to);
    }

    @Operation(summary = "Clientes inadimplentes, do maior saldo em atraso para o menor")
    @GetMapping("/delinquent-clients")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public PageResponse<DelinquentClient> delinquentClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(dashboard.delinquentClients(page, size));
    }

    @Operation(summary = "Contratos a vencer, por faixa de dias")
    @GetMapping("/expiring-timeline")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public List<ExpiringBucket> expiringTimeline(
            @RequestParam(defaultValue = "60") int daysAhead) {
        return dashboard.expiringTimeline(daysAhead);
    }
}
