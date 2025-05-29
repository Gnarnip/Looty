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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "looty", bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    // === Config Spec Builder ===
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // === Config Values ===
    public static final ForgeConfigSpec.BooleanValue ENABLE_ALL_LOOTY_CHESTS;
    public static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK;
    public static final ForgeConfigSpec.IntValue MAGIC_NUMBER;
    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Long>> CUSTOM_RESPAWN_TIMES;

    // === Loaded Runtime Values ===
    public static boolean enableAllLootyChests;
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static List<? extends Long> customRespawnTimes;

    static {
        BUILDER.push("Looty Settings");

        ENABLE_ALL_LOOTY_CHESTS = BUILDER.comment("Whether all chests should be treated as Looty without needing to be marked manually.")
                .define("enableAllLootyChests", false);

        LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup")
                .define("logDirtBlock", true);

        MAGIC_NUMBER = BUILDER.comment("A magic number")
                .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

        MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number")
                .define("magicNumberIntroduction", "The magic number is... ");

        ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.")
                .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

        CUSTOM_RESPAWN_TIMES = BUILDER.comment("Optional list of loot container respawn times (in seconds). Leave empty to use Looty's built-in randomized defaults.")
                .defineListAllowEmpty("customRespawnTimes", List.of(), obj -> obj instanceof Long && (Long) obj > 0);

        BUILDER.pop();
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
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();
        customRespawnTimes = CUSTOM_RESPAWN_TIMES.get();

        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        LOGGER.info("✅ Looty config loaded!");
    }
}
