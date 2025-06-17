package com.example.looty;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class GroupLootConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final File GROUP_CONFIG_FILE = new File("config/group_config.json");
    private static final Map<String, GroupData> groupDataMap = new HashMap<>();

    public static void loadGroupConfig() {
        groupDataMap.clear();

        if (!GROUP_CONFIG_FILE.exists()) {
            try (FileWriter writer = new FileWriter(GROUP_CONFIG_FILE)) {
                JsonObject example = new JsonObject();
                JsonObject dungeon = new JsonObject();
                dungeon.add("center", posToJson(new BlockPos(100, 64, -200)));
                dungeon.addProperty("radius", 300);
                dungeon.addProperty("rarity", "RARE");
                example.add("dungeon", dungeon);
                new GsonBuilder().setPrettyPrinting().create().toJson(example, writer);
                LOGGER.info("Created default group_config.json");
            } catch (Exception e) {
                LOGGER.error("Failed to create group_config.json", e);
                return;
            }
        }

        try (FileReader reader = new FileReader(GROUP_CONFIG_FILE)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                LOGGER.error("group_config.json must be a JSON object.");
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String group = entry.getKey();
                JsonObject obj = entry.getValue().getAsJsonObject();

                BlockPos center = obj.has("center") ? jsonToPos(obj.get("center").getAsJsonArray()) : null;
                int radius = obj.has("radius") ? obj.get("radius").getAsInt() : -1;
                String rarity = obj.has("rarity") ? obj.get("rarity").getAsString().toUpperCase(Locale.ROOT) : null;
                String lootTable = null;
                if (obj.has("lootTable")) {
                    String lootStr = obj.get("lootTable").getAsString();
                    ResourceLocation parsedLootTable = ResourceLocation.tryParse(lootStr);
                    if (parsedLootTable != null) {
                        lootTable = parsedLootTable.toString();
                    } else {
                        LOGGER.warn("⚠ Invalid lootTable '{}' for group '{}'. Skipping it.", lootStr, group);
                    }
                }

                groupDataMap.put(group, new GroupData(center, radius, rarity, lootTable));
            }

            LOGGER.info("✅ Loaded {} groups from group_config.json", groupDataMap.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load group_config.json", e);
        }
    }

    public static void saveGroupConfig() {
        try (FileWriter writer = new FileWriter(GROUP_CONFIG_FILE)) {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, GroupData> entry : groupDataMap.entrySet()) {
                JsonObject obj = new JsonObject();
                GroupData data = entry.getValue();

                if (data.center != null) obj.add("center", posToJson(data.center));
                if (data.radius >= 0) obj.addProperty("radius", data.radius);
                if (data.rarity != null) obj.addProperty("rarity", data.rarity);
                if (data.lootTable != null) obj.addProperty("lootTable", data.lootTable);

                root.add(entry.getKey(), obj);
            }

            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save group_config.json", e);
        }
    }

    public static Set<String> getGroupNames() {
        return groupDataMap.keySet();
    }

    public static GroupData getMatchingGroup(BlockPos chestPos) {
        for (Map.Entry<String, GroupData> entry : groupDataMap.entrySet()) {
            GroupData data = entry.getValue();
            if (data.center != null && data.radius > 0 && chestPos.distManhattan(data.center) <= data.radius) {
                LOGGER.info("📍 Chest at {} is within radius {} of group '{}'", chestPos, data.radius, entry.getKey());
                return data;
            }
        }
        return null;
    }

    public static void setGroupCenter(String groupName, BlockPos pos) {
        GroupData data = groupDataMap.getOrDefault(groupName, new GroupData(null, -1, null, null));
        data.center = pos;
        groupDataMap.put(groupName, data);
        saveGroupConfig();
    }

    public static void addGroupEntry(String name, BlockPos center, int radius, String rarityOrLootTable) {
        boolean isLootTable = rarityOrLootTable.contains(":");
        String rarity = isLootTable ? null : rarityOrLootTable.toUpperCase(Locale.ROOT);
        String lootTable = isLootTable ? rarityOrLootTable : null;

        groupDataMap.put(name, new GroupData(center, radius, rarity, lootTable));
        saveGroupConfig();
    }


    public static boolean removeGroup(String groupName) {
        if (!groupDataMap.containsKey(groupName)) return false;
        groupDataMap.remove(groupName);
        saveGroupConfig();
        return true;
    }

    private static JsonArray posToJson(BlockPos pos) {
        JsonArray arr = new JsonArray();
        arr.add(pos.getX());
        arr.add(pos.getY());
        arr.add(pos.getZ());
        return arr;
    }

    private static BlockPos jsonToPos(JsonArray arr) {
        if (arr.size() != 3) return BlockPos.ZERO;
        return new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }

    public static GroupData getGroup(String groupName) {
        return groupDataMap.get(groupName);
    }

    public static String getRarityForPosition(String groupName, BlockPos pos) {
        GroupData data = groupDataMap.get(groupName);
        if (data != null && data.center != null && data.radius > 0 && pos.distManhattan(data.center) <= data.radius) {
            return data.rarity;
        }
        return null;
    }


    // ✅ PUBLIC + STATIC for visibility outside the file
    public static class GroupData {
        public BlockPos center;
        public int radius;
        public String rarity;
        public String lootTable;

        public GroupData(BlockPos center, int radius, String rarity, String lootTable) {
            this.center = center;
            this.radius = radius;
            this.rarity = rarity;
            this.lootTable = lootTable;
        }

        @Override
        public String toString() {
            return "GroupData{" +
                    "center=" + center +
                    ", radius=" + radius +
                    ", rarity='" + rarity + '\'' +
                    ", lootTable='" + lootTable + '\'' +
                    '}';
        }
    }
}
