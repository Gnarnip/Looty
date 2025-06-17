package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class LootyBannerHandler {

    private static final Set<BlockPos> ghostMarkers = new HashSet<>();

    // === CLIENTSIDE MARKER PLACEMENT (3 ARG VERSION) ===
    public static void placeSavedAlternateSpawns(ServerLevel level, ServerPlayer player, List<BlockPos> spawns) {
        ghostMarkers.clear();

        for (BlockPos pos : spawns) {
            BlockPos markerPos = pos.above();
            ghostMarkers.add(markerPos);
            BlockState fakeWool = Blocks.RED_WOOL.defaultBlockState();
            player.connection.send(new ClientboundBlockUpdatePacket(markerPos, fakeWool));
        }

        player.sendSystemMessage(Component.literal("§7Placed ghost markers for saved alternate spawns."));
    }

    // === CLIENTSIDE MARKER PLACEMENT (CONVENIENCE 2 ARG VERSION) ===
    public static void placeSavedAlternateSpawns(ServerPlayer player, List<BlockPos> spawns) {
        placeSavedAlternateSpawns(player.serverLevel(), player, spawns);
    }

    // === REMOVE PHYSICAL BLOCK WOOL FROM WORLD ===
    public static void removeSavedAlternateSpawns(ServerLevel level, List<BlockPos> spawns) {
        for (BlockPos pos : spawns) {
            BlockPos markerPos = pos.above();
            level.setBlockAndUpdate(markerPos, Blocks.AIR.defaultBlockState());
        }
    }

    // === REMOVE CLIENTSIDE MARKERS ===
    public static void clearGhostMarkers(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // 1. Remove wool from all saved alternate spawns (even if not in ghostMarkers)
        Map<BlockPos, List<BlockPos>> all = ChestDataHandler.loadAlternateSpawns();
        for (List<BlockPos> spawnList : all.values()) {
            for (BlockPos alt : spawnList) {
                BlockPos markerPos = alt.above();
                BlockState realState = level.getBlockState(markerPos);
                player.connection.send(new ClientboundBlockUpdatePacket(markerPos, realState));
            }
        }

        // 2. Clear any leftover ghostMarkers (visual only)
        for (BlockPos ghost : ghostMarkers) {
            BlockState realState = level.getBlockState(ghost);
            player.connection.send(new ClientboundBlockUpdatePacket(ghost, realState));
        }

        ghostMarkers.clear();
    }


    // === DELETE ALT SPAWN ON LEFT CLICK + REMOVE GHOST WOOL ===
    @SubscribeEvent
    public static void onLeftClickMarker(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) return;

        BlockPos clicked = event.getPos();
        if (!ghostMarkers.contains(clicked)) return;

        BlockPos supposedSpawn = clicked.below();
        Map<BlockPos, List<BlockPos>> all = ChestDataHandler.loadAlternateSpawns();
        boolean removed = false;

        for (Map.Entry<BlockPos, List<BlockPos>> entry : all.entrySet()) {
            List<BlockPos> updated = new ArrayList<>(entry.getValue());
            if (updated.removeIf(spawn -> spawn.equals(supposedSpawn))) {
                all.put(entry.getKey(), updated);
                ChestDataHandler.saveAlternateSpawns(all);
                player.sendSystemMessage(Component.literal("§cDeleted alternate spawn at §7" + supposedSpawn.toShortString()));
                removed = true;
                break;
            }
        }

        // Remove ghost marker visually
        ServerLevel level = player.serverLevel();
        BlockState realState = level.getBlockState(clicked);
        player.connection.send(new ClientboundBlockUpdatePacket(clicked, realState));
        ghostMarkers.remove(clicked);

        if (!removed) {
            player.sendSystemMessage(Component.literal("§7No alternate spawn matched marker at " + supposedSpawn.toShortString()));
        }

        event.setCanceled(true); // Prevent default block breaking but still visually update
    }
}
