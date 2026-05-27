# Vanilla Storage Interface Mod
![banner.png](src/main/resources/assets/vanilla_storage_interface/banner.png)

**A sleek, Vanilla-oriented storage terminal mod for Minecraft 1.21 (Fabric).**

### So you ask *why*?

> **Long story short: I wanted to solve one of my biggest problems in my long-term world – item storage.**
> The goal was to come up with a stupidly simple way of storing and accessing large quantities of items ***conveniently*** in one place, using nothing but Vanilla Minecraft inventories (primarily Chests and Shulker Boxes).

If you aren't a fan of digging through massive walls of chests and sifting through countless Shulker Boxes, this mod might be perfect for you! 😊🌸

The mod adds one block, the **Storage Interface**, which acts as a smart terminal for your existing storage. Simply attach an Interface to a Chest, Barrel, or almost any other block with an internal inventory to access a simple, **searchable, and scrollable virtual inventory**.

It also includes massive Quality-of-Life (QoL) features for accessing portable storage from anywhere using Keybinds.

---

## ✨ Features ✨

### 💻 Unified User Interface

Interacting with a Storage Interface opens a custom terminal tailored for bulk and miscellaneous item management:

* **Live Search Bar:** Quickly find what you need (Can sync with REI / *EMI support planned*).
* **Smart Sorting:** Sort items A-Z, Ascending, or Descending. Active sorting modes feature a sleek, semi-transparent green highlight so you always know which filter is active.
* **Scrollable Grid:** Bypasses Vanilla slot limits entirely.
* **Dynamic Quantities:** Displays compact, easy-to-read numbers (like `1.5k`) for stacks exceeding 64.
* **Live External Syncing:** The terminal actively listens to its connected physical inventory. If a Hopper injects an item or another player takes something out, your open terminal updates in real-time.

### ![ender_chest.png](src/main/resources/assets/vanilla_storage_interface/ender_chest.png) Portable Terminals (Void, Player & Shulker)

Access your storage on the go without placing blocks down! *(Can be toggled in settings).*

* **Void Terminal:** Access an Interface terminal for your Ender Chest and deep-scan all Shulker Boxes inside of it. *(Note: You must have an Ender Chest in your inventory to use this).*
* **Player Inventory Terminal:** Browse the contents of all Shulker Boxes sitting in your player inventory in one unified interface. *(Hotbar excluded).*
* **Shulker Box Terminal:** Open a terminal inside a Shulker Box simply by hovering over it or holding it, without ever placing it down!

### 🪧 Zero-Lag Visual Labels

Think of it as a built-in Item Frame, but without the Vanilla entity lag and packed with extra UX features. Put an item in the top-right slot of the UI to display it on the Storage Interface block.

* **Interactive:** Hover over the Ghost Slot and `Scroll` to Rotate, `Shift + Scroll` to Scale, and `Middle Click` to reset.
* **Zero Impact:** Renders statically into the chunk mesh. No ticking BlockEntities means you can have 1,000 of these in your base with zero frame drops. *(Works with almost all items, except Block Entities. See footnote [^1]).*

### 🌸 Blends Into Your Builds

* **Rustic & Modern:** Comes in **34 variants**. All 11 wood types in all 3 forms (Planks, Logs, and Stripped Logs/Stems) plus a Black Stained Glass variant. More to come!
* **💡 Glow in the Dark:** Right-click the Storage Interface block with a **Glow Ink Sac** to make it glow! Made a mistake? Right-click with a **Wet Sponge** to wipe the ink off. 😄

### 📦 Intelligent Auto-Packing

If you Shift-Click loose items into a Storage Interface connected to an inventory containing empty Shulker Boxes, the mod will automatically compress and pack those loose items into the empty Shulker Boxes to save space.

### 🛡️ Stackable Shulker Box Safety

Fully integrated support for mods that increase Shulker Box stack sizes (like Carpet or AllStackable). The terminal safely splits stacked Shulker Boxes when interacting with them, preventing accidental item duplication or box deletion.

---

## 📖 How to Use

1. **Craft a Frame:** Combine your wood of choice (Planks, Logs, or Stripped) to create a `Storage Frame`. *(Use your Recipe Book or mods like REI).*
2. **Craft the Interface:** Upgrade your Frame into the final `Storage Interface`.
3. **Place and Connect:** `Sneak + Right-Click` to place the Interface against any compatible Vanilla storage (Chests, Shulker Boxes, Hoppers, etc.). It can be placed from all 6 directions!
4. **Interact:** `Right-click` the block to open the terminal.

### 🖱️ Standard Mouse Controls

* `Left-Click`: Extract a full stack.
* `Right-Click`: Extract exactly one item.
* `Shift + Scroll Down`: Rapidly pull out full stacks!
* `Ctrl + Scroll Down`: Rapidly pull out single items!
* `Shift-Click (From Inventory)`: Insert items.
* `Shift + Double-Click`: Quick-move ALL matching items from your inventory into the terminal.

