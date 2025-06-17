package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        BlockPos linked = getLinkedLootyChest(player);
        if (linked == null) {
            player.displayClientMessage(Component.literal("§cNo Looty chest is linked."), true);
            return;
        }

        // Resolve origin (in case linked to alternate)
        BlockPos origin = ChestDataHandler.getOriginFor(linked);
        if (origin == null) origin = linked;

        Map<BlockPos, List<BlockPos>> all = ChestDataHandler.loadAlternateSpawns();
        List<BlockPos> currentSpawns = all.getOrDefault(origin, new ArrayList<>());

        // If the selected pos is already in the list, it's a toggle (allowed)
        boolean alreadySelected = isSelected(player, pos);

        // Only check limit if it's a new addition
        if (!alreadySelected && currentSpawns.size() >= 9) {
            player.displayClientMessage(Component.literal("§cThis Looty chest already has 9 alternate spawn locations."), true);
            return;
        }

        // Toggle selection
        LinkedHashSet<BlockPos> set = selectedAlternateSpawns.computeIfAbsent(player, p -> new LinkedHashSet<>());
        if (set.contains(pos)) {
            set.remove(pos);
            player.displayClientMessage(Component.literal("§7Unselected position: " + pos.toShortString()), true);
        } else {
            set.add(pos);
            player.displayClientMessage(Component.literal("§eSelected position: " + pos.toShortString()), true);
        }
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
