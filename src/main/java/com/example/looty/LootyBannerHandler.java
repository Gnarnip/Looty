package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class LootyBannerHandler {

    public static void showSavedAlternateSpawns(ServerLevel level, BlockPos chestPos, ServerPlayer player) {
        // Placeholder for future visual indicator logic
        player.sendSystemMessage(Component.literal("§7(Visual markers would appear here)"));
    }

    public static void removeSavedAlternateSpawns(ServerLevel level, ServerPlayer player) {
        // Placeholder for future removal logic
        player.sendSystemMessage(Component.literal("§7(Visual markers removed)"));
    }
}
