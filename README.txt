📜 WELCOME TO LOOTY 📜
A Dynamic Chest Loot Respawning System for Minecraft

🔹 What is Looty?
Looty is a Minecraft mod that allows chests in your world to despawn and respawn with fresh loot,
making exploration more engaging and rewarding. It supports custom loot tables and structure-based chest identification,
ensuring that dungeons, villages, strongholds, and other locations have unique loot pools.

With Looty, abandoned structures never run dry, and looting always stays exciting!

📦 LOOTY CONFIGURATION
Looty is fully customizable through looty_config.json and per-structure loot tables (.json files in the config/ folder).

These settings control global loot behavior, loot table management, and chest respawn mechanics.

🔧 GLOBAL LOOTY MODE
The setting: "enable_global_looty": false (default)
Set to true to make EVERY chest in the game a Looty chest (not just structures).
In this mode, any chest placed by a player will not be affected.
🔹 Admin Command: /looty_toggle_all → Toggles Global Looty mode. 🔹

📝 LOOT TABLES: How Chests Generate Loot
Looty uses 4 rarity tiers to determine loot spawns in the world:

✨ Tier	 Distance from Spawn	Loot Type:
- COMMON	0 - 1000 blocks	Basic loot (food, wood, stone tools)
- UNCOMMON	2000 - 2999 blocks	Better loot (iron, gold, enchanted books)
- RARE	3000 - 3999 blocks	Strong loot (diamonds, potions, nether items)
- LEGENDARY	4000+ blocks	Ultra-rare loot (netherite, beacons, enchanted gear)
Each rarity tier has a dedicated loot pool, controlled in looty_config.json.


💡 Example Loot Entry:
json
{ "item": "minecraft:diamond", "count": 1, "chance": 20 }
"count" → The quantity of this item given when selected.
"chance" → The % probability (0-100%) that this item appears.
🔹 If "chance": 50, then the item will spawn in 50% of chests that roll this loot table. 🔹

🏰 STRUCTURE-BASED LOOT
Looty automatically recognizes chests in structures and assigns loot based on their location.

📌 Supported Structures & Custom Loot Tables:
- Structure	Custom Loot Table
- Dungeons	dungeon_looty.json
- Strongholds	stronghold_looty.json
- Villages	village_looty.json
- Bastions	bastion_looty.json
- Ruined Portals	ruined_portal_looty.json
- Desert Temples	desert_temple_looty.json
- Jungle Temples	jungle_temple_looty.json
- Shipwrecks	shipwreck_looty.json
- End Cities	end_city_looty.json
- Woodland Mansions	woodland_mansion_looty.json
🛠️ Each structure has its own loot table in config/

Modify these .json files to customize loot per structure!
Loot does not override player-placed chests.

💡 Example (bastion_looty.json)
json
{
  "LEGENDARY": [
    { "item": "minecraft:netherite_scrap", "count": 1, "chance": 25 },
    { "item": "minecraft:ancient_debris", "count": 2, "chance": 30 }
  ],
  "RARE": [
    { "item": "minecraft:gold_block", "count": 1, "chance": 50 }
  ]
}
📌 This means Bastion chests have a 25% chance to contain Netherite Scrap and a 50% chance to contain Gold Blocks!

⚙️ LOOTY COMMANDS (Admin Only)
Looty includes powerful server commands for admins:
Admin Command Effect:

/looty_toggle_all	Toggles Global Looty mode (all chests auto-loot)
/looty_scan_structures	Scans all loaded chunks for Looty-eligible structure chests.

✨ HOW DOES LOOTY WORK?
1️⃣ Chests in structures are automatically marked as LootyAdmin.
2️⃣ When a Looty chest is opened, it starts a 30-second despawn timer.
3️⃣ Chests despawn and will respawn at randomized intervals between 15 - 60 minutes.
4️⃣ New loot is generated based on either:

Rarity tier (distance from spawn)
Structure-specific loot tables (if part of a dungeon, stronghold, etc.)
5️⃣ Players can still place their own chests, which Looty will ignore.
📌 FAQ & TROUBLESHOOTING
❓ Q: Can I add modded items to Looty?
✅ Yes! Simply add the correct modid:item_name into the .json files.

Example (for a modded sword from "mymod"):

json
{ "item": "mymod:epic_sword", "count": 1, "chance": 10 }
❓ Q: What happens if I remove all loot from a category?
⚠️ If you remove ALL loot from a category, chests may spawn empty!
Make sure each loot tier has at least one item available.

❓ Q: Can I make certain chests NOT respawn?
✅ Yes! If a chest was placed by a player, it will NOT be affected.

❤️ THANK YOU FOR USING LOOTY!
📌 Have suggestions? I’d love to hear them!
📢 Custom server with Looty support coming soon! Stay tuned!

- hooshcow 🚜✨