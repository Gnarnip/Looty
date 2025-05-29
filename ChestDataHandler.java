package com.example.looty;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ChestDataHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final File CHEST_DATA_FILE = new File("config/despawned_chests.dat");
    private static final File BLOCK_TYPE_FILE = new File("config/original_block_types.dat");

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
