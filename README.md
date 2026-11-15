# Outfit Atelier

A standalone 1.20.1 Forge mod for designing and wearing your own cosmetic outfits. Draw a garment at a Sewing Table, watch it on your own character as you work, then take it away as a real item you can wear, gift, sell or display.

Outfits are cosmetic only. They have no armor value and sit in their own wardrobe so they are separate from the armor slots.

## Requirements

| Mod | Version | Side |
| --- | --- | --- |
| Minecraft | 1.20.1 | client and server |
| Forge | 47.x (built against 47.4.0) | client and server |

No other mods are required but the "Outfit Atelier" mod must be installed on **both** the client and the server. Java 17.

## Installation

Put `outfitatelier-forge-1.20.1-<version>.jar` in the `mods` folder of the server and of every client.

## How to use it

### Crafting

**Sewing Table** (shaped):

```
T S      T = string
P P      S = shears
P P      P = any planks
```

**Blank Garment** (shapeless): two wool of any color and one string.

### Designing

Right-click the Sewing Table.

- **Template button** (bottom left) cycles between Shirt, Coat, Skirt, Dress and Cape. Changing the template clears the drawing; Undo brings it back.
- **< panel >** chooses which flat part you are drawing on: front, back, sides, shoulders, each sleeve, skirt, coat tails, or the cape outside and lining. A sleeve is shown unrolled: outer side, front, inner side, back.
- **Pencil**, **Fill**, **Erase**, **Pick** and **Undo** work on the panel shown.
- **Mirror** copies the sleeve you are on to the other arm.
- **Color wheel** – click or drag in the wheel for hue and saturation and in the bar for brightness.
- **Hex field** – type a color as `#RRGGBB`. A valid value updates the wheel. An invalid value changes nothing.
- **Recent colors** are the small squares under the hex field.
- Pixels you leave empty stay see-through, so you can shape necklines, open coats, short sleeves and hems.

The middle of the screen shows your own character wearing the garment. Drag it to turn it around. It uses your real skin, so wide and slim arms both preview correctly.

### Finishing

Type a name and press **Finish garment**. This takes one Blank Garment from your inventory (free in creative) and gives you the finished garment item. A finished design cannot be changed.

Under the name field, **< name >** browses your finished designs. Click the name to load one as a starting point for a new design. **Copy** makes another garment of that design for one Blank Garment.

### Wearing

- Right-click with a garment in hand to put it on. A garment already in that slot comes back to your hand.
- Press **O** (changeable in Controls) to open the Wardrobe and take garments off.
- There are four wardrobe slots: Top (shirt or dress), Skirt, Coat and Cape. They can all be worn together.
- Garments render over your skin and under armor. I recommend using the cosmetic armor mod so you can hide armor but still wear clothes over it but its not really necessary.
- On death the wardrobe drops with the rest of your items, unless `keepInventory` is on.

### Displaying

A vanilla armor stand is the display mannequin.

- Right-click an armor stand with a garment to dress it.
- Sneak and right-click it with an empty hand to take the last garment off.
- Breaking the stand drops its garments.

### Operator catalog

Operators (permission level 2) can open the catalog with the **Catalog** button in the Sewing Table screen or with `/outfitatelier catalog`.

- Lists every design on the server, newest first, with name, creator, garment type and date.
- The search field filters by design name or creator.
- Selecting a design shows it on your character.
- **Take copy** gives a free garment. The original creator stays on the item.
- **Delete**, then **Confirm**, deletes the design.

Deleting is permanent. The design stops rendering for everyone at once, whether worn, displayed or held by an offline player, and its items read *Design deleted*. The server keeps a small record of the deleted id so it cannot come back after a restart. The server checks operator permission on every catalog request.

## How it works

- Each design is stored once on the server as a small indexed-color image. Garment items, wardrobes and armor stands only hold the design id.
- A client downloads a design the first time it needs to draw it and keeps up to 256 designs in memory.
- Garment pictures are 128 by 128 with up to 127 colors.

## Configuration

`world/serverconfig/outfitatelier-server.toml`

| Setting | Default | Meaning |
| --- | --- | --- |
| `maxDesignsPerPlayer` | 100 | Finished designs per player |
| `garmentsCostBlank` | true | Finishing or copying takes one Blank Garment in survival |

## Saved data

Designs are stored in `world/data/outfitatelier_designs.dat`. Worn and displayed garments are saved with the player or armor stand.

## Known limits

- Small armor stands show garments at full size.
- A short skirt does not bend with the legs, so legs can show through it while running.
- Capes hang at a fixed angle.

## Building

Requires Java 17. From a clean checkout on Windows:

```
gradlew.bat build
```

The JAR is written to `build/libs/outfitatelier-forge-1.20.1-<version>.jar`.

To run the automated game tests:

```
gradlew.bat runGameTestServer
```

## Assets

All textures are original (and VERY amateur so I will be working on that after adding enough useful features) and are in `src/main/resources/assets/outfitatelier/textures`:

- `block/sewing_table_top.png`, `block/sewing_table_side.png` – the Sewing Table.
- `item/blank_garment.png` – the Blank Garment.
- `item/garment_shirt.png`, `garment_coat.png`, `garment_skirt.png`, `garment_dress.png`, `garment_cape.png` – inventory icons for each garment type. The icon shows the type, not the design.