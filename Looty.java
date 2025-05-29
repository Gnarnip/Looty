package com.example.looty;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import net.minecraftforge.event.level.BlockEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Mod(looty.MODID)
public class looty {

    public static final String MODID = "looty";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<ResourceLocation> lootyContainerBlocks = new HashSet<>();
    private final Set<BlockPos> lootyChestsCache = new HashSet<>();
    private final Map<BlockPos, Long> despawnTimers = new HashMap<>();
    private final Map<BlockPos, Long> despawnedChests = ChestDataHandler.loadDespawnedChests();
    private final Map<BlockPos, ResourceLocation> originalBlockTypes = ChestDataHandler.loadOriginalBlockTypes();
    private final Map<String, Integer> rarityDistances = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static final List<Long> RESPAWN_TIMES = Arrays.asList(
            15L * 60 * 20, 20L * 60 * 20, 25L * 60 * 20, 30L * 60 * 20,
            35L * 60 * 20, 40L * 60 * 20, 50L * 60 * 20, 60L * 60 * 20
    );

    public looty() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LootTableLoader.loadLootTables();
        loadDistanceConfig();
        loadContainerBlockConfig();
        LOGGER.info("Looty mod initialized.");
    }

    private void loadContainerBlockConfig() {
        File file = new File("config/looty_containers.json");
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                JsonArray array = new JsonArray();
                array.add("minecraft:chest");
                array.add("minecraft:barrel");
                array.add("minecraft:shulker_box");
                new GsonBuilder().setPrettyPrinting().create().toJson(array, writer);
            } catch (IOException e) {
                LOGGER.error("Failed to create looty_containers.json", e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement el : array) {
                ResourceLocation id = new ResourceLocation(el.getAsString());
                if (ForgeRegistries.BLOCKS.containsKey(id)) {
                    lootyContainerBlocks.add(id);
                } else {
                    LOGGER.warn("Unknown block in looty_containers.json: {}", id);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Error reading looty_containers.json", e);
        }
    }

    private void loadDistanceConfig() {
        File file = new File("config/distance_config.json");
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                JsonObject json = new JsonObject();
                json.addProperty("COMMON", 1000);
                json.addProperty("UNCOMMON", 3000);
                json.addProperty("RARE", 6000);
                json.addProperty("LEGENDARY", 10000);
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            } catch (IOException e) {
                LOGGER.error("Error creating distance_config.json", e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                rarityDistances.put(entry.getKey().toUpperCase(), entry.getValue().getAsInt());
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Error loading distance_config.json", e);
        }
    }

    @SubscribeEvent
    public void onRightClickChest(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (level.isClientSide()) return;

        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !lootyContainerBlocks.contains(blockId)) return;

        ServerLevel server = (ServerLevel) level;

        if (event.getEntity().getMainHandItem().is(Items.GOLDEN_HOE) && event.getEntity().hasPermissions(2)) {
            toggleAdminChest(pos, server, event);
            return;
        }

        if (Config.enableAllLootyChests || isAdminChest(pos, server)) {
            despawnTimers.put(pos, server.getGameTime() + 600);
        }
    }

    private void toggleAdminChest(BlockPos pos, ServerLevel level, PlayerInteractEvent event) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) return;

        CompoundTag tag = container.getPersistentData();
        boolean marked = tag.contains("LootyAdmin");
        if (marked) {
            tag.remove("LootyAdmin");
            lootyChestsCache.remove(pos);
            event.getEntity().sendSystemMessage(Component.literal("Container unmarked as Looty"));
        } else {
            tag.putBoolean("LootyAdmin", true);
            lootyChestsCache.add(pos);
            event.getEntity().sendSystemMessage(Component.literal("Container marked as Looty"));
        }
        container.setChanged();
    }

    private boolean isAdminChest(BlockPos pos, Level level) {
        BlockEntity entity = level.getBlockEntity(pos);
        return lootyChestsCache.contains(pos) ||
                (entity instanceof RandomizableContainerBlockEntity container &&
                        container.getPersistentData().getBoolean("LootyAdmin"));
    }
    private List<Long> getRespawnTimes() {
        List<? extends Long> configList = Config.customRespawnTimes;
        if (configList != null && !configList.isEmpty()) {
            List<Long> tickList = new ArrayList<>();
            for (Long seconds : configList) {
                tickList.add(seconds * 20); // convert to ticks
            }
            return tickList;
        }
        return RESPAWN_TIMES;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof RandomizableContainerBlockEntity container) {
            CompoundTag tag = container.getPersistentData();
            if (tag.getBoolean("LootyAdmin") && !event.getPlayer().hasPermissions(2)) {
                event.setCanceled(true); // Prevent griefing of Looty containers by non-ops
                event.getPlayer().sendSystemMessage(Component.literal(""));
            }
        }
    }


    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;

        long now = level.getGameTime();

        // === Despawn Queue ===
        Iterator<Map.Entry<BlockPos, Long>> despawnIter = despawnTimers.entrySet().iterator();
        while (despawnIter.hasNext()) {
            Map.Entry<BlockPos, Long> entry = despawnIter.next();
            BlockPos pos = entry.getKey();
            long targetTime = entry.getValue();

            // ✅ Only tick if the chunk is currently loaded
            if (now >= targetTime && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                despawnChest(pos, level);
                despawnIter.remove();
            }
        }

        // === Respawn Queue ===
        Iterator<Map.Entry<BlockPos, Long>> respawnIter = despawnedChests.entrySet().iterator();
        while (respawnIter.hasNext()) {
            Map.Entry<BlockPos, Long> entry = respawnIter.next();
            BlockPos pos = entry.getKey();
            long targetTime = entry.getValue();

            // ✅ Only tick if the chunk is currently loaded
            if (now >= targetTime && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                respawnChest(pos, level);
                respawnIter.remove();
            }
        }
    }


    private void despawnChest(BlockPos pos, ServerLevel level) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof RandomizableContainerBlockEntity container) {
            container.getPersistentData().remove("LootyAdmin");
        }

        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) {
            originalBlockTypes.put(pos, id);
            ChestDataHandler.saveOriginalBlockTypes(originalBlockTypes);
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        despawnedChests.put(pos, level.getGameTime() + getRandomRespawnTime());
        ChestDataHandler.saveDespawnedChests(despawnedChests);
    }

    private void respawnChest(BlockPos pos, ServerLevel level) {
        ResourceLocation id = originalBlockTypes.getOrDefault(pos, ForgeRegistries.BLOCKS.getKey(Blocks.CHEST));
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) return;

        level.setBlock(pos, block.defaultBlockState(), 3);
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) return;

        double distance = pos.distManhattan(level.getSharedSpawnPos());
        LootRarity rarity = getRarityBasedOnDistance(distance);
        List<ItemStack> loot = LootTableLoader.getRandomizedLoot(rarity.name());

        for (int i = 0; i < Math.min(loot.size(), container.getContainerSize()); i++) {
            container.setItem(i, loot.get(i));
        }

        container.getPersistentData().putBoolean("LootyAdmin", true);
        container.setChanged();
        level.sendBlockUpdated(pos, block.defaultBlockState(), block.defaultBlockState(), 3);
    }

    private long getRandomRespawnTime() {
        List<Long> times = getRespawnTimes();
        return times.get(RANDOM.nextInt(times.size()));
    }


    private LootRarity getRarityBasedOnDistance(double distance) {
        if (distance <= rarityDistances.getOrDefault("COMMON", 1000)) return LootRarity.COMMON;
        if (distance <= rarityDistances.getOrDefault("UNCOMMON", 3000)) return LootRarity.UNCOMMON;
        if (distance <= rarityDistances.getOrDefault("RARE", 6000)) return LootRarity.RARE;
        return LootRarity.LEGENDARY;
    }

    public enum LootRarity {
        COMMON, UNCOMMON, RARE, LEGENDARY
    }
}
