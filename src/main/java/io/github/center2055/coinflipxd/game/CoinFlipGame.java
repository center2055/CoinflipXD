package io.github.center2055.coinflipxd.game;

import java.util.Optional;
import java.util.UUID;

/** One coinflip. State changes happen inside {@link BetBook}, which is synchronized. */
public final class CoinFlipGame {

    private final UUID id;
    private final UUID creator;
    private final GameType type;
    private final UUID target;
    private final double amount;
    private final long createdAt;
    private final long expiresAt;

    private volatile GameState state = GameState.PENDING;
    private volatile UUID acceptor;

    public CoinFlipGame(UUID id, UUID creator, GameType type, UUID target, double amount, long createdAt, long expiresAt) {
        this.id = id;
        this.creator = creator;
        this.type = type;
        this.target = target;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public UUID creator() {
        return creator;
    }

    public GameType type() {
        return type;
    }

    public Optional<UUID> target() {
        return Optional.ofNullable(target);
    }

    public double amount() {
        return amount;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isExpired(long now) {
        return state == GameState.PENDING && now >= expiresAt;
    }

    public GameState state() {
        return state;
    }

    void state(GameState state) {
        this.state = state;
    }

    public Optional<UUID> acceptor() {
        return Optional.ofNullable(acceptor);
    }

    void acceptor(UUID acceptor) {
        this.acceptor = acceptor;
    }
}
