package com.example.looty;

import com.google.gson.*;
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
    private static final File ALT_SPAWN_FILE = new File("config/looty/alternate_spawns.json");

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

    // === Alternate Spawns (JSON) ===

    public static void saveAlternateSpawns(Map<BlockPos, List<BlockPos>> data) {
        JsonObject root = new JsonObject();

        for (Map.Entry<BlockPos, List<BlockPos>> entry : data.entrySet()) {
            JsonArray coordsArray = new JsonArray();
            for (BlockPos alt : entry.getValue()) {
                coordsArray.add(serializeBlockPos(alt));
            }
            root.add(serializeBlockPos(entry.getKey()), coordsArray);
        }

        try {
            File parent = ALT_SPAWN_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (FileWriter writer = new FileWriter(ALT_SPAWN_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save alternate_spawns.json", e);
        }
    }

    public static Map<BlockPos, List<BlockPos>> loadAlternateSpawns() {
        Map<BlockPos, List<BlockPos>> result = new HashMap<>();
        if (!ALT_SPAWN_FILE.exists()) return result;

        try (FileReader reader = new FileReader(ALT_SPAWN_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                BlockPos origin = deserializeBlockPos(entry.getKey());
                List<BlockPos> altList = new ArrayList<>();
                for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                    altList.add(deserializeBlockPos(elem.getAsString()));
                }
                result.put(origin, altList);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load alternate_spawns.json", e);
        }

        return result;
    }

    // === Utilities ===

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

    private static String serializeBlockPos(BlockPos pos) {
        return "[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]";
    }

    private static BlockPos deserializeBlockPos(String str) {
        str = str.replace("[", "").replace("]", "");
        String[] parts = str.split(",");
        if (parts.length != 3) return BlockPos.ZERO;

        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid alternate spawn BlockPos string: {}", str);
            return BlockPos.ZERO;
        }
    }
}
