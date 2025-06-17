package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;



import java.util.HashMap;
import java.util.Map;


    @Mod.EventBusSubscriber
    public class LootyMarkerHandler {

        private static final Map<String, BlockPos> signPositions = new HashMap<>();


        public static void placeAdminSign(ServerLevel level, BlockPos basePos, String groupCommandArgs) {
            BlockPos signPos = basePos.above();

            // Use OAK_SIGN (not OAK_WALL_SIGN or old STANDING variants)
            BlockState signState = Blocks.OAK_SIGN.defaultBlockState()
                    .setValue(StandingSignBlock.ROTATION, 0);
            level.setBlock(signPos, signState, 3);

            // Place block entity and set lines

            if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
                String[] args = groupCommandArgs.split(" ", 3);

                Component line1 = Component.literal("Group: " + (args.length > 0 ? args[0] : ""));
                Component line2 = Component.literal("Radius: " + (args.length > 1 ? args[1] : ""));
                Component line3 = Component.literal((args.length > 2 && args[2].contains(":")) ? "LootTable:" : "Rarity:");
                Component line4 = Component.literal(args.length > 2 ? args[2] : "");

                // Create a new SignText for the front side
                SignText frontText = signEntity.getFrontText()
                        .setMessage(0, line1)
                        .setMessage(1, line2)
                        .setMessage(2, line3)
                        .setMessage(3, line4);

                // Apply to sign (false = not the back side)
                signEntity.setText(frontText, false);
                signEntity.setChanged();

                // Force update
                level.sendBlockUpdated(signPos, signState, signState, 3);
                for (ServerPlayer player : level.players()) {
                    if (player.hasPermissions(2)) {
                        player.connection.send(new ClientboundBlockUpdatePacket(level, signPos));
                        player.connection.send(signEntity.getUpdatePacket());
                    }
                }

                signPositions.put(args[0], signPos);
            }

        }



        public static void removeAdminSign(ServerLevel level, String groupName) {
            BlockPos pos = signPositions.get(groupName);
            if (pos != null && level.getBlockState(pos).getBlock() == Blocks.OAK_SIGN) {
                level.removeBlock(pos, false);
            }
            signPositions.remove(groupName);
            GroupLootConfig.removeGroup(groupName);
        }

        public static String getGroupNameForSign(BlockPos pos) {
            for (Map.Entry<String, BlockPos> entry : signPositions.entrySet()) {
                if (entry.getValue().equals(pos)) return entry.getKey();
            }
            return null;
        }

        public static void sendSignUpdateToAdmins(ServerLevel level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;

            for (ServerPlayer player : level.players()) {
                if (player.hasPermissions(2)) {
                    player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
                    player.connection.send(sign.getUpdatePacket());
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockPos pos = event.getPos();
            if (level.getBlockState(pos).getBlock() == Blocks.OAK_SIGN) {
                String group = getGroupNameForSign(pos);
                if (group != null) {
                    signPositions.remove(group);
                    GroupLootConfig.removeGroup(group);
                    level.players().forEach(p -> {
                        if (p.hasPermissions(2)) {
                            p.displayClientMessage(Component.literal("§c[LOOTY] Group sign '" + group + "' removed (broken)."), false);
                        }
                    });
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            ServerLevel level = player.serverLevel();

            // Resend updates for already tracked signs
            for (BlockPos pos : signPositions.values()) {
                sendSignUpdateToAdmins(level, pos);
            }

            // Scan chunks near the player to find group signs
            int chunkRadius = 5;
            ChunkPos center = player.chunkPosition();

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
                    if (chunk == null) continue; // Skip unloaded chunks

                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockPos scanPos = new BlockPos(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                                if (level.getBlockState(scanPos).getBlock() == Blocks.OAK_SIGN) {
                                    if (level.getBlockEntity(scanPos) instanceof SignBlockEntity sign) {
                                        Component line1 = sign.getFrontText().getMessage(0, false);
                                        if (line1.getString().startsWith("Group: ")) {
                                            String group = line1.getString().substring(7).trim();
                                            signPositions.put(group, scanPos);
                                            sendSignUpdateToAdmins(level, scanPos);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            @SubscribeEvent
        public static void onChunkWatch(ChunkWatchEvent.Watch event) {
            ServerPlayer player = event.getPlayer();
            if (!player.hasPermissions(2)) return;

            ServerLevel level = player.serverLevel();
            ChunkPos watchedChunk = event.getPos();

            for (BlockPos pos : signPositions.values()) {
                ChunkPos signChunk = new ChunkPos(pos);
                if (signChunk.equals(watchedChunk)) {
                    sendSignUpdateToAdmins(level, pos);
                }
            }
        }
    }
