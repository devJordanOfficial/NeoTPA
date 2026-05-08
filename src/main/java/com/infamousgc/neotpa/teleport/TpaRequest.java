package com.infamousgc.neotpa.teleport;

import com.infamousgc.neotpa.Config;

import java.util.UUID;

/**
 * Represents a pending teleport request between two players.
 *
 * @param senderUUID the player who initiated the request
 * @param targetUUID the player who received the request
 * @param type       whether the sender wants to teleport to the target, or the target to the sender
 * @param createdAt  system time in milliseconds when the request was created
 */
public record TpaRequest(UUID senderUUID, UUID targetUUID, RequestType type, long createdAt) {

    /**
     * The type of teleport request.
     */
    public enum RequestType {
        TPA,
        TPA_HERE
    }

    /**
     * Checks whether this request has exceeded the configured timeout.
     *
     * @return {@code true} if the request has expired
     */
    public boolean isExpired() {
        int timeoutSeconds = Config.REQUEST_TIMEOUT_SECONDS.get();
        return System.currentTimeMillis() - createdAt > timeoutSeconds * 1000L;
    }
}
