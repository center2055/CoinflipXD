package com.yourorg.coinflip.game;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class CoinFlipGame {

    private final UUID id;
    private final UUID creator;
    private final GameType type;
    private final UUID target;
    private final double amount;
    private final long createdAt;
    private final long expiresAt;
    private final Lock lock = new ReentrantLock(true);

    private volatile GameState state;
    private volatile UUID acceptor;
    private volatile Instant resolvedAt;

    public CoinFlipGame(UUID id, UUID creator, GameType type, UUID target, double amount, long createdAt, long expiresAt) {
        this.id = id;
        this.creator = creator;
        this.type = type;
        this.target = target;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.state = GameState.PENDING;
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

    public void state(GameState state) {
        this.state = state;
    }

    public Optional<UUID> acceptor() {
        return Optional.ofNullable(acceptor);
    }

    public void acceptor(UUID acceptor) {
        this.acceptor = acceptor;
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public void resolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Lock lock() {
        return lock;
    }
}

