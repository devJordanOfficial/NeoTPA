package com.infamousgc.neotpa.util;

import com.infamousgc.neotpa.teleport.TpaRequest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Builds styled chat components for all NeoTPA messages.
 * Provides a consistent visual style with clickable interactive elements.
 */
public class MessageHelper {

    private static final String PREFIX = "✦ ";

    private MessageHelper() {}

    /**
     * Builds the two-line request notification sent to the target player.
     * <p>
     * Line 1: ✦ PlayerName is requesting to teleport to you.
     * Line 2:   [Accept] [Deny]
     *
     * @param senderName the name of the player who sent the request
     * @param type       the request type, used to adjust the message wording
     * @return the composed message component
     */
    public static Component buildRequestNotification(String senderName, TpaRequest.RequestType type) {
        String action = type == TpaRequest.RequestType.TPA
                ? " is requesting to teleprot to you."
                : " is requesting you to teleport to them.";

        MutableComponent line1 = Component.literal(PREFIX)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(senderName)
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(action)
                        .withStyle(ChatFormatting.GRAY));

        MutableComponent acceptButton = Component.literal("[Accept]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + senderName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to accept teleport request").withStyle(ChatFormatting.GRAY))));

        MutableComponent denyButton = Component.literal("[Deny]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + senderName))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to deny teleport request").withStyle(ChatFormatting.GRAY))));

        MutableComponent line2 = Component.literal("  ")
                .append(acceptButton)
                .append(Component.literal(" "))
                .append(denyButton);

        return line1.append(Component.literal("\n")).append(line2);
    }

    /**
     * Builds a confirmation message sent to the sender after their request is dispatched.
     */
    public static Component requestSent(String targetName, TpaRequest.RequestType type) {
        String action = type == TpaRequest.RequestType.TPA
                ? "Teleport request sent to "
                : "Request sent to teleport ";

        String suffix = type == TpaRequest.RequestType.TPA ? "." : " to you.";

        return prefix()
                .append(Component.literal(action).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(targetName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(suffix).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notifies the sender that the target has accepted the request.
     */
    public static Component requestAccepted(String otherPlayerName) {
        return prefix(ChatFormatting.GREEN)
                .append(Component.literal(otherPlayerName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" accepted your teleport request.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notifies the sender that the target has denied the request.
     */
    public static Component requestDenied(String otherPlayerName) {
        return prefix(ChatFormatting.RED)
                .append(Component.literal(otherPlayerName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" denied your teleport request.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notified the sender that their request was successfully canceled.
     */
    public static Component cancelConfirmed(String targetName) {
        return prefix()
                .append(Component.literal("You canceled your request to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(targetName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notifies the target that the sender canceled their request.
     */
    public static Component requestCanceled(String senderName) {
        return prefix()
                .append(Component.literal(senderName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" canceled their teleport request.").withStyle(ChatFormatting.GRAY));
    }

    public static Component playerWentOffline() {
        return prefix(ChatFormatting.RED)
                .append(Component.literal("Teleport canceled - the requesting player went offline.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notifies a player that a request has expired.
     */
    public static Component requestExpired(String otherPlayerName) {
        return prefix()
                .append(Component.literal("Teleport request with ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(otherPlayerName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" has expired.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the sender that the target is not accepting teleport requests.
     */
    public static Component toggledOff(String targetName) {
        return prefix(ChatFormatting.RED)
                .append(Component.literal(targetName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" is not accepting teleport requests.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Confirms to a player that they toggled teleport requests on or off.
     */
    public static Component toggleStatus(boolean nowAccepting) {
        String status = nowAccepting ? "now accepting" : "no longer accepting";
        ChatFormatting color = nowAccepting ? ChatFormatting.GREEN : ChatFormatting.RED;

        return prefix(color)
                .append(Component.literal("You are " + status + " teleport requests.").withStyle(color));
    }

    /**
     * Notifies the teleporting player that warmup has started.
     */
    public static Component warmupStarted(int seconds) {
        return prefix()
                .append(Component.literal("Teleporting in ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(seconds + "s").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(". Don't move!").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Notifies the player that their teleport was canceled because they moved.
     */
    public static Component warmupCanceledMoved() {
        return prefix(ChatFormatting.RED)
                .append(Component.literal("Teleport canceled — you moved!").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the sender they are on cooldown.
     */
    public static Component onCooldown(int secondsRemaining) {
        return prefix(ChatFormatting.RED)
                .append(Component.literal("You must wait ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(secondsRemaining + "s").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" before sending another request.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the player they have no pending requests.
     */
    public static Component noPendingRequests() {
        return prefix()
                .append(Component.literal("You have no pending teleport requests.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the player they have no outgoing request to cancel.
     */
    public static Component noOutgoingRequest() {
        return prefix()
                .append(Component.literal("You have no outgoing teleport request.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the sender they cannot send a request to themselves.
     */
    public static Component cannotRequestSelf() {
        return prefix(ChatFormatting.RED)
                .append(Component.literal("Oops! You cannot send a teleport request to yourself.").withStyle(ChatFormatting.GRAY));
    }

    public static Component multipleRequests(List<TpaRequest> requests, MinecraftServer server) {
        MutableComponent message = prefix()
                .append(Component.literal("You have multiple pending requests. Click one or specify a player:")
                        .withStyle(ChatFormatting.GRAY));

        for (TpaRequest request :requests) {
            ServerPlayer sender = server.getPlayerList().getPlayer(request.senderUUID());
            if (sender == null) continue;

            String name = sender.getGameProfile().getName();
            String typeLabel = request.type() == TpaRequest.RequestType.TPA ? "wants to teleport to you" : "wants you to teleport to them";

            MutableComponent entry = Component.literal("\n  ")
                    .append(Component.literal(name)
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tpaccept " + name))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Click to accept " + name + "'s request").withStyle(ChatFormatting.GRAY)))))
                    .append(Component.literal(" — " + typeLabel).withStyle(ChatFormatting.DARK_GRAY));

            message.append(entry);
        }

        return message;
    }

    /**
     * Tells the player that the specified sender has no pending request to them.
     */
    public static Component noRequestFrom(String senderName) {
        return prefix(ChatFormatting.RED)
                .append(Component.literal("No pending request from ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(senderName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Tells the sender they already have a pending outgoing request.
     * Informs them the old request was replaced.
     */
    public static Component replacedOutgoing(String oldTargetName) {
        return prefix()
                .append(Component.literal("Your previous request to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(oldTargetName).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" was canceled.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Creates the aqua prefix component used by all messages.
     */
    private static MutableComponent prefix() {
        return Component.literal(PREFIX).withStyle(ChatFormatting.AQUA);
    }
    private static MutableComponent prefix(ChatFormatting color) {
        return Component.literal(PREFIX).withStyle(color);
    }
}
