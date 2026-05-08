package com.infamousgc.neotpa;

import com.infamousgc.neotpa.command.TpaCommands;
import com.infamousgc.neotpa.teleport.TpaManager;
import com.infamousgc.neotpa.util.MessageHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod(NeoTPA.MODID)
public class NeoTPA {

    public static final String MODID = "neotpa";

    public NeoTPA(IEventBus modEventBus, ModContainer modContainer) {
        // Register server config
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        // Register event listeners on the game event bus
        IEventBus gameEventBus = NeoForge.EVENT_BUS;
        gameEventBus.addListener(this::onRegisterCommands);
        gameEventBus.addListener(this::onServerTick);
        gameEventBus.addListener(this::onServerStopped);
    }

    /**
     * Registers all TPA commands when the server loads or reloads commands.
     */
    private void onRegisterCommands(RegisterCommandsEvent event) {
        TpaCommands.register(event.getDispatcher());
    }

    /**
     * Processes active warmups each server tick.
     * Checks if the teleporting player has moved or if the warmup duration has elapsed.
     */
    private void onServerTick(ServerTickEvent.Post event) {
        TpaManager manager = TpaManager.getInstance();
        Map<UUID, TpaManager.WarmupEntry> warmups = manager.getActiveWarmups();

        if (warmups.isEmpty()) return;

        Iterator<Map.Entry<UUID, TpaManager.WarmupEntry>> iterator = warmups.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TpaManager.WarmupEntry> entry = iterator.next();
            TpaManager.WarmupEntry warmup = entry.getValue();

            ServerPlayer teleporting = event.getServer().getPlayerList().getPlayer(warmup.playerUUID());
            ServerPlayer destination = event.getServer().getPlayerList().getPlayer(warmup.destinationUUID());

            // Either player went offline - cancel
            if (teleporting == null || destination == null) {
                iterator.remove();
                if (teleporting != null) {
                    teleporting.sendSystemMessage(MessageHelper.playerWentOffline());
                }
                continue;
            }

            // Check if the teleporting player moved (>1 block from start position)
            Vec3 currentPos = teleporting.position(); //
            if (currentPos.distanceToSqr(warmup.startPos()) > 1.0E-6) {
                iterator.remove();
                teleporting.sendSystemMessage(MessageHelper.warmupCanceledMoved());
                continue;
            }

            // Check if warmup duration has elapsed
            if (System.currentTimeMillis() >= warmup.teleportAt()) {
                iterator.remove();
                TpaCommands.performTeleport(teleporting, destination);
            }
        }
    }

    /**
     * Clears all TPA state when the server shuts down to prevent stale references.
     */
    private void onServerStopped(ServerStoppedEvent event) {
        TpaManager.getInstance().clearAll();
    }
}
