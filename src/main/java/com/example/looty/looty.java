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

        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (event.getEntity().isShiftKeyDown()
                && event.getEntity().getMainHandItem().is(Items.GOLDEN_HOE)
                && event.getEntity() instanceof ServerPlayer player
                && level instanceof ServerLevel serverLevel
                && player.hasPermissions(2)) {

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
            return; // important: don’t continue with Looty container checks. poop.
        }

        if (blockId == null || !lootyContainerBlocks.contains(blockId)) return;

        ServerLevel server = (ServerLevel) level;

        if (event.getEntity().getMainHandItem().is(Items.GOLDEN_HOE) && event.getEntity().hasPermissions(2)) {
            toggleAdminChest(pos, server, event);
            return;
        }

        if (Config.enableAllLootyChests || isAdminChest(pos, server)) {
            despawnTimers.put(pos, server.getGameTime() + 600);
            if (event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().is(Items.GOLDEN_HOE)) {
                if (event.getEntity() instanceof ServerPlayer player && level instanceof ServerLevel serverLevel) {
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

                    // Optional: Add particle effect here for feedback
                    serverLevel.sendParticles(ParticleTypes.GLOW, clickedPos.getX() + 0.5, clickedPos.getY() + 1.2, clickedPos.getZ() + 0.5, 5, 0.25, 0.25, 0.25, 0.01);

                    event.setCanceled(true);
                }
            }

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

        // Check if the broken block is an admin sign used to mark a loot group
        if (level.getBlockState(pos).getBlock() == Blocks.OAK_SIGN) {
            String group = LootyMarkerHandler.getGroupNameForSign(pos);
            if (group != null) {
                // Remove the group from configuration
                GroupLootConfig.removeGroup(group);
                // Remove the sign marker
                LootyMarkerHandler.removeAdminSign((ServerLevel) level, group);
                event.getPlayer().sendSystemMessage(Component.literal("§cGroup '" + group + "' removed by breaking its sign."));
            }
        }

        // Then check for Looty container griefing prevention
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


    private void respawnChest(BlockPos pos, ServerLevel level) {
        ResourceLocation id = originalBlockTypes.getOrDefault(pos, ForgeRegistries.BLOCKS.getKey(Blocks.CHEST));
        Block block = ForgeRegistries.BLOCKS.getValue(id);

        if (block == null) return;

        level.setBlock(pos, block.defaultBlockState(), 3);
        if (Config.enableRespawnAnimations) {
            LootRarity rarity = getRarityBasedOnDistance(level, pos);
            if (Config.enableFancyRespawnAnimations) {
                spawnFancyFirework(level, pos, rarity);
            } else {
                spawnSimpleRespawnParticles(level, pos, rarity);
            }
        }

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) return;

        List<ItemStack> loot;

        GroupLootConfig.GroupData group = GroupLootConfig.getMatchingGroup(pos);

        if (group != null) {
            if (group.lootTable != null) {
                loot = LootTableLoader.getRandomizedLoot(group.lootTable);
                LOGGER.info("Respawning chest at {} matched group with custom loot table '{}'", pos, group.lootTable);
            } else if (group.rarity != null) {
                loot = LootTableLoader.getRandomizedLoot(group.rarity);
                LOGGER.info("Respawning chest at {} matched group with rarity '{}'", pos, group.rarity);
            } else {
                LootRarity fallback = getRarityBasedOnDistance(level, pos);
                loot = LootTableLoader.getRandomizedLoot(fallback.name());
                LOGGER.info("Group matched but had no loot info. Using fallback '{}'", fallback.name());
            }
        } else {
            LootRarity fallback = getRarityBasedOnDistance(level, pos);
            loot = LootTableLoader.getRandomizedLoot(fallback.name());
            LOGGER.info("No group matched. Using fallback distance rarity '{}'", fallback.name());
        }



        // Finalize loot assignment
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

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId == null || !lootyContainerBlocks.contains(blockId)) return;

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (!(blockEntity instanceof RandomizableContainerBlockEntity)) return;

        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        BlockPos linked = LootyAccess.getLinkedLootyChest(serverPlayer);

        if (linked != null && linked.equals(pos)) {
            LootyAccess.setLinkedLootyChest(serverPlayer, null);
            LootyBannerHandler.removeSavedAlternateSpawns(serverLevel, serverPlayer);
            serverPlayer.displayClientMessage(Component.literal("§cUnlinked from Looty chest."), true);
        } else {
            LootyAccess.setLinkedLootyChest(serverPlayer, pos);
            LootyBannerHandler.showSavedAlternateSpawns(serverLevel, pos, serverPlayer);
            serverPlayer.displayClientMessage(Component.literal("§6Linked to Looty chest at " + pos.toShortString()), true);
        }

        event.setCanceled(true); // Prevent breaking the block
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
