package com.infamousgc.neotpa;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Config {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue WARMUP_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("teleport");

        WARMUP_SECONDS = builder
                .comment("Time in seconds after accepting a request before the teleport occurs.",
                        "The teleporting playe rmust stand still during this period.")
                .defineInRange("warmupSeconds", 3, 0, Integer.MAX_VALUE);

        COOLDOWN_SECONDS = builder
                .comment("Time in seconds before a player can send another teleport request.")
                .defineInRange("cooldownSeconds", 60, 0, Integer.MAX_VALUE);

        REQUEST_TIMEOUT_SECONDS = builder
                .comment("Time in seconds before a pending teleport request automatically expires.")
                .defineInRange("requestTimeoutSeconds", 60, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.push("permissions");

        PERMISSION_LEVEL = builder
                .comment("Vanilla permission level required to use /tpa and /tpahere.",
                        "0 = all players | 1 = moderators | 2 = operators | 3 = admins | 4 = owner.")
                .defineInRange("permissionLevel", 0, 0, 4);

        builder.pop();

        SPEC = builder.build();
    }

    private Config() {}
}
