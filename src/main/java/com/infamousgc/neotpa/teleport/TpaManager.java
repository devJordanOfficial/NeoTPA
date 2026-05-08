package com.infamousgc.neotpa.teleport;

import com.infamousgc.neotpa.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all teleport request state: pending requests,
 * cooldowns, toggle preferences, and active warmups.
 */
public final class TpaManager {

    private static final TpaManager INSTANCE = new TpaManager();

    /** Outgoing requests keyed by sender UUID. One per sender; new replaces old. */
    private final Map<UUID, TpaRequest> outgoingRequests = new ConcurrentHashMap<>();

    /** Incoming requests keyed by target UUID. Multiple senders can request the same target. */
    private final Map<UUID, List<TpaRequest>> incomingRequests = new ConcurrentHashMap<>();

    /** Timestamps (millis) of when each sender's cooldown expires. */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /** Players who have toggled off incoming teleport requests. */
    private final Set<UUID> toggledOff = ConcurrentHashMap.newKeySet();

    /** Active warmups keyed by the UUID of the player who will be teleported. */
    private final Map<UUID, WarmupEntry> activeWarmups = new ConcurrentHashMap<>();

    private TpaManager() {}

    public static TpaManager getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new TPA request from sender to target.
     * If the sender already has an outgoing request, the old one is removed first.
     *
     * @param sender the requesting player
     * @param target the receiving player
     * @param type   TPA or TPA_HERE
     * @return the created request
     */
    public TpaRequest createRequest(ServerPlayer sender, ServerPlayer target, TpaRequest.RequestType type) {
        TpaRequest request = new TpaRequest(sender.getUUID(), target.getUUID(), type, System.currentTimeMillis());

        // Remove any existing outgoing request from this sender
        cancelOutgoing(sender.getUUID());

        outgoingRequests.put(sender.getUUID(), request);
        incomingRequests.computeIfAbsent(target.getUUID(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(request);

        return request;
    }

    /**
     * Removes a specific incoming request for the target from the given sender.
     *
     * @param targetUUID the target's UUID
     * @param senderUUID the sender's UUID
     * @return the removed request, or {@code null} if not found
     */
    public TpaRequest removeIncoming(UUID targetUUID, UUID senderUUID) {
        List<TpaRequest> requests = incomingRequests.get(targetUUID);
        if (requests == null) return null;

        TpaRequest removed = null;
        Iterator<TpaRequest> iterator = requests.iterator();
        while (iterator.hasNext()) {
            TpaRequest request = iterator.next();
            if (request.senderUUID().equals(senderUUID)) {
                removed = request;
                iterator.remove();
                break;
            }
        }

        if (requests.isEmpty()) incomingRequests.remove(targetUUID);
        if (removed != null) outgoingRequests.remove(senderUUID);

        return removed;
    }

    /**
     * Cancels the sender's outgoing request, cleaning up both maps.
     *
     * @param senderUUID the sender's UUID
     * @return the canceled request, or {@code null} if none existed
     */
    public TpaRequest cancelOutgoing(UUID senderUUID) {
        TpaRequest request = outgoingRequests.remove(senderUUID);
        if (request == null) return null;

        List<TpaRequest> targetIncoming = incomingRequests.get(request.targetUUID());
        if (targetIncoming != null) {
            targetIncoming.removeIf(r -> r.senderUUID().equals(senderUUID));
            if (targetIncoming.isEmpty()) {
                incomingRequests.remove(request.targetUUID());
            }
        }

        return request;
    }

    /**
     * Returns all non-expired incoming requests for a target.
     * Expired requests are pruned during this call.
     *
     * @param targetUUID the target's UUID
     * @return an unmodifiable list of pending requests, possibly empty
     */
    public List<TpaRequest> getIncomingRequests(UUID targetUUID) {
        List<TpaRequest> requests = incomingRequests.get(targetUUID);
        if (requests == null) return List.of();

        // Prune expired requests
        requests.removeIf(r -> {
            if (r.isExpired()) {
                outgoingRequests.remove(r.senderUUID());
                return true;
            }
            return false;
        });

        if (requests.isEmpty()) {
            incomingRequests.remove(targetUUID);
            return List.of();
        }

        return List.copyOf(requests);
    }

    /**
     * Returns the sender's current outgoing request, or {@code null} if none or expired.
     */
    public TpaRequest getOutgoing(UUID senderUUID) {
        TpaRequest request = outgoingRequests.get(senderUUID);
        if (request != null && request.isExpired()) {
            cancelOutgoing(senderUUID);
            return null;
        }
        return request;
    }

    // ---- Cooldowns ----

    /**
     * Starts a cooldown for the given player based on the configured duration.
     */
    public void startCooldown(UUID playerUUID) {
        int cooldownSeconds = Config.COOLDOWN_SECONDS.get();
        if (cooldownSeconds > 0) {
            cooldowns.put(playerUUID, System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
    }

    /**
     * Checks whether the given player is still on cooldown.
     */
    public boolean isOnCooldown(UUID playerUUID) {
        Long expiresAt = cooldowns.get(playerUUID);
        if (expiresAt == null) return  false;
        if (System.currentTimeMillis() >= expiresAt) {
            cooldowns.remove(playerUUID);
            return false;
        }
        return true;
    }

    /**
     * Returns the remaining cooldown time in seconds, or 0 if not on cooldown.
     */
    public int getRemainingCooldown(UUID playerUUID) {
        Long expiresAt = cooldowns.get(playerUUID);
        if (expiresAt == null) return 0;
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining > 0 ? (int) Math.ceil(remaining / 1000.0) : 0;
    }

    // ---- Toggle ----

    /**
     * Toggles whether a player accepts incoming teleport requests.
     *
     * @return {@code true} if the player is now accepting requests, {@code false} if now blocking
     */
    public boolean toggle(UUID playerUUID) {
        if (toggledOff.contains(playerUUID)) {
            toggledOff.remove(playerUUID);
            return true;
        } else {
            toggledOff.add(playerUUID);
            return false;
        }
    }

    /**
     * Checks whether a player is currently accepting teleport requests.
     */
    public boolean isAcceptingRequests(UUID playerUUID) {
        return !toggledOff.contains(playerUUID);
    }

    // ---- Warmups ----

    /**
     * Starts a warmup for the player who will be teleported.
     *
     * @param teleportingPlayer the player who will move
     * @param destination       the player to teleport to
     */
    public void startWarmup(ServerPlayer teleportingPlayer, ServerPlayer destination) {
        Vec3 startPos = teleportingPlayer.position();
        long teleportAt = System.currentTimeMillis() + Config.WARMUP_SECONDS.get() * 1000L;
        activeWarmups.put(teleportingPlayer.getUUID(),
                new WarmupEntry(teleportingPlayer.getUUID(), destination.getUUID(), startPos, teleportAt));
    }

    /**
     * Cancels an active warmup for the given player.
     *
     * @return {@code true} if a warmup was canceled
     */
    public boolean cancelWarmup(UUID playerUUID) {
        return activeWarmups.remove(playerUUID) != null;
    }

    /**
     * Returns all currently active warmups. Used by the tick handler.
     */
    public Map<UUID, WarmupEntry> getActiveWarmups() {
        return activeWarmups;
    }

    /**
     * Clears all state. Called on server shutdown to prevent leaks.
     */
    public void clearAll() {
        outgoingRequests.clear();
        incomingRequests.clear();
        cooldowns.clear();
        toggledOff.clear();
        activeWarmups.clear();
    }

    /**
     * Tracks an in-progress warmup before teleport.
     *
     * @param playerUUID      the player who will be teleported
     * @param destinationUUID the player to teleport to
     * @param startPos        position when warmup began (movement check)
     * @param teleportAt      system time in millis when teleport should fire
     */
    public record WarmupEntry(UUID playerUUID, UUID destinationUUID, Vec3 startPos, long teleportAt) {}
}
