package com.caiocodes.crbap.shared.domain.exception;

/**
 * Transição de estado que a máquina de estados não permite — renovar um
 * contrato cancelado, pagar uma cobrança já cancelada, e assim por diante.
 */
public class IllegalStateTransitionException extends DomainException {

    private final String currentState;
    private final String attemptedAction;

    public IllegalStateTransitionException(String currentState, String attemptedAction) {
        super("INVALID_STATE_TRANSITION",
                "Não é possível executar '" + attemptedAction
                        + "' com o estado atual '" + currentState + "'");
        this.currentState = currentState;
        this.attemptedAction = attemptedAction;
    }

    public String currentState() {
        return currentState;
    }

    public String attemptedAction() {
        return attemptedAction;
    }
}
