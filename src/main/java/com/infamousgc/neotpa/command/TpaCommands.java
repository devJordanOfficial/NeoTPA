package com.infamousgc.neotpa.command;

import com.infamousgc.neotpa.Config;
import com.infamousgc.neotpa.teleport.TpaManager;
import com.infamousgc.neotpa.teleport.TpaRequest;
import com.infamousgc.neotpa.util.MessageHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Registers and handles all NeoTPA commands via Brigadier.
 */
public class TpaCommands {

    private TpaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /tpa <player>
        dispatcher.register(Commands.literal("tpa")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> sendRequest(ctx, TpaRequest.RequestType.TPA))));

        // /tpahere <player>
        dispatcher.register(Commands.literal("tpahere")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> sendRequest(ctx, TpaRequest.RequestType.TPA_HERE))));

        // /tpaccept [player]
        dispatcher.register(Commands.literal("tpaccept")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                        .executes(ctx -> handleAcceptDeny(ctx, true, null))
                .then(Commands.argument("sender", EntityArgument.player())
                        .executes(ctx -> handleAcceptDeny(ctx, true, EntityArgument.getPlayer(ctx, "sender")))));

        // /tpdeny [player]
        dispatcher.register(Commands.literal("tpdeny")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                .executes(ctx -> handleAcceptDeny(ctx, false, null))
                .then(Commands.argument("sender", EntityArgument.player())
                        .executes(ctx -> handleAcceptDeny(ctx, false, EntityArgument.getPlayer(ctx, "sender")))));

        // /tpcancel
        dispatcher.register(Commands.literal("tpcancel")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                .executes(TpaCommands::handleCancel));

        // /tptoggle
        dispatcher.register(Commands.literal("tptoggle")
                .requires(src -> src.hasPermission(Config.PERMISSION_LEVEL.get()))
                .executes(TpaCommands::handleToggle));
    }

    /**
     * Handles /tpa and /tpahere — sends a teleport request to the target.
     */
    private static int sendRequest(CommandContext<CommandSourceStack> ctx, TpaRequest.RequestType type) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        TpaManager manager = TpaManager.getInstance();

        // Can't request yourself
        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(MessageHelper.cannotRequestSelf());
            return 0;
        }

        // Check toggle
        if (!manager.isAcceptingRequests(target.getUUID())) {
            sender.sendSystemMessage(MessageHelper.toggledOff(target.getGameProfile().getName()));
            return 0;
        }

        // Check cooldown
        if (manager.isOnCooldown(sender.getUUID())) {
            sender.sendSystemMessage(MessageHelper.onCooldown(manager.getRemainingCooldown(sender.getUUID())));
            return 0;
        }

        // Check for existing outgoing - notify sender the old one was replaced
        TpaRequest existing = manager.getOutgoing(sender.getUUID());
        if (existing != null) {
            ServerPlayer oldTarget = ctx.getSource().getServer().getPlayerList().getPlayer(existing.targetUUID());
            if (oldTarget != null) {
                sender.sendSystemMessage(MessageHelper.replacedOutgoing(oldTarget.getGameProfile().getName()));
                oldTarget.sendSystemMessage(MessageHelper.requestCanceled(sender.getGameProfile().getName()));
            }
        }

        // Create request
        manager.createRequest(sender, target, type);

        // Notify both parties
        sender.sendSystemMessage(MessageHelper.requestSent(target.getGameProfile().getName(), type));
        target.sendSystemMessage(MessageHelper.buildRequestNotification(sender.getGameProfile().getName(), type));

        return 1;
    }

    /**
     * Handles /tpaccept and /tpdeny, with optional player argument.
     *
     * @param accept      {@code true} for accept, {@code false} for deny
     * @param specifiedSender the sender argument if provided, otherwise {@code null}
     */
    private static int handleAcceptDeny(CommandContext<CommandSourceStack> ctx, boolean accept, ServerPlayer specifiedSender) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        TpaManager manager = TpaManager.getInstance();

        List<TpaRequest> incoming = manager.getIncomingRequests(target.getUUID());

        if (incoming.isEmpty()) {
            target.sendSystemMessage(MessageHelper.noPendingRequests());
            return 0;
        }

        // Determine which request to act on
        TpaRequest request;
        if (specifiedSender != null) {
            //Player specified - find their request
            request = incoming.stream()
                    .filter(r -> r.senderUUID().equals(specifiedSender.getUUID()))
                    .findFirst()
                    .orElse(null);

            if (request == null) {
                target.sendSystemMessage(MessageHelper.noRequestFrom(specifiedSender.getGameProfile().getName()));
                return 0;
            }
        } else if (incoming.size() == 1) {
            // Only one request - act on iut
            request = incoming.getFirst();
        } else {
            // Multiple requests - prompt player to specify
            target.sendSystemMessage(MessageHelper.multipleRequests(incoming, ctx.getSource().getServer()));
            return 0;
        }

        // Resolve the sender player
        ServerPlayer sender = ctx.getSource().getServer().getPlayerList().getPlayer(request.senderUUID());

        // Remove the request from both maps
        manager.removeIncoming(target.getUUID(), request.senderUUID());

        if (accept) {
            // Start cooldown on the sender
            manager.startCooldown(request.senderUUID());

            // Determine who teleports where
            ServerPlayer teleportingPlayer;
            ServerPlayer destination;
            if (request.type() == TpaRequest.RequestType.TPA) {
                teleportingPlayer = sender;
                destination = target;
            } else {
                teleportingPlayer = target;
                destination = sender;
            }

            // Ensure both players are still online
            if (teleportingPlayer == null || destination == null) {
                target.sendSystemMessage(MessageHelper.playerWentOffline());
                return 0;
            }

            // Notify both
            sender.sendSystemMessage(MessageHelper.requestAccepted(target.getGameProfile().getName()));

            int warmup = Config.WARMUP_SECONDS.get();
            if (warmup > 0) {
                teleportingPlayer.sendSystemMessage(MessageHelper.warmupStarted(warmup));
                manager.startWarmup(teleportingPlayer, destination);
            } else {
                // Instant teleport
                performTeleport(teleportingPlayer, destination);
            }
        } else {
            // Denied
            if (sender != null) {
                sender.sendSystemMessage(MessageHelper.requestDenied(target.getGameProfile().getName()));
            }
        }

        return 1;
    }

    /**
     * Handles /tpacancel — cancels the sender's outgoing request.
     */
    private static int handleCancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        TpaManager manager = TpaManager.getInstance();

        TpaRequest request = manager.cancelOutgoing(sender.getUUID());
        if (request == null) {
            sender.sendSystemMessage(MessageHelper.noOutgoingRequest());
            return 0;
        }

        // Notify the target
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayer(request.targetUUID());
        if (target != null) {
            target.sendSystemMessage(MessageHelper.requestCanceled(sender.getGameProfile().getName()));
        }

        sender.sendSystemMessage(MessageHelper.cancelConfirmed(
                target != null ? target.getGameProfile().getName() : "Unknown"));

        return 1;
    }

    /**
     * Handles /tptoggle — toggles whether the player accepts incoming requests.
     */
    private static int handleToggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean nowAccepting = TpaManager.getInstance().toggle(player.getUUID());
        player.sendSystemMessage(MessageHelper.toggleStatus(nowAccepting));
        return 1;
    }

    /**
     * Teleports a player to the destination player's exact position and dimension.
     */
    public static void performTeleport(ServerPlayer teleporting, ServerPlayer destination) {
        teleporting.teleportTo(
                destination.serverLevel(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                teleporting.getYRot(),
                teleporting.getXRot()
        );
    }
}
