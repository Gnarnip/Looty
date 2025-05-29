package com.example.looty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class LootyCommand {

    // Remove the cached variable entirely

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("looty")
                .requires(source -> source.hasPermission(2)) // OP level 2+
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            LootTableLoader.loadLootTables(); // reload loot
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
        );
    }
}
