package com.example.looty;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class LootTableLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, LootRarityConfig> lootTables = new HashMap<>();
    private static final File lootConfigFile = new File("config/looty_config.json");
    private static final Random RANDOM = new Random();

    public static void loadLootTables() {
        if (!lootConfigFile.exists() || lootConfigFile.length() == 0) {
            LOGGER.warn("Loot config not found or empty. Creating default looty_config.json...");
            createDefaultLootConfig();
        }

        lootTables.clear();

        try (Reader reader = Files.newBufferedReader(Paths.get(lootConfigFile.toURI()))) {
            JsonElement parsed = JsonParser.parseReader(reader);

            if (!parsed.isJsonObject()) {
                LOGGER.error("❌ Loot config must be a JSON object with rarity keys like 'COMMON', 'RARE', etc. Found: {}", parsed);
                return;
            }

            JsonObject root = parsed.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String rarity = entry.getKey().toUpperCase();
                JsonObject rarityObj = entry.getValue().getAsJsonObject();

                int minItems = rarityObj.has("minItems") ? rarityObj.get("minItems").getAsInt() : 1;
                int maxItems = rarityObj.has("maxItems") ? rarityObj.get("maxItems").getAsInt() : 3;

                JsonArray lootArray = rarityObj.has("items") ? rarityObj.getAsJsonArray("items")
                        : rarityObj.has("loot") ? rarityObj.getAsJsonArray("loot") : null;

                if (lootArray == null) {
                    LOGGER.warn("Rarity '{}' missing 'items' or 'loot' array.", rarity);
                    continue;
                }

                List<LootEntry> entries = new ArrayList<>();
                for (JsonElement e : lootArray) {
                    if (!e.isJsonObject()) continue;
                    JsonObject obj = e.getAsJsonObject();

                    String itemId = obj.get("item").getAsString();
                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;

                    int countMin = obj.has("countMin") ? obj.get("countMin").getAsInt() : count;
                    int countMax = obj.has("countMax") ? obj.get("countMax").getAsInt() : countMin;

                    int chance = 100;
                    if (obj.has("chance")) {
                        JsonPrimitive p = obj.get("chance").getAsJsonPrimitive();
                        double raw = p.isNumber() ? p.getAsDouble() : 1.0;
                        chance = raw <= 1.0 ? (int)(raw * 100) : (int) raw;
                    }

                    if (chance <= 0) continue;

                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                    if (item == null) {
                        LOGGER.warn("Unknown item '{}'. Skipping.", itemId);
                        continue;
                    }

                    ItemStack baseStack = new ItemStack(item, 1); // count is handled per-roll
                    if (obj.has("nbt")) {
                        try {
                            CompoundTag tag = TagParser.parseTag(obj.get("nbt").getAsString());
                            baseStack.setTag(tag);
                        } catch (Exception ex) {
                            LOGGER.warn("Invalid NBT for '{}': {}", itemId, ex.getMessage());
                        }
                    }

                    entries.add(new LootEntry(baseStack, chance, countMin, countMax));
                }

                lootTables.put(rarity, new LootRarityConfig(entries, minItems, maxItems));
                LOGGER.info("Loaded {} items for '{}' (min={}, max={})", entries.size(), rarity, minItems, maxItems);
            }

        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to parse looty_config.json", e);
        }
    }

    public static List<ItemStack> getRandomizedLoot(String rarity) {
        LootRarityConfig config = lootTables.get(rarity.toUpperCase());
        if (config == null) return Collections.emptyList();

        List<ItemStack> result = new ArrayList<>();
        List<LootEntry> pool = new ArrayList<>(config.lootEntries());
        Set<String> alreadyIncluded = new HashSet<>();
        Collections.shuffle(pool, RANDOM);

        // Step 1: Chance-based inclusion
        for (LootEntry entry : pool) {
            if (RANDOM.nextInt(100) < entry.chance()) {
                ItemStack stack = copyItemStack(entry.stack());
                int amount = entry.countMin() == entry.countMax()
                        ? entry.countMin()
                        : RANDOM.nextInt(entry.countMax() - entry.countMin() + 1) + entry.countMin();
                stack.setCount(amount);
                result.add(stack);
                alreadyIncluded.add(entry.stack().getItem().getDescriptionId());
            }
        }

        // Step 2: Fallback logic to guarantee minimum
        int index = 0;
        while (result.size() < config.minItems() && !pool.isEmpty()) {
            LootEntry fallback = pool.get(index % pool.size());
            String id = fallback.stack().getItem().getDescriptionId();
            if (!alreadyIncluded.contains(id)) {
                ItemStack stack = copyItemStack(fallback.stack());
                int amount = fallback.countMin() == fallback.countMax()
                        ? fallback.countMin()
                        : RANDOM.nextInt(fallback.countMax() - fallback.countMin() + 1) + fallback.countMin();
                stack.setCount(amount);
                result.add(stack);
                alreadyIncluded.add(id);
            }
            index++;
        }

        Collections.shuffle(result, RANDOM);
        return result.subList(0, Math.min(config.maxItems(), result.size()));
    }

    private static ItemStack copyItemStack(ItemStack original) {
        if (original == null || original.isEmpty()) return ItemStack.EMPTY;
        return original.copy();
    }

    private static void createDefaultLootConfig() {
        JsonObject root = new JsonObject();
        root.add("COMMON", createRaritySection(1, 4, new Object[][] {
                {"minecraft:apple", 1, 2, 75},
                {"minecraft:bread", 1, 3, 90},
                {"minecraft:stick", 2, 5, 50}
        }));
        root.add("UNCOMMON", createRaritySection(1, 3, new Object[][] {
                {"minecraft:iron_ingot", 1, 3, 70},
                {"minecraft:gold_ingot", 1, 1, 40}
        }));
        root.add("RARE", createRaritySection(1, 2, new Object[][] {
                {"minecraft:diamond", 1, 1, 60}
        }));
        root.add("LEGENDARY", createRaritySection(2, 4, new Object[][] {
                {"minecraft:netherite_ingot", 1, 2, 50},
                {"minecraft:totem_of_undying", 1, 1, 30},
                {"minecraft:diamond_sword", 1, 1, 40}
        }));

        try (Writer writer = Files.newBufferedWriter(Paths.get(lootConfigFile.toURI()))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write default looty_config.json", e);
        }
    }

    private static JsonObject createRaritySection(int min, int max, Object[][] items) {
        JsonObject obj = new JsonObject();
        obj.addProperty("minItems", min);
        obj.addProperty("maxItems", max);

        JsonArray array = new JsonArray();
        for (Object[] item : items) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", (String) item[0]);
            entry.addProperty("countMin", (int) item[1]);
            entry.addProperty("countMax", (int) item[2]);
            entry.addProperty("chance", (int) item[3]);
            array.add(entry);
        }
        obj.add("items", array);
        return obj;
    }

    public record LootEntry(ItemStack stack, int chance, int countMin, int countMax) {}
    public record LootRarityConfig(List<LootEntry> lootEntries, int minItems, int maxItems) {}
}
