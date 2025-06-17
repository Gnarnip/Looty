package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public class ChestDataHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final File CHEST_DATA_FILE = new File("config/despawned_chests.dat");
    private static final File BLOCK_TYPE_FILE = new File("config/original_block_types.dat");
    private static final File CONFIG_DIR = new File("config/looty");
    private static final File ORIGIN_MAPPING_FILE = new File(CONFIG_DIR, "origin_mappings.dat");

    // === Despawned Chests ===

    public static void saveDespawnedChests(Map<BlockPos, Long> data) {
        Map<String, Long> serialized = new HashMap<>();
        for (Map.Entry<BlockPos, Long> entry : data.entrySet()) {
            serialized.put(serializePos(entry.getKey()), entry.getValue());
        }
        writeToFile(CHEST_DATA_FILE, serialized);
    }

    @SuppressWarnings("unchecked")
    public static Map<BlockPos, Long> loadDespawnedChests() {
        Map<BlockPos, Long> map = new HashMap<>();
        if (!CHEST_DATA_FILE.exists()) return map;

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(CHEST_DATA_FILE))) {
            Map<String, Long> serialized = (Map<String, Long>) in.readObject();
            for (Map.Entry<String, Long> entry : serialized.entrySet()) {
                map.put(deserializePos(entry.getKey()), entry.getValue());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load despawned chest data", e);
        }

        return map;
    }

    // === Original Block Types ===

    public static void saveOriginalBlockTypes(Map<BlockPos, ResourceLocation> data) {
        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<BlockPos, ResourceLocation> entry : data.entrySet()) {
            serialized.put(serializePos(entry.getKey()), entry.getValue().toString());
        }
        writeToFile(BLOCK_TYPE_FILE, serialized);
    }

    @SuppressWarnings("unchecked")
    public static Map<BlockPos, ResourceLocation> loadOriginalBlockTypes() {
        Map<BlockPos, ResourceLocation> map = new HashMap<>();
        if (!BLOCK_TYPE_FILE.exists()) return map;

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(BLOCK_TYPE_FILE))) {
            Map<String, String> serialized = (Map<String, String>) in.readObject();
            for (Map.Entry<String, String> entry : serialized.entrySet()) {
                map.put(deserializePos(entry.getKey()), new ResourceLocation(entry.getValue()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load original block type data", e);
        }

        return map;
    }

    // === Alternate Spawns ===

    private static void writeBlockPos(DataOutputStream out, BlockPos pos) throws IOException {
        out.writeInt(pos.getX());
        out.writeInt(pos.getY());
        out.writeInt(pos.getZ());
    }

    private static BlockPos readBlockPos(DataInputStream in) throws IOException {
        int x = in.readInt();
        int y = in.readInt();
        int z = in.readInt();
        return new BlockPos(x, y, z);
    }

    public static void saveAlternateSpawns(Map<BlockPos, List<BlockPos>> map) {
        File file = new File(CONFIG_DIR, "alternate_spawns.dat");

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(map.size());
            for (Map.Entry<BlockPos, List<BlockPos>> entry : map.entrySet()) {
                writeBlockPos(out, entry.getKey());
                List<BlockPos> spawns = entry.getValue();
                out.writeInt(spawns.size());
                for (BlockPos pos : spawns) {
                    writeBlockPos(out, pos);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save alternate_spawns.dat", e);
        }

        // 🟩 Refresh origin links for all alternates
        refreshOriginMappings();
    }

    public static Map<BlockPos, List<BlockPos>> loadAlternateSpawns() {
        File file = new File(CONFIG_DIR, "alternate_spawns.dat");
        Map<BlockPos, List<BlockPos>> map = new HashMap<>();

        if (file.exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    BlockPos chest = readBlockPos(in);
                    int listSize = in.readInt();
                    List<BlockPos> spawns = new ArrayList<>();
                    for (int j = 0; j < listSize; j++) {
                        spawns.add(readBlockPos(in));
                    }
                    map.put(chest, spawns);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load alternate_spawns.dat", e);
            }
        }

        return map;
    }

    public static Optional<BlockPos> findMatchingAlternateSpawn(BlockPos woolBlockPos) {
        BlockPos actualSpawn = woolBlockPos.below(); // wool marker is 1 above the spawn
        Map<BlockPos, List<BlockPos>> altMap = loadAlternateSpawns(); // now always current

        for (Map.Entry<BlockPos, List<BlockPos>> entry : altMap.entrySet()) {
            for (BlockPos alt : entry.getValue()) {
                if (alt.equals(actualSpawn)) {
                    return Optional.of(alt);
                }
            }
        }
        return Optional.empty();
    }

    // === Origin Mapping ===

    @SuppressWarnings("unchecked")
    public static Map<BlockPos, BlockPos> loadOriginMappings() {
        Map<BlockPos, BlockPos> map = new HashMap<>();
        if (!ORIGIN_MAPPING_FILE.exists()) return map;

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(ORIGIN_MAPPING_FILE))) {
            Map<String, String> serialized = (Map<String, String>) in.readObject();
            for (Map.Entry<String, String> entry : serialized.entrySet()) {
                BlockPos key = deserializePos(entry.getKey());
                BlockPos value = deserializePos(entry.getValue());
                map.put(key, value);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load origin mappings", e);
        }

        return map;
    }

    public static void saveOriginMappings(Map<BlockPos, BlockPos> map) {
        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> entry : map.entrySet()) {
            serialized.put(serializePos(entry.getKey()), serializePos(entry.getValue()));
        }
        writeToFile(ORIGIN_MAPPING_FILE, serialized);
    }

    public static BlockPos getOriginFor(BlockPos pos) {
        Map<BlockPos, BlockPos> originMap = loadOriginMappings();
        return originMap.get(pos);
    }

    // === Utilities ===
    public static void refreshOriginMappings() {
        Map<BlockPos, List<BlockPos>> altSpawns = loadAlternateSpawns();
        Map<BlockPos, BlockPos> current = loadOriginMappings();

        for (Map.Entry<BlockPos, List<BlockPos>> entry : altSpawns.entrySet()) {
            BlockPos origin = entry.getKey();
            for (BlockPos alt : entry.getValue()) {
                current.put(alt, origin);  // map each alternate back to the origin
            }
            current.put(origin, origin); // make sure origin maps to itself
        }

        saveOriginMappings(current);
    }

    private static void writeToFile(File file, Object data) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                LOGGER.error("Could not create directory: {}", parent.getAbsolutePath());
                return;
            }

            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                out.writeObject(data);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write to file: {}", file.getName(), e);
        }
    }

    private static String serializePos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos deserializePos(String str) {
        String[] parts = str.split(",");
        if (parts.length != 3) return BlockPos.ZERO;

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid BlockPos string: {}", str);
            return BlockPos.ZERO;
        }
    }
}
