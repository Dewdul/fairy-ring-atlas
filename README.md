# Fairy Ring Atlas

Fairy Ring Atlas replaces the fairy ring dials with a map of every fairy ring destination. You can zoom in and pan the map, which is drawn sharply from the game's own world map. Choose a ring on the map. The plugin then shows you the travel log entry or the dial turns that get you there.

## Features

- **Map of every ring.** All 42 surface rings appear on a world map. You can zoom from an overview of the whole map down to individual buildings: scroll to zoom and drag to pan.
- **Names in the ring's own menu.** Right-click a fairy ring and *Last-destination (AJR)* and the *Favourites* codes show the destination's name, e.g. *CIR Mount Karuulm*. Entries Fairy Ring Favourites has already renamed are left alone.
- **Your house.** DIQ is drawn at your house portal (Rimmington, Taverley, Pollnivneach, Rellekka, Brimhaven, Yanille, Hosidius or Aldarin), and the *Your house* card zooms to it. It shows as locked until you have used a fairy ring in your house.
- **Favourites list.** Your in-game favourites (starred in the travel log) are listed at the top of the side panel. Click one to select it and zoom to it. Its card also reminds you of the game's own one-click teleport: right-click any fairy ring, then *Favourites*. Your last destination gets the same tip for *Last-destination*.
- **Off-surface destinations.** These include Zanaris, the Abyss, Mor Ul Rek, the Cosmic entity's plane and the other realms. Each one has a map of its own, opened from the *Elsewhere* section of the side panel or from a portal marker on the surface. Opening one also selects its ring (the first one in your travel log, when an area has two). The orange **Back to Gielinor** button at its top-left returns to the world map. *Your house* (DIQ) has a card.
- **Ring cards.** Hover over any ring for its code, name and area. The card also shows:
  - whether the ring is in your travel log;
  - what is nearby;
  - quest, skill and item requirements;
  - dangers;
  - on rings you can leave without a staff, a line saying so (unless you have the elite Lumbridge & Draynor Diary).
- **Marker states.** Markers show at a glance which rings are in your travel log, which are favourites, your last destination, the ring you are standing at and the code currently on the dials.
- **Travel log search.** Typing in the game's own travel log search dims the rings that don't match and zooms the map to the ones that do.
- **Clue helper.** The map marks your active clue location, and a fairy ring clue's code is selected for you. This needs the core *Clue Scroll* plugin to be enabled. A hot-cold clue is marked once it is solved.

## How to use it

1. Use a fairy ring. The map opens over the dials, centred on the ring you are standing at. The real **Teleport** and close buttons sit in the map's bottom-right corner. Teleport is greyed out until the dials show your chosen ring, so you can't teleport somewhere else by mistake.
2. **Click a ring** to select it. The greyed-out Teleport now reads *Teleport to* its code, and it and the card tell you the next step:
   - **The ring is in your travel log.** The log is narrowed to that code and its row is highlighted. Click the row (*Use code*), then click **Teleport**. If you click a row in the log yourself, the map follows and the log is left as it is.
   - **The ring is not in your travel log yet.** Click **Show dials**. Arrows on the real dials show which way to turn each one and how many times. Turn the dials, then click **Teleport**. The **Map** button takes you back to the map.
   - **The dials already show the code.** The card reads *Ready: click Teleport* and the Teleport button pulses.
3. To navigate:
   - **Mouse wheel:** zoom.
   - **Drag:** pan.
   - **`+` / `-`:** zoom in and out.
   - **Fit:** show every ring on this map (on an underground map, the whole area).
   - **Dials:** switch to the plain dials.
   - **Right-click a ring:** *Zoom to*, or *Clear selection*. Right-click anywhere else on the map, or press **Clear**, to clear the selection.

### Why clicking the map doesn't teleport you

RuneLite plugins may not act in the game for you. They can't send clicks, turn dials or choose a travel log entry. A click on the map therefore only selects a ring. The plugin then highlights the real log row, or shows you the dial turns, and you make those clicks yourself: the travel log row (or the dials) is what sets the code for **Teleport**. The plugin changes the fairy ring interfaces only while they are open: it hides the dials under the map, and narrows and recolours the travel log (in map or dial mode). Under the map it also removes the game's own right-click options, such as *Walk here*. It puts everything back when you close the interface.

## Settings

| Section | Setting | Default | What it does |
|---|---|---|---|
| Map | Open as map | on | Start with the map rather than the dials |
| | Max width / Max height | 1100 / 720 | Largest map size in resizable mode |
| | Open at | Around you | Centre on the ring you are standing at (*Around you*), *Fit all* rings, or *Remember* the last view this session |
| | Place names / Map icons | on | World map place names, and icons when zoomed in |
| | Fit to search | on | Zoom to the rings that match the travel log search |
| Markers | Code labels | All | Show codes for *All* rings, *Favourites* only, or *None* |
| | Dim unvisited | off | Draw rings that aren't in your log at half opacity |
| | Visited / Selected / Favourite colour | teal / orange / gold | Marker colours |
| Travel log | Filter travel log | on | Narrow the log to the selected ring, moved to the top |
| | Dial guidance | on | Turn arrows on the dials for the selected ring |
| Advanced | Clue helper | on | Mark the active clue and preselect fairy ring clue codes (needs the core Clue Scroll plugin) |

## Notes

- **Stretched mode.** The map is drawn at the game's own resolution and then stretched along with the rest of the game. It can't be sharper than the game itself.
- **Fixed mode.** The map fills the dial area. The side panel (*Favourites* and *Elsewhere*) starts collapsed, and you can open it with its tab.
- **Fairy Ring Map.** That plugin also draws over the dials, so the two are marked as conflicting: RuneLite offers to turn the other off when you enable one. If both are on anyway, the map shows a notice asking you to turn one of them off.
- **Memory.** About 30 seconds after you close the fairy ring, the plugin frees its decoded map tiles and image buffers.

## Data sources

- **Map imagery.** The map was rendered from the Old School RuneScape game cache with RuneLite's `MapImageDumper` (`net.runelite:cache` 1.13.1), using OpenRS2 cache 2727 (2026-09-30, build 241). It is bundled as small tiles, so the plugin makes no network requests. Place names and map icons come from the same cache.
- **Ring codes and landing tiles.** These were checked against the cache's fairy ring table. While the dials are open, the plugin also reads that table from the game.
- **Ring cards.** The card text (descriptions, nearby places, requirements and dangers) was written for this plugin, using the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) as reference.
- **Regenerating.** The generators live in `tools/`. See `tools/mapgen/README.md` and `tools/data/README.md`.

## Credits

- Fairy Ring Map (Plugin Hub) by jdkrupa, for the idea of a map in place of the travel log and for the travel log filtering technique this plugin also uses.
- RuneLite, for the client, the cache tools and `MapImageDumper`.
- OpenRS2, for the cache archive.
- The Old School RuneScape Wiki and its editors.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
