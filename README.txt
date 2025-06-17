# 🧰 Looty — Dynamic, Respawning Loot Chests for Minecraft

Looty is a server-side Minecraft mod that brings life back to the loot system. It creates **immersive, respawning loot containers** (chests, barrels, shulker boxes) with configurable randomness, rarity, and distance-based progression — all driven by JSON.

> Think of it as Minecraft’s **loot system on steroids**, with the brains of a mod and the charm of a Dungeon Master.

---

## 🎯 Key Features

### 🧭 Dynamic Loot Respawn System
- Loot containers despawn and **respawn after a configurable cooldown**.
- Loot is never static — it’s freshly generated each time from defined rarity or group-based rules.
- **Supports chests, barrels, and shulkers**, each remembering their original block type.

### 📦 Rarity-Based Loot
- Loot rarity is based on **distance from world spawn** (`distance_config.json`):
  - `COMMON`, `UNCOMMON`, `RARE`, `LEGENDARY`
- Each rarity tier supports:
  - Min/max item count
  - Per-item spawn chances
  - NBT data, enchantments, and modded items

### 🏷️ Group-Based Loot Overrides
- Admins can place signs using `/looty addgroup <name> <radius> <rarity|loottable>`
- Any Looty container within the radius uses that group’s rarity or custom loot table
- Perfect for custom areas like dungeons, towns, or world events

### 📍 Alternate Respawn Positions
- Admins can link a Looty chest and **select up to 9 alternate spawn locations**
- Chest respawns at **random** among its origin + selected spawns
- `/looty addspawns` to save selections

### ✨ Visual Feedback (For Admins)
- Shift + Left/Right Click with a golden hoe to:
  - Link a Looty container
  - Select alternate spawn locations
- Glowing red wool markers show alternate spawn spots (visible only to admins)
- `/looty unlink` clears your linked chest and removes the ghost markers

### 💥 Particle Animations
- Chests **despawn with smoke**, and **respawn with fireworks or colored particles**
- Configurable via `looty_config.json`:
  - Toggle despawn/respawn animation
  - Switch between **"fancy fireworks"** and **"minimal particles"**

### 🧠 Fully JSON-Driven Configs
- `looty_config.json`: Define loot for each rarity (min/max items, chance, NBT)
- `group_config.json`: Define groups by name, center, radius, rarity or loot table
- `distance_config.json`: Distance thresholds for rarity tiers
- No code changes needed — server owners control it all!

---

## 🔧 Admin Tools & Commands

| Command | Description |
|--------|-------------|
| `/looty reload` | Reloads all configs live |
| `/looty toggleall` | Enable or disable all chests as Looty containers |
| `/looty setgroup <name>` | Sets your feet position as center of a group |
| `/looty addgroup <name> <radius> <rarity/table>` | Adds a loot group and places a sign |
| `/looty del <name>` | Deletes a group and its marker sign |
| `/looty addspawns` | Saves your selected alternate spawn positions |
| `/looty unlink` | Unlinks from chest and removes ghost markers |
| `/looty listgroups` | Lists all defined loot groups |

---

## 📁 File Structure

- `config/looty_config.json` → Loot tables by rarity
- `config/group_config.json` → Group-based override data
- `config/distance_config.json` → Rarity distance thresholds
- `config/looty_containers.json` → Valid blocks (chests, barrels, shulkers)
- `looty_data/` → Runtime state: despawned chests, original blocks, alt spawns, group signs

---

## 💡 Ideal For:

- PvE & RPG servers
- Survival games with **territory-based loot zones**
- Dungeon & event-driven worlds
- Loot randomization without relying on loot tables alone

---

## 🧪 Compatibility

✅ Works with:
- Modded items and NBT
- All dimensions (Overworld, Nether, etc.)
- Multiplayer servers
- Compatible with most structure mods

🚫 Not tested with:
- Mods that overwrite container logic (e.g., Lootr)
- Non-container loot entities (e.g., mobs or drops)

---

## 📦 Future Goals

- In-game GUI loot editor
- Per-player loot memory (optional)
- JEI integration
- Integration with quest/event mods

---

## 🪙 Credits

Made with ♥ by the Looty Dev Team  
Inspired by the spirit of **Clippy**, reimagined as a cheeky Minecraft chest.  
“Looks like you’re trying to build a dungeon... need some loot with that?”

---

## 🧠 Need Help?

Create an issue or ping us on GitHub. Contributions and suggestions are always welcome.

---

