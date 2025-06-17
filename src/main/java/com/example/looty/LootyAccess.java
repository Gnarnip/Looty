package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class LootyAccess {
    private static final Map<ServerPlayer, BlockPos> linkedLootyChest = new HashMap<>();
    private static final Map<ServerPlayer, LinkedHashSet<BlockPos>> selectedAlternateSpawns = new HashMap<>();

    public static BlockPos getLinkedLootyChest(ServerPlayer player) {
        return linkedLootyChest.get(player);
    }

    public static void setLinkedLootyChest(ServerPlayer player, BlockPos pos) {
        if (pos == null) {
            linkedLootyChest.remove(player);
            selectedAlternateSpawns.remove(player);
        } else {
            linkedLootyChest.put(player, pos);
        }
    }

    public static List<BlockPos> getSelectedSpawns(ServerPlayer player) {
        return new ArrayList<>(selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>()));
    }

    public static void addSelectedSpawn(ServerPlayer player, BlockPos pos) {
        selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>()).add(pos);
    }

    public static void removeSelectedSpawn(ServerPlayer player, BlockPos pos) {
        selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>()).remove(pos);
    }

    public static boolean isSelected(ServerPlayer player, BlockPos pos) {
        return selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>()).contains(pos);
    }

    public static void clearSelectedSpawns(ServerPlayer player) {
        selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>()).clear();
    }

    public static boolean isLinked(ServerPlayer player) {
        return linkedLootyChest.containsKey(player);
    }
}
