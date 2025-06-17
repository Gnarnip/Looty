package com.example.looty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import com.example.looty.GroupLootConfig.GroupData;

@Mod.EventBusSubscriber
public class LootyCommand {

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("looty")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            LootTableLoader.loadLootTables();
                            GroupLootConfig.loadGroupConfig();
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§aLooty config reloaded!"), true);
                            return 1;
                        }))

                .then(Commands.literal("toggleall")
                        .executes(ctx -> {
                            boolean newState = !Config.enableAllLootyChests;
                            Config.enableAllLootyChests = newState;
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§eAll chests now " + (newState ? "§aenabled" : "§cdisabled") + " §efor Looty."), true);
                            return 1;
                        }))

                .then(Commands.literal("setgroup")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    String groupName = StringArgumentType.getString(ctx, "name");

                                    GroupLootConfig.setGroupCenter(groupName, pos);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§bGroup §e" + groupName + " §bcenter set to §7" + pos), true);
                                    return 1;
                                })))

                .then(Commands.literal("addgroup")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("rarity_or_loottable", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                                    String value = StringArgumentType.getString(ctx, "rarity_or_loottable");
                                                    BlockPos signPos = BlockPos.containing(ctx.getSource().getPosition()).below();

                                                    boolean isLootTable = value.contains(":");
                                                    String rarity = isLootTable ? null : value.toUpperCase(Locale.ROOT);
                                                    String lootTable = isLootTable ? value : null;

                                                    if (!isLootTable && !LootTableLoader.hasRarity(rarity)) {
                                                        ctx.getSource().sendFailure(Component.literal("§cUnknown rarity: §f" + rarity));
                                                        return 0;
                                                    }

                                                    GroupLootConfig.addGroupEntry(name, signPos, radius, isLootTable ? lootTable : rarity);
                                                    String combinedArgs = name + " " + radius + " " + value;
                                                    LootyMarkerHandler.placeAdminSign(ctx.getSource().getLevel(), signPos, combinedArgs);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("§aGroup §e'" + name + "' §aadded at §7" + signPos +
                                                                    " §awith radius §e" + radius + " §aand " +
                                                                    (isLootTable ? "loot table §b" + lootTable : "rarity §b" + rarity)), true);
                                                    return 1;
                                                })))))


                .then(Commands.literal("del")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean removed = GroupLootConfig.removeGroup(name);
                                    if (removed) {
                                        LootyMarkerHandler.removeAdminSign(ctx.getSource().getLevel(), name);
                                        ctx.getSource().sendSuccess(() -> Component.literal("§cGroup '" + name + "' removed."), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("§cGroup '" + name + "' not found."));
                                    }
                                    return 1;
                                })))

                .then(Commands.literal("listgroups")
                        .executes(ctx -> {
                            var groups = GroupLootConfig.getGroupNames();
                            if (groups.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§7No groups are currently defined."), false);
                                return 1;
                            }

                            ctx.getSource().sendSuccess(() -> Component.literal("§b--- Looty Groups ---"), false);
                            for (String group : groups) {
                                GroupData data = GroupLootConfig.getGroup(group);
                                String info = String.format("§e%s §7@ %s §8| Radius: §f%d §8| %s: §a%s",
                                        group,
                                        data.center != null ? data.center : "unknown",
                                        data.radius,
                                        data.lootTable != null ? "LootTable" : "Rarity",
                                        data.lootTable != null ? data.lootTable : data.rarity
                                );
                                ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                            }

                            return 1;
                        }))

                .then(Commands.literal("unlink")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("§cThis command must be run by a player."));
                                return 0;
                            }

                            BlockPos linked = LootyAccess.getLinkedLootyChest(player);
                            if (linked == null) {
                                player.sendSystemMessage(Component.literal("§cYou are not linked to any Looty chest."));
                                return 1;
                            }

                            LootyAccess.setLinkedLootyChest(player, null);
                            LootyBannerHandler.removeSavedAlternateSpawns(player.serverLevel(), player);
                            player.sendSystemMessage(Component.literal("§cUnlinked from Looty chest."));
                            return 1;
                        }))

                .then(Commands.literal("addspawns")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("§cThis command must be run by a player."));
                                return 0;
                            }

                            BlockPos chest = LootyAccess.getLinkedLootyChest(player);
                            if (chest == null) {
                                player.sendSystemMessage(Component.literal("§cYou are not currently linked to a Looty chest."));
                                return 1;
                            }

                            List<BlockPos> selected = new ArrayList<>(LootyAccess.getSelectedSpawns(player));
                            if (selected.isEmpty()) {
                                player.sendSystemMessage(Component.literal("§cYou have no selected alternate spawn coordinates."));
                                return 1;
                            }

                            // Save to alternate spawn cache
                            Map<BlockPos, List<BlockPos>> batch = ChestDataHandler.loadAlternateSpawns();
                            batch.put(chest, new ArrayList<>(selected));
                            ChestDataHandler.saveAlternateSpawns(batch);

                            LootyAccess.clearSelectedSpawns(player);
                            player.sendSystemMessage(Component.literal("§aSaved §e" + selected.size() + " §aalternate spawn(s) to Looty chest §7" + chest));
                            return 1;
                        }))
        );
    }
}
