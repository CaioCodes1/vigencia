package com.caiocodes.crbap.shared.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Base das raízes de agregado.
 *
 * <p>O agregado acumula os eventos do que aconteceu com ele e alguém os "puxa"
 * depois — normalmente o caso de uso, para gravar na outbox dentro da mesma
 * transação. Assim o domínio produz o fato sem saber que existe mensageria.
 *
 * @param <I> tipo do identificador do agregado
 */
public abstract class AggregateRoot<I> {

    private final List<DomainEvent> events = new ArrayList<>();

    public abstract I id();

    protected void register(DomainEvent event) {
        events.add(event);
    }

    /** Devolve os eventos e limpa a lista — chamar duas vezes não republica. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pending = List.copyOf(events);
        events.clear();
        return pending;
    }

    public boolean hasPendingEvents() {
        return !events.isEmpty();
    }
}