### ⌨️ Seamless IPN Integration (Inventory Profiles Next)

If you have IPN installed, the mod dynamically reads your `inventoryprofiles.json` configuration and mimics your **"Move All Items"** action. Whether you use a modifier + click (like `Space + Left Click`) or a pure keyboard chord (like `Left Alt + B`), your muscle memory will work perfectly inside the Virtual Terminal!

**If you don't have IPN installed, this defaults to `Space + Left Click`.**

* **Move All Matching:** Hover over an item and trigger your IPN action to instantly vacuum every identical item from that inventory into the opposite one.
* **Dump Inventory:** Hover over an empty slot in your player inventory and trigger your IPN action to instantly dump everything (excluding your hotbar) into the terminal.
* **Refill Inventory:** Hover over an empty space in the terminal grid and trigger your IPN action to instantly top off all partial stacks in your player inventory.
* **Refill Cursor:** Hold a partial stack of items on your cursor and `Middle-Click` that exact item in the terminal to instantly max out your hand to 64!
* **Smart Search Bar Logic:** Pressing your IPN keys while hovering over items will intelligently intercept the keystrokes so you don't accidentally type spaces or letters into the search bar.

> **Developer Note on IPN Integration:**
> Because this mod utilizes a fully virtual inventory – and *not* standard Minecraft inventory `Slot`s – making IPN work natively out of the box is impossible. Instead, I built a lightweight translation layer that parses your IPN settings from disk and perfectly replicates the logic natively inside the UI for some of the (imo most useful) IPN item movement magic features.

### 🖥️ Default Keybinds

* `V`: Opens the **Void Terminal** (Searches your Ender Chest).
* `H`: Opens a **Shulker Box Terminal** for the box your mouse is currently hovering over.
* `B`: Opens a **Shulker Box Terminal** for the box in your hand (or offhand).
* `N`: Opens the **Player Inventory Terminal** (Searches all Shulkers in your inventory).

*Note: Holding `Shift` while pressing any of the above keybinds opens the default Vanilla UI instead of the terminal. If `Invert Shift Modifiers` is enabled in the config, this logic is reversed.*

---

## ⚙️ Installation & Requirements

Place the `.jar` into your `.minecraft/mods` folder and launch the game. That's all there is to it, given that your game satisfies the following:

**Requirements:**

| Name | Version |
| --- | --- |
| **Minecraft** | 1.21.1 |
| **[Fabric Loader](https://fabricmc.net)** | >= 0.19.2 |
| **[Fabric API](https://modrinth.com/mod/fabric-api)** | * |

**Dependencies:**

| Mod Name | Version | Why? |
| --- | --- | --- |
| **[ModMenu](https://modrinth.com/mod/modmenu)** | >= 11.0.0 | Required for the settings page. |
| **[Cloth Config API](https://modrinth.com/mod/cloth-config)** | >= 15.0.0 | Required for the settings page. |

---

## 🛠️ Nerd Area

If you want to poke around the code, here are a couple of notes:

**Architecture & Virtual Inventory Syncing**

Like I mentioned before, the mod bypasses standard Vanilla UI Slots entirely. Instead of forcing 5,000 items into massive invisible chest screens, the `StorageAggregator` builds a `VirtualInventory` hashmap natively on the Server. It dives into 1.21's new `ContainerComponent` data to read nested Shulker Boxes dynamically (even inside an Ender Chest or Player Inventory). This data is converted into a `VirtualItem` list and sent via a custom `StorageSyncPayload`. The UI interaction happens inside an un-slotted grid, sending `StorageActionPayload`s to execute precise Server-side extractions/insertions.

**The Render Pipeline (Fabric Baked Models)**

Straight up, I don't like lag. Rendering 3D items inside glass panes usually requires a ticking `BlockEntityRenderer` (BER), which causes severe lag in bulk. Instead, `StorageInterfaceBakedModel` hooks into Fabric's mesh emission API. It pulls the native `ModelTransformationMode.FIXED` matrix (the exact math Mojang uses to flatten 3D items like swords/fences into Item Frames), applies your custom scale/rotation data from the NBT, and bakes the item geometry *directly* into the static Chunk mesh. **Zero tick impact.**

**REI Integration**
The mod hooks into REI natively. Hovering over any item in the virtual grid and pressing `R` or `U` will pull up recipes/uses exactly as if it were a physical item in your inventory.

---

*Feel free to contact me for potential improvements, feature requests, or any other inquiries. As this is my first time playing around with Minecraft modding, I still have much to learn, so please have patience with me :).*

> [^1]: Chests, Ender Chests and Shulker Boxes should render okay(?), but Beds and Shields won't render at all. They use the `BuiltinModelItemRenderer` via Java GL calls and because they lack standard quads, they won't render in the ghost slot. This is a negligible trade-off for keeping visual labels completely lag-free!