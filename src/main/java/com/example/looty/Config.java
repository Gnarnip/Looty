package com.example.looty;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "looty", bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // === Config Values ===
    public static final ForgeConfigSpec.BooleanValue ENABLE_ALL_LOOTY_CHESTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_RESPAWN_ANIMATIONS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DESPAWN_ANIMATIONS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FANCY_RESPAWN_ANIMATIONS;
    public static final ForgeConfigSpec.DoubleValue FIREWORK_LAUNCH_Y_OFFSET;
    public static final ForgeConfigSpec.IntValue FIREWORK_FLIGHT_DURATION;

    public static final ForgeConfigSpec.ConfigValue<List<? extends Long>> CUSTOM_RESPAWN_TIMES;

    public static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK;
    public static final ForgeConfigSpec.IntValue MAGIC_NUMBER;
    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION;

    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_RARITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DUPLICATE_ITEMS;

    // === Runtime values ===
    public static boolean enableAllLootyChests;
    public static Set<Item> items;
    public static boolean enableRespawnAnimations;
    public static boolean enableDespawnAnimations;
    public static boolean enableFancyRespawnAnimations;
    public static double fireworkLaunchYOffset;
    public static int fireworkFlightDuration;
    public static List<? extends Long> customRespawnTimes;
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static String fallbackRarity;
    public static boolean enableDuplicateItems;

    static {
        BUILDER.push("Looty Settings");

        // 🧰 General Settings
        BUILDER.push("General");
        ENABLE_ALL_LOOTY_CHESTS = BUILDER.comment("🧰 Whether all chests should be treated as Looty automatically.")
                .define("enableAllLootyChests", false);

        ITEM_STRINGS = BUILDER.comment("🧱 List of item IDs to log when loading Looty.")
                .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);
        BUILDER.pop();

        // 🎯 Loot Behavior
        BUILDER.push("Loot Behavior");
        FALLBACK_RARITY = BUILDER.comment("🎯 Used when no rarity (distance/group) is detected. Default: COMMON")
                .define("fallbackRarity", "COMMON");
        ENABLE_DUPLICATE_ITEMS = BUILDER.comment("📦 If true, allow duplicate items to spawn in the same chest.")
                .define("enableDuplicateItems", false);
        BUILDER.pop();

        // 🎇 Animation Settings
        BUILDER.push("Animations");
        ENABLE_RESPAWN_ANIMATIONS = BUILDER.comment("🎇 Enable chest respawn animations (fireworks or particles).")
                .define("enableRespawnAnimations", true);
        ENABLE_DESPAWN_ANIMATIONS = BUILDER.comment("💨 Enable smoke particles when chests despawn.")
                .define("enableDespawnAnimations", true);
        ENABLE_FANCY_RESPAWN_ANIMATIONS = BUILDER.comment("✨ If true, use fireworks for respawns. If false, use colored particles.")
                .define("enableFancyRespawnAnimations", true);
        FIREWORK_LAUNCH_Y_OFFSET = BUILDER.comment("🎆 Vertical Y offset for firework launch. Default 0.1 spawns below chest.")
                .defineInRange("fireworkLaunchYOffset", 0.1, -2.0, 2.0);
        FIREWORK_FLIGHT_DURATION = BUILDER.comment("🚀 Firework duration. 0 = instant explode, 1 = short, up to 3 = long flight.")
                .defineInRange("fireworkFlightDuration", 0, 0, 3);
        BUILDER.pop();

        // ⏱ Respawn Timers
        BUILDER.push("Timers");
        CUSTOM_RESPAWN_TIMES = BUILDER.comment("⏱ Custom loot container respawn times (in seconds). Leave empty to use defaults.")
                .defineListAllowEmpty("customRespawnTimes", List.of(), obj -> obj instanceof Long && (Long) obj > 0);
        BUILDER.pop();

        // 🐞 Debug/Test Settings
        BUILDER.push("Debug");
        LOG_DIRT_BLOCK = BUILDER.comment("🐞 Log dirt block info on setup.")
                .define("logDirtBlock", true);
        MAGIC_NUMBER = BUILDER.comment("🎲 A fun configurable magic number.")
                .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);
        MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("📢 What to say about the magic number in logs.")
                .define("magicNumberIntroduction", "The magic number is... ");
        BUILDER.pop();

        BUILDER.pop(); // Looty Settings

        SPEC = BUILDER.build();
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("✅ Looty Config Registered"));
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        enableAllLootyChests = ENABLE_ALL_LOOTY_CHESTS.get();
        items = ITEM_STRINGS.get().stream()
                .map(name -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(name)))
                .collect(Collectors.toSet());

        enableRespawnAnimations = ENABLE_RESPAWN_ANIMATIONS.get();
        enableDespawnAnimations = ENABLE_DESPAWN_ANIMATIONS.get();
        enableFancyRespawnAnimations = ENABLE_FANCY_RESPAWN_ANIMATIONS.get();
        fireworkLaunchYOffset = FIREWORK_LAUNCH_Y_OFFSET.get();
        fireworkFlightDuration = FIREWORK_FLIGHT_DURATION.get();

        customRespawnTimes = CUSTOM_RESPAWN_TIMES.get();
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        fallbackRarity = FALLBACK_RARITY.get().toUpperCase(Locale.ROOT);
        enableDuplicateItems = ENABLE_DUPLICATE_ITEMS.get();

        LOGGER.info("✅ Looty config loaded!");
    }
}
