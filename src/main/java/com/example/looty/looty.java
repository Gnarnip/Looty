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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.server.level.ServerPlayer;

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
    private static final Map<UUID, Long> lastClickTick = new HashMap<>();
    private static final List<Long> RESPAWN_TIMES = Arrays.asList(
            5L * 20,   // 5 seconds
            10L * 20,  // 10 seconds
            15L * 20,  // 15 seconds
            20L * 20,  // 20 seconds
            25L * 20,  // 25 seconds
            30L * 20   // 30 seconds
    );


    public looty() {
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LootTableLoader.loadLootTables();
        GroupLootConfig.loadGroupConfig();
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

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        UUID playerId = player.getUUID();
        long gameTick = serverLevel.getGameTime();
        if (player.isShiftKeyDown() &&
                player.getMainHandItem().is(Items.GOLDEN_HOE) &&
                player.hasPermissions(2)) {

            // Prevent double toggling in same tick
            if (lastClickTick.getOrDefault(playerId, -1L) == gameTick) {
                return;
            }
            lastClickTick.put(playerId, gameTick);

            BlockPos clickedPos = event.getPos();

            if (!LootyAccess.isLinked(player)) {
                player.sendSystemMessage(Component.literal("§cLink to a Looty chest first (Shift + Left Click)."));
                return;
            }

            if (LootyAccess.isSelected(player, clickedPos)) {
                LootyAccess.removeSelectedSpawn(player, clickedPos);
                player.sendSystemMessage(Component.literal("§eUnselected block at " + clickedPos.toShortString()));
            } else {
                if (LootyAccess.getSelectedSpawns(player).size() >= 9) {
                    player.sendSystemMessage(Component.literal("§cYou can only select up to 9 alternate spawn positions."));
                    return;
                }
                LootyAccess.addSelectedSpawn(player, clickedPos);
                player.sendSystemMessage(Component.literal("§aSelected block at " + clickedPos.toShortString()));
            }

            serverLevel.sendParticles(ParticleTypes.GLOW,
                    clickedPos.getX() + 0.5,
                    clickedPos.getY() + 1.2,
                    clickedPos.getZ() + 0.5,
                    5, 0.25, 0.25, 0.25, 0.01);

            event.setCanceled(true);
            return;
        }

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        if (blockId == null || !lootyContainerBlocks.contains(blockId)) return;

        if (player.getMainHandItem().is(Items.GOLDEN_HOE) && player.hasPermissions(2)) {
            toggleAdminChest(pos, serverLevel, event);
            return;
        }

        if (Config.enableAllLootyChests || isAdminChest(pos, serverLevel)) {
            despawnTimers.put(pos, serverLevel.getGameTime() + 600);
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
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();

        // Handle group sign deletion
        if (level.getBlockState(pos).getBlock() == Blocks.OAK_SIGN) {
            String group = LootyMarkerHandler.getGroupNameForSign(pos);
            if (group != null) {
                GroupLootConfig.removeGroup(group);
                LootyMarkerHandler.removeAdminSign(level, group);
                event.getPlayer().sendSystemMessage(Component.literal("§cGroup '" + group + "' removed by breaking its sign."));
            }
        }

        // Handle wool block above alternate spawn
        if (level.getBlockState(pos).getBlock() == Blocks.RED_WOOL) {
            ChestDataHandler.findMatchingAlternateSpawn(pos).ifPresent(matchingAlt -> {
                BlockPos origin = ChestDataHandler.getOriginFor(matchingAlt);
                if (origin != null) {
                    Map<BlockPos, List<BlockPos>> map = ChestDataHandler.loadAlternateSpawns();
                    List<BlockPos> list = map.getOrDefault(origin, new ArrayList<>());
                    list.remove(matchingAlt);

                    if (list.isEmpty()) {
                        map.remove(origin);
                    } else {
                        map.put(origin, list);
                    }

                    ChestDataHandler.saveAlternateSpawns(map);
                    event.getPlayer().sendSystemMessage(Component.literal("§cRemoved alternate spawn at " + matchingAlt.toShortString() + " by breaking wool."));
                }
            });
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


    private void respawnChest(BlockPos currentPos, ServerLevel level) {
        // Always resolve the origin, even if we're starting from an alternate location
        BlockPos origin = ChestDataHandler.getOriginFor(currentPos);
        if (origin == null) origin = currentPos;

        ResourceLocation id = originalBlockTypes.getOrDefault(origin, ForgeRegistries.BLOCKS.getKey(Blocks.CHEST));
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) return;

        // Load all alternate positions from origin
        Map<BlockPos, List<BlockPos>> allAlternates = ChestDataHandler.loadAlternateSpawns();
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(origin);
        candidates.addAll(allAlternates.getOrDefault(origin, Collections.emptyList()));

        // Choose a new random position to respawn
        BlockPos finalPos = candidates.get(RANDOM.nextInt(candidates.size()));
        if (!finalPos.equals(currentPos)) {
            LOGGER.info("Chest at {} is respawning at {}", currentPos, finalPos);
            level.removeBlock(currentPos, false); // Clean up previous
        }

        // Place chest at new location
        level.setBlock(finalPos, block.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS);

        // Animation particles
        if (Config.enableRespawnAnimations) {
            LootRarity rarity = getRarityBasedOnDistance(level, finalPos);
            if (Config.enableFancyRespawnAnimations) {
                spawnFancyFirework(level, finalPos, rarity);
            } else {
                spawnSimpleRespawnParticles(level, finalPos, rarity);
            }
        }

        // Insert loot
        BlockEntity entity = level.getBlockEntity(finalPos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) return;

        List<ItemStack> loot;
        GroupLootConfig.GroupData group = GroupLootConfig.getMatchingGroup(finalPos);
        if (group != null) {
            if (group.lootTable != null) {
                loot = LootTableLoader.getRandomizedLoot(group.lootTable);
                LOGGER.info("Matched group with custom table '{}'", group.lootTable);
            } else if (group.rarity != null) {
                loot = LootTableLoader.getRandomizedLoot(group.rarity);
                LOGGER.info("Matched group with rarity '{}'", group.rarity);
            } else {
                LootRarity fallback = getRarityBasedOnDistance(level, finalPos);
                loot = LootTableLoader.getRandomizedLoot(fallback.name());
                LOGGER.info("Group matched, fallback to '{}'", fallback.name());
            }
        } else {
            LootRarity fallback = getRarityBasedOnDistance(level, finalPos);
            loot = LootTableLoader.getRandomizedLoot(fallback.name());
            LOGGER.info("No group, fallback to '{}'", fallback.name());
        }

        // Fill chest with loot
        for (int i = 0; i < Math.min(loot.size(), container.getContainerSize()); i++) {
            container.setItem(i, loot.get(i));
        }

        container.getPersistentData().putBoolean("LootyAdmin", true);
        container.setChanged();
        level.sendBlockUpdated(finalPos, block.defaultBlockState(), block.defaultBlockState(), 3);
    }

    private long getRandomRespawnTime() {
        List<Long> times = getRespawnTimes();
        return times.get(RANDOM.nextInt(times.size()));
    }

    private void despawnChest(BlockPos pos, ServerLevel level) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);

        if (blockId != null) {
            originalBlockTypes.put(pos, blockId); // Save original block type
            ChestDataHandler.saveOriginalBlockTypes(originalBlockTypes);
        }
        if (Config.enableDespawnAnimations) spawnDespawnSmoke(level, pos);
        level.removeBlock(pos, false); // Remove the chest

        long respawnTime = level.getGameTime() + getRandomRespawnTime();
        despawnedChests.put(pos, respawnTime);
        ChestDataHandler.saveDespawnedChests(despawnedChests);

        LOGGER.info("Despawned chest at {} — will respawn in {} ticks", pos, respawnTime - level.getGameTime());
    }

    private LootRarity getRarityBasedOnDistance(ServerLevel level, BlockPos pos) {
        for (String group : GroupLootConfig.getGroupNames()) {
            String rarityMatch = GroupLootConfig.getRarityForPosition(group, pos);
            if (rarityMatch != null) {
                try {
                    return LootRarity.valueOf(rarityMatch.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[Looty] Invalid rarity '{}' in group_config.json for group '{}'", rarityMatch, group);
                }
            }
        }

        // Fallback to distance-based logic
        int distance = pos.distManhattan(level.getSharedSpawnPos());
        if (distance <= rarityDistances.getOrDefault("COMMON", 1000)) return LootRarity.COMMON;
        if (distance <= rarityDistances.getOrDefault("UNCOMMON", 3000)) return LootRarity.UNCOMMON;
        if (distance <= rarityDistances.getOrDefault("RARE", 6000)) return LootRarity.RARE;
        return LootRarity.LEGENDARY;
    }

    public enum LootRarity {
        COMMON, UNCOMMON, RARE, LEGENDARY

    }

    private void spawnFancyFirework(ServerLevel level, BlockPos pos, LootRarity rarity) {
        int color = switch (rarity) {
            case COMMON -> 0xFFFFFF;
            case UNCOMMON -> 0x00FF00;
            case RARE -> 0xFF0000;
            case LEGENDARY -> 0xFFD700;
        };

        CompoundTag explosionTag = new CompoundTag();
        explosionTag.putIntArray("Colors", new int[]{color});
        explosionTag.putByte("Type", (byte) 1); // Star-shaped
        explosionTag.putBoolean("Flicker", true);
        explosionTag.putBoolean("Trail", true);

        CompoundTag fireworksTag = new CompoundTag();
        fireworksTag.put("Explosions", new ListTag() {{
            add(explosionTag);
        }});
        fireworksTag.putByte("Flight", (byte) Config.fireworkFlightDuration);  // uses config

        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        firework.addTagElement("Fireworks", fireworksTag);

        double yOffset = Config.fireworkLaunchYOffset;
        FireworkRocketEntity rocket = new FireworkRocketEntity(
                level,
                pos.getX() + 0.5,
                pos.getY() + yOffset,
                pos.getZ() + 0.5,
                firework
        );

        level.addFreshEntity(rocket);
    }

    private void spawnSimpleRespawnParticles(ServerLevel level, BlockPos pos, LootRarity rarity) {
        ParticleOptions particle = switch (rarity) {
            case COMMON -> ParticleTypes.GLOW;
            case UNCOMMON -> ParticleTypes.HAPPY_VILLAGER;
            case RARE -> ParticleTypes.CRIT;
            case LEGENDARY -> ParticleTypes.FLAME;
        };

        for (int i = 0; i < 20; i++) {
            double dx = RANDOM.nextGaussian() * 0.2;
            double dy = RANDOM.nextDouble() * 0.5;
            double dz = RANDOM.nextGaussian() * 0.2;
            level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 1, dx, dy, dz, 0.1);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (!event.getEntity().isShiftKeyDown()) return;

        ItemStack stack = event.getEntity().getMainHandItem();
        if (!stack.is(Items.GOLDEN_HOE)) return;
        if (!event.getEntity().hasPermissions(2)) return;

        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId == null || !lootyContainerBlocks.contains(blockId)) return;

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity)) return;

        Map<BlockPos, List<BlockPos>> all = ChestDataHandler.loadAlternateSpawns();

        // Case 1: Clicked is the original chest
        if (all.containsKey(pos)) {
            LootyAccess.setLinkedLootyChest(serverPlayer, pos);
            LootyBannerHandler.placeSavedAlternateSpawns(serverLevel, serverPlayer, all.get(pos));
            serverPlayer.displayClientMessage(Component.literal("§aLinked to original Looty chest at §7" + pos.toShortString()), true);
            event.setCanceled(true);
            return;
        }

        // Case 2: Clicked is one of the alternate spawn locations
        for (Map.Entry<BlockPos, List<BlockPos>> entry : all.entrySet()) {
            if (entry.getValue().contains(pos)) {
                BlockPos origin = entry.getKey();
                LootyAccess.setLinkedLootyChest(serverPlayer, origin);
                LootyBannerHandler.placeSavedAlternateSpawns(serverLevel, serverPlayer, entry.getValue());
                serverPlayer.displayClientMessage(Component.literal("§aLinked to alternate Looty chest at §7" + pos.toShortString() +
                        " §7(Origin: " + origin.toShortString() + ")"), true);
                event.setCanceled(true);
                return;
            }
        }

        // Case 3: Already linked, toggle off
        BlockPos linked = LootyAccess.getLinkedLootyChest(serverPlayer);
        if (linked != null) {
            Map<BlockPos, List<BlockPos>> saved = ChestDataHandler.loadAlternateSpawns();
            List<BlockPos> spawns = saved.getOrDefault(linked, List.of());
            LootyBannerHandler.removeSavedAlternateSpawns(serverPlayer.serverLevel(), spawns); // ✅ correct
        }


        // Case 4: Standalone looty chest (no alternates) — allow linking
        LootyAccess.setLinkedLootyChest(serverPlayer, pos);
        Map<BlockPos, List<BlockPos>> saved = ChestDataHandler.loadAlternateSpawns();
        List<BlockPos> spawns = saved.getOrDefault(linked, List.of());
        LootyBannerHandler.removeSavedAlternateSpawns(serverLevel, spawns);
        serverPlayer.displayClientMessage(Component.literal("§aLinked to solo Looty chest at §7" + pos.toShortString()), true);
        event.setCanceled(true);
    }

    private void spawnDespawnSmoke(ServerLevel level, BlockPos pos) {
        if (!Config.enableDespawnAnimations) return;
        for (int i = 0; i < 30; i++) {
            double dx = RANDOM.nextGaussian() * 0.2;
            double dy = RANDOM.nextDouble() * 0.5;
            double dz = RANDOM.nextGaussian() * 0.2;
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 1, dx, dy, dz, 0.01);

        }
    }
}
