# Fairy Ring Atlas: design spec

A RuneLite Plugin Hub plugin by Dewdul. It replaces the fairy ring dial screen
with a high-resolution, pannable and zoomable map of every fairy ring
destination. It improves on the hub plugin "Fairy Ring Map" (jdkrupajk), whose
map is a single 508x312 image at 0.18 px per game tile: no zoom, no pan,
overlapping markers, off-map destinations shown only as a strip of 11 px
icons, and the dials blocked while the map is showing.

The facts this spec relies on (interface ids, client scripts, varbits, cache
tables, Plugin Hub reviewer rulings and Jagex's third-party client guidelines)
are restated below, so this file is self-contained. Game facts were checked
against the game cache and the Old School RuneScape Wiki.

- Internal hub name: `fairy-ring-atlas`
- Display name: "Fairy Ring Atlas"
- Package: `com.fairyringatlas`
- Config group: `fairyringatlas`
- Java target: release 11
- Built against RuneLite `latest.release` (1.13.1 today)
- Build with JDK 21: `JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"`.
  Gradle 8.10 cannot run on the default JDK 25.

---

## 1. Hard rules (compliance; non-negotiable)

These come from Jagex's third-party client guidelines and from Plugin Hub
reviewer rulings. Breaking one gets the plugin rejected.

1. **The plugin never sends a game action.** It never calls:
   - `client.menuAction`, `client.runScript`, `createScriptEventBuilder`,
     `ScriptEvent.setCanSendPackets`
   - `client.setVarbit`, `setVarbitValue`, `setVarcIntValue`, `setVarcStrValue`
   - `java.awt.Robot`, `dispatchEvent`, `KeyboardFocusManager`, reflection
   - anything else that injects input.

   A unit test (`ComplianceTest`) scans `src/main/java` for these strings and
   fails if it finds one.
2. **A map click only selects a ring.** The player's own clicks on the real
   game widgets do the work: the travel-log row ("Use code"), the rotate
   zones and Confirm ("Teleport to this location"). The fairy ring client
   scripts send nothing, and log rows have no onop script. A map click
   therefore *cannot* set the code, and must not try.
3. **Menu entries the plugin adds are `MenuAction.RUNELITE`** with `onClick`
   callbacks that change only plugin state.
4. **Game widgets: hide, move and recolour only.** Hiding, recolouring and
   moving fairy-ring rows within their own interface is the approved technique
   of Fairy Ring Map.
   - Never unhide a component the game hid. Restore only what we changed
     ourselves.
   - Never move CONFIRM, the close button, or the top-level modal containers.
5. **No network access at runtime.** All imagery and data are bundled.
6. **Image budget.** The hub bot rejects any bundled image whose decoded size
   (w*h*4) is about 1 MiB or more. Every bundled PNG is therefore at most
   256x256. Keep total bundled resources at **6.5 MiB or less** so the jar
   stays near 7 MiB; the hard cap is 10 MiB and a warning appears above 8.
7. **No keyboard capture by default.** The game auto-opens its own chatbox
   search when the log opens, and typing must keep working.
   - Search is read passively from `VarClientID.FAIRYRINGS_SEARCHSTRING` (1344).
   - No KeyListener. The plugin never opens or focuses windows.
8. **Source size.** The review bot counts only `src/main/java`, with comments
   stripped, against a 200k-token budget (riktenx, plugin-hub #17419). Keep the
   Java lean. Generators live in `tools/` as standalone projects. The root
   `settings.gradle` must not include them.

---

## 2. Game facts used (verified in research)

### Interfaces

**Dial interface 398, `InterfaceID.Fairyrings`.** Child geometry is relative to
`ROOT_RECT0`, which is 512x334.

| Component | Constant | Value | Position and notes |
|---|---|---|---|
| Background | `ROOT_RECT0` | 26083328 | Child 0 |
| Ornament | `ROOT_MODEL1` | | (242,34) |
| Title text | `ROOT_TEXT2` | | "Choose a combination" |
| Dial bases | `ROOT_MODEL3/4/5` | | x=72/242/409, y=150 |
| Dial 1 letters | `A, B, C, D` | | Models, children 6-9 |
| Dial 2 letters | `I, J, K, L` | | Models, children 10-13 |
| Dial 3 letters | `P, Q, R, S` | | Models, children 14-17 |
| Centre ornament | `ROOT_MODEL18` | | |
| Rotate zones, dial 1 | `_1_CLOCKWISE` / `_1_ANTICLOCKWISE` | | (88,82) / (8,82), each 80x160 |
| Rotate zones, dial 2 | `_2_CLOCKWISE` / `_2_ANTICLOCKWISE` | | (258,82) / (178,82) |
| Rotate zones, dial 3 | `_3_CLOCKWISE` / `_3_ANTICLOCKWISE` | | (426,82) / (346,82) |
| Ornament behind Confirm | `ROOT_MODEL25` | | |
| Confirm | `CONFIRM` | 26083354 | (172,254) 169x53, op "Confirm". Core Fairy Rings plugin rewrites its text to the destination name |
| Close button | `ROOT_GRAPHIC27` | | (476,10) 26x23 |

**Travel log 381, `InterfaceID.FairyringsLog`.**

| Component | Constant | Value | Notes |
|---|---|---|---|
| Root | `UNIVERSE` | | 190x261 |
| Search button | `SEARCH` | | |
| Scroll layer | `CONTENTS` | | |
| Favourites block | `FAVES` | | |
| Code rows | one per code: `AIP` … `DLS` | | Hidden with empty text unless the server filled them; ops "Use code" / "Add Favourite" |
| Star icons | `*_FAVE` | | |
| Favourite rows | `FAVE_1..10` | | Op "Use code" |
| Favourite code text | `FAVE_CODE_1..10` | | |
| Favourite icons | `FAVE_ICON_1..10` | | |
| Scrollbar | `SCROLLBAR` | | |
| Hideout row | `HIDEOUT` | | |

- A code that is favourited appears in the `FAVE_n` rows, **not** in its
  ordinary row.
- Proc **8080** (`fairyrings_sort_update`) re-lays-out the log (on open, sort
  and search, and about every 20 cycles while typing). Re-apply our row edits
  in `ScriptPostFired` for 8080. It also creates dynamic CC text children for
  the red code labels.
- Script **399** (confirm) hides the six rotate zones for about 0.9 s.
  Script **400** un-hides them. Re-apply our hides after `ScriptPostFired` 400.

### Data in the cache: DB table 89 (`DBTableID.Fairyring`)

- Columns:

  | Column | Index | Contents |
  |---|---|---|
  | `COL_ID` | 0 | |
  | `COL_MULTILOC_STATE` | 1 | 16·d1+4·d2+d3 |
  | `COL_DEST_COORD` | 2 | Packed coord |
  | `COL_CODE` | 3 | "A I S", with spaces |
  | `COL_TEXT_COMPONENT` | 4 | Packed widget id of the log row |
  | `COL_FAVE_ICON_COMPONENT` | 5 | |
  | `COL_DESC` | 8 | |
  | `COL_NO_STAFF_RETURN` | 11 | |

- There are 64 rows. Unused codes have an empty desc and coord (65,65,plane 1).
  DIQ (the house) has coord 0.
- Client API (client thread only):
  - `client.getDBTableRows(89)`
  - `client.getDBTableField(row, col, 0)`, which returns `Object[]`
- Verify how a coord comes back (probably an `Integer` packed as
  `plane<<28 | x<<14 | y`) by javap or a test, and decode it ourselves.

### Varbits and varcs

- **Dials:** `VarbitID.FAIRYRING_1/2/3` = 3985/3986/3987, values 0..3. The
  letter orders are **not alphabetical**:
  - dial 1 is `{A,D,C,B}`
  - dial 2 is `{I,L,K,J}`
  - dial 3 is `{P,S,R,Q}`
- **Rotating:** a clockwise click does `v=(v+1)%4` and an anticlockwise click
  does `v=(v+3)%4`. For target `t`, let `k=(t-v)%4`:
  - k=0: nothing to do
  - k=1: 1 clockwise
  - k=2: 2 clicks (either way; show clockwise)
  - k=3: 1 anticlockwise
- **Visited, i.e. present in the travel log:** `VarbitID.FAIRYRINGS_LOG_<CODE>`.
  The per-code ids are bundled in `rings.json` as `logVarbit`.
  `FAIRYRINGS_LOG_HIDEOUT` = 4026.
- **Last destination:** `VarbitID.FAIRYRING_LASTLOC` (5374) is the
  multiloc_state index 16·d1+4·d2+d3. `FAIRYRING_LASTLOC_SET` (20250) says
  whether it is set.
- **Favourites:** `VarbitID.FAIRYRING_FAVE_1..10` (20240-20249). Each holds
  `set<<6 | d1<<4 | d2<<2 | d3` in dial-value order.
- **Search string:** `VarClientID.FAIRYRINGS_SEARCHSTRING` (1344), read only.
  `VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE` (4498) means no staff is needed.

### Open and close

- `WidgetLoaded` and `WidgetClosed` fire for group 398 and for 381.
- The interface is open when `client.getWidget(Fairyrings.CONFIRM)` is
  non-null and not hidden.
- 398/381 can close and reopen within about 2 ticks (when a favourite is
  toggled). Keep the selection across a reopen within 3 ticks.
- `MenuOptionClicked` with option "Use code" on group 381 means the player
  committed a row. Sync the map selection to that code.

### Layout

- **Fixed mode:** the dial interface fills the 512x334 viewport at canvas (4,4).
  The log is at (547,205), 190x261.
- **Resizable:** the dial is centred in the area above the chatbox and left of
  the side panel. The log is in the side-panel area, bottom right.
- Always use live `Widget.getBounds()`.

---

## 3. Bundled resources (contracts between generator and plugin)

All resources live under `src/main/resources/com/fairyringatlas/`.

### 3.1 `rings.json` (written by `tools/build_rings.py`)

The source is `tools/data/fairy_rings.verified.json`, which is the verified
research dataset.

```json
{
  "rings": [
    {
      "code": "AIQ",                       // null for non-code entries (see kind)
      "kind": "destination",               // destination | sequence | exit
      "name": "Mudskipper Point",          // short display name
      "description": "One line.",
      "x": 2996, "y": 3114, "plane": 0,    // landing tile (game dest_coord); 0,0,0 for DIQ
      "layer": "surface",                  // layer id, see 3.3; "poh" for DIQ (no map)
      "area": "Asgarnia",                  // region label for the info card
      "requirements": ["Quest: ...", "..."],  // per-ring only; human-readable lines
      "danger": ["..."],
      "poi": ["Mudskipper Point", "Mogres", "..."],  // shown in the card and used for search
      "tags": ["mogre", "..."],            // extra search terms (RuneLite enum tags etc.)
      "notes": ["..."],
      "logVarbit": 3990,                   // FAIRYRINGS_LOG_<CODE>; -1 if none
      "noStaffReturn": false
    }
  ],
  "global": {
    "requirements": ["...partial Fairytale II...", "...Dramen or Lunar staff unless the elite Lumbridge & Draynor Diary is complete..."]
  },
  "portals": [ { "layer": "zanaris", "x": 3201, "y": 3169, "label": "Zanaris" } ]
}
```

- **Portals** are optional. They are surface positions where an underground
  layer is entered, and are only listed where a clear single entrance exists.
- **Other entries:** the AIR→DLR→DJQ→AJS hideout sequence (`kind: sequence`)
  and the 2 Zanaris exit rings (`kind: exit`) are included for information.
  They are shown in the Zanaris layer; they are not dial targets.

### 3.2 Map tiles (written by `tools/mapgen`)

- **Path and coordinates:** `map/<z>/<tx>_<ty>.png`, where z is the zoom level,
  pixels per game tile `ppt = 2^z`, and every tile is 256x256.
  - Each tile spans `256/ppt` game tiles.
  - Tile `(tx,ty)` covers x ∈ `[tx*span, (tx+1)*span)` and y ∈ `[ty*span, (ty+1)*span)`.
  - **Image row 0 is the NORTH edge.** Pixel (px,py) shows game tile
    `x = tx*span + px/ppt`, `y = (ty+1)*span - 1 - py/ppt`.
  - At z=2 (4 ppt), one tile is exactly one 64x64 map region, so tx and ty
    are the region coordinates.
- **Bundled levels:**
  - z=2 is the full-detail base, plane 0, with no icons and no labels baked in.
  - One small overview level, z=-1 (0.5 ppt) at least, also bundled.
  - The plugin derives any other level at runtime.
- **Missing tiles:** tiles that are a single solid colour, or that are missing,
  are not shipped. The index records the fill colour per layer.
- **Format:** PNG8 (palette) where it looks right, otherwise PNG24. Smallest wins.
- **Index:** `map/index.json`

```json
{
  "cache": "openrs2 2727 (2026-09-30, build 241)",
  "tileSize": 256,
  "levels": [2, -1],                         // bundled z levels
  "tiles": { "2": ["18_37", "18_38", ...], "-1": ["2_4", ...] },   // shipped tiles per level
  "solid": { "2": { "20_40": "#2b4a6b" } },  // optional: solid tiles not shipped
  "layers": [
    { "id": "surface", "name": "Gielinor", "bounds": [x0, y0, x1, y1], "background": "#rrggbb" },
    { "id": "zanaris", "name": "Zanaris", "bounds": [...], "background": "#000000" }
  ],
  "labels": [ { "t": "Lumbridge", "x": 3222, "y": 3218, "s": 1, "layer": "surface" } ],
  "icons":  [ [3208, 3220, 1448] ]            // [x, y, sprite] triples
}
```

- **Bounds** are inclusive-exclusive world tile bounds.
- **`labels`** are world-map place names exported from the cache's world-map
  data. `s` is the size class: 0 small, 1 medium, 2 large.
- **`icons`** are map-function icons, written as compact `[x, y, sprite]`
  triples because they are most of the file. `sprite` is a game sprite id the
  plugin draws through `SpriteManager`.
- Both are optional. If the cache API makes either impractical, ship an empty
  array and say so.

### 3.3 Layers (ids shared by both files)

| id | name | rings | rough bounds (world tiles) |
|---|---|---|---|
| surface | Gielinor | the 42 surface rings | crop x 1152–3776, y 2368–4032 (generator may tighten to all rings ±1 region) |
| zanaris | Zanaris | BKS, hideout sequence landing (2328,4426), Zanaris exits | [2304,4288]–[2560,4544] |
| abyss | The Abyss | ALR (Abyssal Area) and DIP (Abyssal Nexus) | [2880,4672]–[3200,4992] |
| dorgesh_south | Dorgesh-Kaan South Dungeon | AJQ | [2624,5120]–[2816,5312] |
| fisher_realm | Fisher Realm | BJR | [2560,4608]–[2752,4800] |
| enchanted_valley | Enchanted Valley | BKQ | [2944,4416]–[3136,4608] |
| mor_ul_rek | Mor Ul Rek | BLP | ring (2437,5126) ± about 1.5 regions, within [2304,4928]–[2624,5248] |
| cosmic_plane | Cosmic entity's plane | CKP | [1984,4736]–[2176,4928] |
| gorak_plane | Gorak Plane | DIR | [2944,5248]–[3136,5440] |
| yubiusk | Yu'biusk | BLQ | 3x3 regions around (3572,4372) |
| grimstone | Grimstone Dungeon | DLP | 3x3 regions around (2926,10455) |
| hollows | Myreque Hideout (The Hollows) | DLS | [3328,9728]–[3584,9984] |
| poh | Your house | DIQ | no map; card only |

Every ring's coordinates must fall inside its layer's bounds. The generator
checks this and also renders only regions that hold map data.

---

## 4. Plugin behaviour (UX)

### 4.1 Modes

When the dial interface (398) opens, the overlay shows in **Map mode** by
default (config `openInMapMode`). The overlay draws only while 398 is open.

- **Map mode.** The map covers the dial interface.
  - We hide these 398 components: ornaments 1/18, title text 2, dial models
    3-17 and the six rotate zones 19-24.
  - We keep `ROOT_RECT0`, `ROOT_MODEL25`, CONFIRM and the close button.
  - The overlay leaves **holes** over CONFIRM and the close button. Inside a
    hole nothing is drawn and no input is consumed, so the real widgets show
    and work. Each hole gets a thin frame and a drop shadow so it reads as a
    button on the map.
  - Re-apply the hides on `WidgetLoaded(398)`, `ScriptPostFired(400)` and a
    cheap per-tick check. Restore everything we hid when leaving Map mode, when
    398 closes, and on shutdown.
- **Dial mode.** The overlay hides the map. The vanilla dials are visible and
  usable. Two things are drawn:
  - A small floating **"Map"** button near the dial interface's top-left
    corner, which switches back.
  - **Dial guidance** when a ring is selected and the dials don't match. On
    the real rotate zone the player should click (clockwise or anticlockwise
    half), draw an arrow and an "x1" or "x2" badge. Show the target letter
    over each dial, and a check mark when a dial is correct. Guidance drawing
    never consumes input.

  The **"Show dials"** button in the map's info card or toolbar switches to
  Dial mode.

### 4.2 Map layout (`MapLayout`)

**Resizable mode.**

1. Start from the usable area `avail`:
   - the canvas, inset by 6 px, grown to hold the dial rect when the dials
     sit closer than 6 px to an edge (small or stretched resizable windows);
   - minus the side-panel or travel-log region: limit `avail.right` to the
     log's `x - 8` when the log is open and overlaps vertically, otherwise to
     the side panel's left edge;
   - minus the chatbox: limit `avail.bottom` to the chatbox top `- 6`. Use the
     chatbox's live bounds if it is visible.
2. Size the map `W×H` = min(config `maxWidth` (default 1100), avail.w) by
   min(config `maxHeight` (default 720), avail.h).
3. Never make it smaller than the dial rect (512x334). If `avail` is smaller
   than that, use the dial rect.
4. Centre the map on the dial's centre, then shift it to lie inside `avail`.
5. The dial rect must lie inside the map. If it cannot, grow or shift the map
   to contain it, because the holes must fall inside the map.

**Fixed mode** (`!client.isResized()`): the map rect equals the dial rect.

### 4.3 Map chrome

**Frame.** A 2 px dark border (`#1e1a14`), a 1 px inner highlight, and a soft
shadow outside.

**Top bar.** A translucent dark strip about 22 px tall, inside the map along
its top edge. It holds:
- the breadcrumb: "Gielinor", or "Gielinor ▸ Zanaris" with a clickable
  "◂ Gielinor" back button;
- the live dial readout, e.g. "Dials: A I Q → Mudskipper Point" or
  "→ nothing", taken from the varbits;
- the active search, e.g. "Search: zul (3)";
- buttons on the right: `−`, `+`, `Fit`, `Dials`.

Buttons are hit-tested by the overlay and work through RUNELITE menu entries
(left-click).

**"Elsewhere" panel.** A vertical strip of cards inside the map's left edge,
about 150 px wide. It can be collapsed to a 20 px tab.

- It holds one card per off-surface layer and one for the house (DIQ):
  - Each card shows the layer name and its ring code chips, e.g. "ALR DIP"
    for The Abyss.
  - Chip colours follow the marker state colours in 4.4.
- Clicking a card opens that layer, with the view fitted to the layer's
  bounds. The house card selects DIQ directly.
- In a non-surface layer, the active card is highlighted and the top bar shows
  the back button.
- Cards whose rings match the active search are highlighted. Cards with no
  matching ring are dimmed.

**Info card.** It describes the hovered ring, or the selected ring when
nothing is hovered.

- **Position:** anchored bottom-left inside the map. It must stay clear of the
  CONFIRM hole: if it would overlap, use the bottom-right corner.
- **Contents:**
  - code, name and area;
  - status: visited (in your travel log) or not, plus star / last-destination
    badges;
  - description;
  - POIs (up to about 6);
  - requirements, in amber if the ring is not yet visited;
  - danger lines in red;
  - notes (a couple of lines).
- **Next-step line** for the selected ring (see 4.5).
- Long text wraps. The card is capped at about 340x260, with
  "…" truncation if it would overflow.

### 4.4 Markers and labels

**Ring markers** are drawn as vector shapes (no sprites):

- **Base:** a ring of 8 small dots, or a mushroom-ring style circle, about
  10-14 px at any zoom. It grows slightly when zoomed in past 4 ppt.
- **Colours by state:**

  | State | Look |
  |---|---|
  | Visited (in log) | Teal `#3FD9C8` |
  | Not visited | Hollow grey-teal with a dashed outline, readable but clearly different |
  | Favourite | Gold star badge |
  | Last destination | Small "↺" badge |
  | Hover | White outline |
  | Selected | Orange `#FF981F`, larger, with a soft pulse halo |
  | Currently dialled code | Dashed orange ring, even when not selected |
  | You are here | The ring nearest the player, within 4 tiles when 398 opened. Pin icon or "You" tag |

- **Search dimming:** non-matching markers drop to 25% alpha.

**Code labels.** Draw the code (e.g. "AIQ") next to a marker in the
RuneScape small font, with a 1 px dark outline.

- Config `codeLabels`: ALL (default), FAVOURITES, NONE.
- **Placement:** a greedy solver tries 8 candidate positions around the marker.
  Selected, hovered and favourite labels are placed first. A label that cannot
  be placed is hidden.

**Place labels.** Use `index.json` labels with screen-constant font sizes:
- s=2 always shows;
- s=1 shows at ppt ≥ 0.75;
- s=0 shows at ppt ≥ 2.

Labels are white or yellow with a dark outline, like the in-game world map.
Config `placeLabels` (default on).

**Map icons.** Config `mapIcons` (default on). Draw from `index.json` icons at
ppt ≥ 2 at native sprite size. Fetch sprites through
`SpriteManager.getSpriteAsync` and cache them.

**Portals.** On the surface map, draw a portal marker at each `portals[]`
position: a dungeon or ladder glyph plus the layer name. Clicking it opens
that layer.

**Clue marker.** Config `clueHelper`, default on.
- If a clue with a world location is active (through `ClueScrollService`),
  draw a clue "X" at that location on whichever layer contains it, and a thin
  dashed line to the nearest visited ring.
- If the clue is a fairy ring clue, auto-select that code when 398 opens.
- Do all of this defensively. Wrap it in try/catch so the map never breaks if
  the clue API changes.

### 4.5 Selection flow

Hovering a marker updates the info card. The left-click menu entry is
`Select` with target `<code> <name>`.

**Selecting a ring** (left-click on a marker, a card chip or a portal-layer
marker) does the following:

- **Sets plugin state:** `selected = code`. When 398 opens again the selection
  is cleared, unless the reopen is within 3 ticks or a clue auto-selects.
- **If the dials already show the code:** the next step is "Ready: click
  Teleport". The CONFIRM hole frame pulses green.
- **Else, if the code has a log row** (visited, or one of the 10 favourites):
  1. When `filterTravelLog` is on (default) and the native search is empty,
     **filter the log**:
     - hide every other row and the favourites block;
     - move the selected row, its star and its dynamic code label to the top
       of the list;
     - colour the row orange.

     The real row the player clicks is the favourite row `FAVE_n` when the
     code is favourited, otherwise the ordinary row.
  2. Draw a pulsing highlight rectangle around the row's on-screen bounds.
     This drawing must not consume input.
  3. Draw a curved leader arrow from the info card (or the map edge nearest
     the log) to the row.
  4. The next-step line reads: "Click the highlighted code in your travel
     log, then Teleport."
- **Else** (not visited): the next-step line reads "Not in your travel log
  yet — dial it: A ↻1 · I ✓ · Q ↺1", followed by a **[Show dials]** button.

**After "Use code" or a manual rotation:**
- `VarbitChanged` on 3985-3987 updates the dial readout.
- When the dials equal the selection, the next step reads "Ready: click
  Teleport".
- In Dial mode, the guidance arrows update live.

**Deselecting:** right-click "Clear selection" (on empty map and chrome it
sits below Cancel, because a left press there pans or is absorbed), the
Esc-free toolbar "Clear" button, or selecting another ring. Deselecting
restores the log.

**When the native search becomes non-empty:**
- stop filtering the log and restore it;
- the selected row is only highlighted, and only if the game shows it;
- markers that don't match the search are dimmed;
- if `autoFitSearch` is on (default), animate the view to fit the matching
  rings on the current layer;
- if the current layer has no match and another layer does, flash the
  matching Elsewhere cards.

**Travel-log row clicks:** a `MenuOptionClicked` "Use code" on a 381 row
selects that ring on the map and pans to it. This keeps the map in sync when
the player uses the log directly. The log is not filtered for a code picked
this way: the player is already using it.

**Dials matching the selection:** the filter is lifted (re-checked on
`VarbitChanged` for 3985-3987), because the next step is Teleport.

### 4.6 Navigation

| Input | Effect |
|---|---|
| Mouse wheel over the map | Zoom about the cursor by ×1.25 per notch (use precise rotation). Range: 0.125 ppt up to 16 ppt, clamped |
| Left-press and drag on empty map | Pan |
| Toolbar `+` / `−` | Zoom about the centre |
| `Fit` | Fit every ring on the current layer, or the layer bounds for underground layers |
| `Dials` | Switch to Dial mode |

**Animation.** Fit, search-fit and zoom-to-selection animate over about 250 ms
with ease-out. Fixed-step zooming stays crisp.

**Clamp.** Keep the view so the layer bounds cannot be dragged entirely out of
sight.

**Initial view** (config `startView`):
- FIT_ALL (default) fits all surface rings;
- AROUND_YOU centres on the ring you are standing at, at 2 ppt;
- REMEMBER restores the last view of the session.

In every case, if the player stands at an off-surface ring, open that layer.

### 4.7 Input and menu ownership (`AtlasInput`, `onPostMenuSort`)

**Mouse listener.** One `MouseAdapter` that also implements
`MouseWheelListener`. Register it with the normal appending
`mouseManager.registerMouseListener(l)` and
`registerMouseWheelListener(l)`. Do **not** register at index 0, because
Stretched Mode's coordinate translators must run first.

The listener is "active" only when 398 is open, the mode is Map, no menu is
open (`menuOpen` is cached volatile from `ClientTick`), and the point is
inside the map rect and **not** inside a hole. Its rules:

- **Wheel:** zoom, consume.
- **Left press:**
  - on a marker, button, card or portal: do not consume. The game runs the
    top menu entry, which is our RUNELITE entry. The game runs the menu it
    built at the previous frame's mouse position, so the press is let through
    only when `onPostMenuSort` built that menu for this same hit (a stable
    key of kind, id and ring). Otherwise it is swallowed and the player
    clicks again a frame later, so no stale game entry ("Walk here" from just
    outside the map, "Confirm" from the Teleport hole) can run.
  - on empty map: start a drag and consume the press.
- **Drag in progress:** pan. Dragged events are passed on unconsumed: the game
  took no press, so it treats them as plain moves and its mouse position
  (hover, the info card, the next menu) follows the pointer. Released and
  clicked events are consumed, even outside the bounds, until release.
- **Right press:** do not consume. The game opens the menu we built.
- **`mouseMoved`:** never consumed.

**Threading.** The input thread (AWT) touches only volatile or atomic view
state. All widget and var access happens on the client thread. Overlays and
events already run there, and `MenuEntry.onClick` runs there too.

**`onPostMenuSort`** (client thread; skip when `client.isMenuOpen()`). When
the mouse is inside the map rect and not inside a hole:

1. Remove every menu entry except CANCEL. This removes "Walk here", the
   hidden rotate zones and examine options.
2. Add our entries:
   - On a marker: `Select` (left-click), `Zoom to`, `Clear selection` (if
     there is a selection).
   - On a button, card or portal: its action.
   - On empty map: `Clear selection` if there is a selection, otherwise
     nothing.

   Targets are coloured with `ColorUtil.wrapWithColorTag(..., JagexColors.MENU_TARGET)`.

In Dial mode, only the floating "Map" button owns its rect. Everything else
passes through.

### 4.8 Rendering (`AtlasOverlay`, `MapRenderer`, `TileStore`)

**Overlay settings.** `setPosition(DYNAMIC)`, `setLayer(ABOVE_WIDGETS)` and
`setPriority(PRIORITY_HIGHEST)`. Do not use `ALWAYS_ON_TOP`: it would cover the
right-click menu and tooltips. `render` draws in absolute canvas coordinates
and **returns null**. Keep our own published `Rectangle` for hit tests.

**Base map cache.** Keep an opaque `TYPE_INT_RGB` image the size of the map
rect.
- Rebuild it only when the view key changes: layer, centre, ppt, size, or the
  tile-availability generation counter.
- Blit it 1:1 every frame.
- Draw the following on top each frame: labels, icons, markers, highlight
  rectangles, cards, the toolbar and the hole frames.

**Level choice.** For target ppt `p`, use the bundled or derived level `z`
with the smallest `2^z ≥ p`. Draw each tile scaled by `p/2^z`:
- BILINEAR when downscaling;
- NEAREST_NEIGHBOR when upscaling past 4 ppt (crisp pixel art).

If that level's tile isn't ready yet, draw a coarser available level, scaled
up, so the map fills in progressively, then any cached finer tiles (one or two
levels finer, left over from before a zoom out) over it. Never block the
client thread on decoding. An incomplete base map is rebuilt every 250 ms as
a safety net.

**`TileStore`.**
- It loads PNGs with `getResourceAsStream` + `ImageIO.read`, off the client
  thread on the injected `ScheduledExecutorService`, or a single-thread
  executor of our own that is shut down in `shutDown`.
- It keeps an LRU of decoded tiles, bounded by about 48 MB (one 256² INT_RGB
  tile is 256 KB). The cap is soft: tiles drawn by the last two viewport
  rebuilds are never evicted, so a view that needs more (2000x1400 just above
  a level boundary) holds its tiles instead of evicting and re-decoding them
  for ever.
- Its one worker serves the newest request first, and drops a request the
  latest rebuild did not repeat (every rebuild asks again for each tile it
  still shows), so the current view never waits behind views already left.
- About 30 s (50 ticks) after 398 closes, and at the login screen, the LRU,
  the base-map cache, the chrome layer and the text sprites are released.
- It derives levels between bundled levels by area-averaging 2×2 blocks
  (z=1 and z=0 from z=2, z=-2 and z=-3 from z=-1). Derived tiles are cached in
  the same LRU.
- A tile that is not shipped is drawn as its `solid` colour or the layer
  background.
- A `generation` counter increments whenever a tile finishes decoding, so the
  renderer knows to rebuild the cache.

**Text.** Use `FontManager.getRunescapeSmallFont()` and
`getRunescapeBoldFont()`, with antialiasing off for those bitmap fonts.

**Performance.** Allocate nothing per frame in hot paths. Precompute label
layouts per view key.

**Stretched mode.** The canvas is scaled after drawing, so the map can be no
sharper than the game resolution. This is a known limit. Document it; do not
fight it.

### 4.9 Ring data at runtime (`RingRepository`)

On startup, load `rings.json` and `map/index.json` with Gson. Use the
injected `Gson`, because the old RuneLite Gson has no
`JsonParser.parseString`.

On the first open of 398, on the client thread:
1. Read DB table 89 and update each ring:
   - x, y and plane from `COL_DEST_COORD`;
   - the log-row widget id from `COL_TEXT_COMPONENT`.
2. Add any code that the table has and the JSON lacks, using `COL_DESC` as
   its name and the surface layer if its coordinate is inside the surface
   bounds.
3. If a DB read fails, fall back to the JSON alone.

**Per tick while open:**
- `visited` comes from `logVarbit`. Additionally, treat a non-empty log row
  text as visited.
- Favourites come from the 10 `FAIRYRING_FAVE_n` varbits (decode `set` and the
  dial values) and from the `FAVE_CODE_n` texts as a fallback.
- The last destination comes from 5374/20250.

**Search matching.** Case-insensitive substring on the code (with or without
spaces), name, area, POIs, tags and description words. When the code row also
carries user tags from the core plugin, ignore that: we can only match our own
data.

### 4.10 Config (`FairyRingAtlasConfig`)

Group `fairyringatlas`. Sections: Map, Markers, Travel log, Advanced.

| Key | Type | Default |
|---|---|---|
| `openInMapMode` | boolean | true |
| `maxWidth` | int px | 1100, range 512–2000 |
| `maxHeight` | int px | 720, range 334–1400 |
| `startView` | enum FIT_ALL / AROUND_YOU / REMEMBER | FIT_ALL |
| `codeLabels` | enum ALL / FAVOURITES / NONE | ALL |
| `placeLabels` | boolean | true |
| `mapIcons` | boolean | true |
| `dimUnvisited` | boolean | false; when true, unvisited markers are drawn at 50% alpha |
| `autoFitSearch` | boolean | true |
| `filterTravelLog` | boolean | true |
| `dialGuidance` | boolean | true |
| `clueHelper` | boolean | true |
| `visitedColor` | Color | `#3FD9C8` |
| `selectedColor` | Color | `#FF981F` |
| `favouriteColor` | Color | `#FFD700` |

### 4.11 Coexistence with other plugins

**Core Fairy Rings** rewrites the CONFIRM text and adds "Edit Tags" entries.
That is fine: we never touch CONFIRM, and our `PostMenuSort` only strips
entries under the map, never on the log.

**Fairy Ring Organizer** may re-sort rows. Our row edits are re-applied after
8080 and restored exactly.

**Fairy Ring Map** (jdkrupajk) draws its own widget map over the dials. If its
plugin is enabled, the two would fight.
- Detect it by `PluginManager.getPlugins()`, comparing the class name, or by
  its `"fairyringmap:layer"` widget tag being present.
- Show a one-line notice in the top bar: "Fairy Ring Map is also enabled —
  disable one of them".

---

## 5. Code structure

`src/main/java/com/fairyringatlas/`:

| File | Responsibility |
|---|---|
| `FairyRingAtlasPlugin` | Lifecycle, event subscriptions, mode state, selection state, menu ownership |
| `FairyRingAtlasConfig` | Config interface |
| `Ring`, `Layer`, `Portal`, `MapIndex` | Data classes (Lombok `@Value`/`@Data` OK) |
| `RingRepository` | Loads JSON, reads the DB table, holds visited / favourite / last state |
| `DialMath` | Letter orders, code↔dial values, rotation plan. Pure; unit tested |
| `MapView` | Immutable view (layer, cx, cy, ppt, rect), world↔screen transforms, zoomAbout, fit, clamp. Pure; unit tested |
| `MapLayout` | Computes the map rect and holes from widget bounds |
| `TileStore` | Resource loading, LRU, async decode, derived levels |
| `MapRenderer` | Draws the base map into the cache image |
| `AtlasOverlay` | Draws everything; hit-testing helpers (marker, button, card at point) |
| `AtlasInput` | Mouse and wheel listener |
| `TravelLogController` | Log filtering and highlighting with exact restore |
| `DialGuideOverlay` | May live inside `AtlasOverlay`; Dial-mode guidance |
| `ClueHelper` | Optional `ClueScrollService` integration, defensive |
| `LabelPlacer` | Greedy collision-avoiding label placement. Pure; unit tested |

**Tests** in `src/test/java/com/fairyringatlas/`:
- `DialMathTest`
- `MapViewTest`
- `LabelPlacerTest`
- `RingDataTest`: loads the real `rings.json` and `index.json` and checks that
  every ring lies in its layer's bounds, that codes are unique, and that every
  index tile exists as a resource.
- `ComplianceTest`
- `FairyRingAtlasPluginTest`: the dev launcher main.

**Preview tool.** `src/test/java/.../MapPreview.java` has a `main` that renders
the map offline, without a client, at several views into
`build/preview/*.png`. Views to cover:
- full fit;
- the Lumbridge area at 4 ppt;
- 16 ppt;
- an underground layer.

Each preview draws markers, labels and cards so a human can check the
quality. This means `MapRenderer`, the marker painter and the card painter
must not depend on `Client`. Pass in plain data and fonts (fall back to a
default font when `FontManager` is unavailable).

---

## 6. Generators (`tools/`; never shipped and not part of the plugin build)

- **`tools/data/fairy_rings.verified.json`:** the research dataset.
- **`tools/build_rings.py`:** builds `rings.json` as in 3.1, deterministically.
- **`tools/mapgen/`:** a standalone Gradle project with its own
  `settings.gradle`, using `net.runelite:cache` and RuneLite's
  `MapImageDumper`.
  - It renders z=2 tiles per region (icons and labels off) for every layer's
    bounds, plus the overview level(s).
  - It exports labels and icons, writes `index.json`, and quantises and
    optimises the PNGs (Python Pillow is available).
  - It reports the total bytes.
  - It reads the OpenRS2 cache (download into `tools/mapgen/cache/`, which is
    git-ignored).
  - The README records how to regenerate it, plus the cache id and date.

---

## 7. Hub packaging

- `LICENSE` is BSD-2 (Dewdul).
- `icon.png` is 48x72 at the repo root, at most 256 KiB.
- `README.md` covers:
  - features and how to use the plugin;
  - why a map click can't teleport by itself;
  - the stretched-mode note;
  - data provenance (game cache via RuneLite's `MapImageDumper`, cache id);
  - credit to Fairy Ring Map for the idea and the approved log-filter
    technique.
- The plugin-hub PR description lists the differences from fairy-ring-map.

---

## Deviations

### Map imagery (`tools/mapgen`)

- **Off-surface layer bounds in `index.json` are tighter than 3.3.** Each is
  the bounding box of the destination's non-empty pixels plus 8 tiles, and is
  not always region-aligned. The 3.3 bounds come from wiki basemaps and also
  contain unrelated areas packed next to the destination in the cache; those
  regions are not shipped, and pixels outside a layer's bounds are painted
  with its background. Mor Ul Rek is 3x2 regions, [2368,5056]-[2560,5184]
  (the row above is empty). Every ring still lies inside its layer.
- **Surface bounds** are the surface rings' bounding box ±2 regions:
  [1152,2304]-[3776,4032]. That is one region further south than 3.3's
  y 2368, because CJQ (The Great Conch) is at y 2447.
- **No `poh` entry in `index.json` `layers`.** It has no map. The plugin
  treats `poh` as a card-only layer.
- **Labels** carry an optional `c`, the in-game text colour such as
  `#ff981f`; when it is absent the text is white. Multi-line game labels
  (`<br>`) are joined with spaces.
- **Icons** leave out the transportation icon (sprite 1504) on each fairy
  ring in the dataset, because the plugin draws its own markers.
- **Levels:** z=2 and z=-1 only. z=0 does not fit the budget.
- **PNG8 threshold:** PNG8 is used only at PSNR ≥ 40 dB, stricter than the
  38 dB example in the task.

### Ring data (`tools/build_rings.py`, `rings.json`)

- **Additive fields.** Every ring has an `id`: its code, `HIDEOUT` for the
  sequence, or `ZANARIS_EXIT_ALKHARID` / `ZANARIS_EXIT_LUMBRIDGE` for the exits.
  This gives non-code entries a stable key. The hideout also has
  `"sequence": ["AIR","DLR","DJQ","AJS"]`. Portals carry a one-line
  `description` of how to get in. The top level has an `_about` string. Gson
  ignores all of these unless a class declares them.
- **Hideout `logVarbit` is 4026** (`FAIRYRINGS_LOG_HIDEOUT`) rather than -1,
  because a log varbit exists for it. The exits have -1.
- **Display text comes from a curated file,
  `tools/data/ring_display.json`, not from the dataset.** The dataset's own
  name, description, poi and notes fields are research notes. Portals live in
  `tools/data/portals.json`. The verified dataset is copied unchanged and
  remains the source for codes, tiles, flags and varbits.
- **Text is printable ASCII only**, because the card uses RuneScape bitmap
  fonts. Requirement lines start with `Quest:`, `Skill:`, `Unlock:`, `Item:`
  or `Diary:`. Optional advice, such as a light source or a greegree, goes in
  notes. The game's `noStaffReturn` flag is not repeated in notes, so the
  plugin should render it itself.
- **Zanaris portal is at the wiki's shed pin (3204,3169)**, not (3201,3169).
  The 3.1 example is the exit ring's arrival tile, 3 tiles away.
- **No Grimstone portal.** The dungeon's cave entrance is at (2912,4066),
  north of the surface crop. **No Dorgesh-Kaan South portal**, because it has
  no single surface entrance.
- **Near a layer edge.** The hideout landing (2328,4426) is 24 tiles from
  the west edge of 3.3's Zanaris bounds and 13 tiles inside the tightened
  `index.json` bounds. Every ring is inside both.

### Plugin code (`src/main/java`)

- **More, smaller classes than section 5 lists.** The marker and label
  painter is `AtlasPainter`, the chrome (top bar, Elsewhere panel, info card,
  frame, hole frames) is `ChromePainter`, text rendering is `Ink`, and the
  per-frame data they draw from is `Scene`, with `Hit` for hit regions. None
  of them touches `Client`, so `MapPreview` renders exactly what the overlay
  draws. Dial-mode guidance lives in `AtlasOverlay`, as 5 allows.
- **Size.** The review bot counts only `src/main/java`, with comments
  stripped (riktenx on plugin-hub #17419), against its 200k-token budget.
  Ours is about 178 KB of Java, roughly 50k tokens once comments are removed, so
  `tools/`, `docs/` and the JSON resources do not matter for that budget.
  `RingDataTest.javaSourceFitsTheReviewBudget` fails when `src/main/java`
  passes 300,000 bytes, as an early warning.
- **ASCII and vector glyphs instead of the spec's Unicode symbols.** The
  RuneScape fonts have no glyphs for the arrows, check mark, "▸"/"◂" or
  the em dash. The plugin draws them as vector shapes: the rotate arrows,
  check mark, triangles and the "→" in the dial readout. Text uses "-" and
  "...".
- **Caching beyond 4.8.** Three caches keep a steady frame cheap:
  - The chrome is drawn into a translucent cached layer, rebuilt only when
    what it shows changes (selection, hover, dials, search, panel, hovered
    button).
  - Marker states and outlined labels are pre-rendered sprites.
  - Label placement is cached per view and ring state.
- **TileStore details.**
  - A tile outside every layer's bounds is drawn in the current layer's
    background.
  - Source tiles decoded only to derive a coarser level are not kept in the
    LRU. Without this, deriving z=1 for a 1100x720 view evicted its own
    results.
- **Layout.** `MapLayout` generalises 4.2's cuts. Each obstacle (chat
  container, side panel or tab bars, travel log) cuts the usable area from
  whichever side clears it and keeps the most room. A cut never goes past the
  dials' own edge, so the dials stay inside, and on a canvas too small for
  any cut to clear an obstacle, the least overlapping cut is taken. The chat
  container counts even when the chat is collapsed, so the game's search box
  is never covered.
- **Elsewhere panel.**
  - It starts collapsed when the map is narrower than 700 px (fixed mode)
    until the player toggles it.
  - It scrolls with the mouse wheel when it overflows.
  - The house card is a single chip that selects DIQ.
- **Info card.**
  - It is narrower on small maps: 45% of the map width, at least 220 px,
    and at most 62% of the map height.
  - When nothing is hovered or selected, a one-line hint shows instead.
  - Besides avoiding the holes, it avoids covering the ring it describes.
  - Rings with the game's `noStaffReturn` flag get a grey line, "No staff
    needed to leave: this ring can take you back to Zanaris", unless the
    elite Lumbridge diary is done. The flag marks a place you may leave by
    the ring without a staff; it is not a trap warning.
- **Toolbar.**
  - `+` and `-` zoom by x1.5 about the centre, animated.
  - `Clear` appears only while there is a selection.
  - `Fit` on the surface fits all surface rings.
- **Search fit** zooms in no further than 2 ppt, so a single match keeps
  some context.
- **Non-dial entries** (hideout sequence, Zanaris exits) are purple diamonds
  with an info card. Their only menu entry is "Zoom to".
- **Travel log filter** never un-hides a row the game hid. If the target row
  is hidden or empty, the filter is not applied and only the highlight
  (when visible) is drawn. Favourite rows are found from the `FAVE_CODE_n`
  texts first, then the `FAIRYRING_FAVE_n` varbit slot.
- **Fairy Ring Map detection** uses the plugin class name only, not the
  widget tag. The descriptor also declares `conflicts = "Fairy Ring Map"`, so
  RuneLite offers to disable the other plugin when either is enabled.
- **Clue helper.** A `LocationClueScroll` is read with `getLocation(null)`;
  for a hot-cold clue that is the dig spot once solved, and nothing before. A
  clue that is only a `LocationsClueScroll` (a three-step cryptic) is marked
  only when exactly one location is left, because its list holds unsolved
  steps or candidates. Clue types that need the Clue Scroll plugin instance
  are skipped. A fairy ring clue's code is taken from the first three letters
  of its text. It needs the core Clue Scroll plugin to be enabled:
  `@PluginDependency` loads it but does not turn it on.
- **DB coord column.** It was verified in the injected client 1.13.1:
  `getDBTableField(row, col, tuple)` returns the stored values for one tuple
  element, so the coord is an `Integer` packed as `plane<<28 | x<<14 | y`.
  It is decoded defensively from any `Number`.

### Integration (fixes made after the three parts were joined)

- **Fits and pans keep clear of the chrome.** `MapView.fit(..., Insets)` and
  `focusOn(..., Insets)` fit or centre in the part of the map that the top bar
  and the Elsewhere panel leave free. Before this, a full fit hid the
  Varlamore and Kourend rings under the panel, and a layer opened from the
  panel could put a destination under it (the Zanaris hideout). The fits in
  4.6 (Fit, search fit, the initial view, opening a layer, Zoom to and pan to
  a ring) all use these insets.
- **The base map draws only inside the current layer's bounds.** Off-surface
  layers share world coordinates and tiles with their neighbours. Beyond a
  layer's bounds, `TileStore` used to fill each tile with the background of
  whichever layer it met first, which put black blocks above the surface map
  at overview zooms. `MapRenderer` now clips to the layer's bounds and leaves
  the current layer's background outside them.
- **Info card.**
  - Place and code labels are kept out from under the card. The card from the
    previous frame is a label obstacle.
  - Its fill is slightly more opaque, alpha 238 instead of 225, so markers
    don't show through the text.
  - Requirements, the staff warning and dangers now come before "Nearby".
    A short card therefore cuts the places first, and the last line shown
    ends in "...".
  - On maps under 450 px tall (fixed mode), the card is capped at 48% of the
    map height instead of 62%.
- **Names on non-dial entries.** The hideout and the two Zanaris exits get a
  purple name label beside their diamond, placed after all codes. They are
  shown only when `codeLabels` is ALL.
- **The clue X is drawn above the ring markers.** Its dashed line is still
  drawn below them. Before this, a clue next to a ring was half hidden.
- **The hole frames set their own stroke.** A dashed stroke left by another
  painter made the Teleport frame dashed.
- **Previews and the icon.** The Gradle task `preview` runs `MapPreview` into
  `build/preview/`. It also writes:
  - `7-checks.png`, 8 ppt close-ups of ten rings for checking alignment;
  - `icon.png`, the hub icon: a crop of Zanaris with an orange marker.
- **`RingDataTest` no longer skips.** A missing resource now fails the test.
  The test also checks that:
  - the tiles on disk and the tiles in `index.json` match exactly;
  - every bundled image is at most 256x256 and decodes to under 950,000 bytes;
  - `src/main/resources` is at most 6.5 MiB;
  - every ring's layer is in `index.json` itself, not only in the built-in
    defaults.
- **No code label for a marker under the chrome.** When a marker sits under
  the top bar, the Elsewhere panel or the card, its code label is not drawn.
  Before, a label could point at a ring the player couldn't see.

### Review fixes

- **Travel log.** Selecting a second ring while the log is filtered restores
  the log before looking up the new row (the old filter had hidden it). On a
  log rebuild (proc 8080), the dropped records still put back the text
  colours, which 8080 never resets; otherwise our selection colour was later
  "restored" as the original.
- **Game states.** `LOADING` and `CONNECTION_LOST` no longer count as a close:
  the interfaces and our hides survive them, and a real close is still caught
  by `WidgetClosed` or the per-tick check. Before, Dial mode could come back
  with the dials hidden for good.
- **Search** is read as the game's filter reads it: lower case, cut to 30
  characters, not trimmed.
- **Info card.** The "Click a fairy ring" hint is a label obstacle and absorbs
  presses like the card. The top bar obstacle includes the Fairy Ring Map
  notice strip. Markers inside a hole get no code label. The collapsed
  Elsewhere tab is outlined (and flashes) when the search matches a ring on
  another layer. While a right-click menu is open, the card keeps describing
  the ring it was opened on.
- **Toolbar.** `+`/`-` compound from the running animation's target, and
  clicking the active Elsewhere card fits its layer again.
- **Chrome cache key** hashes the hovered hit by value (it hashed a fresh
  object's identity, which rebuilt the chrome every frame under the mouse).
- **Tiles** are decoded in memory (`MemoryCacheImageInputStream`), not through
  ImageIO's temp-file cache.
- **Shutdown** runs everything that the client thread uses (overlay caches,
  view, tile store and executor) on the client thread, after the overlay is
  removed.
- **New codes from the DB table** are named from `COL_DESC` without its tags,
  split into area and name at the colon.
- **Data.** Bosses reached from a ring are in its POIs and tags: Yama (DJR),
  the Royal Titans (AIQ), Araxxor and the Morytania Spider Cave (ALQ) and the
  Grotesque Guardians (CKS). CJQ lists every start requirement of Troubled
  Tortugans. The BLS Mountain Guide note says the lift must first be unlocked
  at the summit. `ring_display.json` may list a dataset requirement the wiki
  does not state for that ring under `unconfirmed`; BLS's Kourend visit is
  told as a note instead of an amber requirement. The Mor Ul Rek "Fight Cave"
  label is moved from the arena instance to the cave entrance north of BLP
  (`LABEL_MOVES` in `mapgen.py`).

### Favourites list (added 2026-10-03, at the user's request)

- The side panel starts with a **Favourites** section above *Elsewhere*. It lists the
  player's in-game favourites in favourites-block order (`RingRepository.favouriteRings`),
  each row a code chip plus the ring's name. A row is a `CHIP` hit with id `Hit.FAVE`.
  Left-click selects the ring and focuses it at no less than 2 ppt, switching layer when
  needed. Rows dim with the search like the cards. With no favourites, a grey line says
  to star a code in the travel log.
- The info card adds a hint line for a favourite, "1 click: right-click any fairy ring >
  Favourites > CODE", and for the last destination, "... > Last-destination (CODE)".
  These point at the game's own one-click teleports; the plugin adds nothing to the ring's
  menu.

### In-game feedback, 2026-10-03

- **Teleport and close move to the map's bottom-right corner.** In the first in-game test,
  the real Teleport button showed through a hole in the middle of the map reading "Invalid
  location" (core Fairy Rings rewrites its text, and the dials start at the unused code AIP).
  The close button also sat mid-map. The game draws and clicks 398's components only inside the
  dials' own 512x334 rectangle, so the map now grows up and left from the dials' bottom-right
  corner. Map mode moves three components into that corner and puts each back exactly when
  leaving map mode:
  - CONFIRM to (335,299) 169x27;
  - the parchment plaque behind it (ROOT_MODEL25) to (404,297);
  - the close button (ROOT_GRAPHIC27) to (478,268), just above Teleport.

  The user chose this over a map only 334 px tall, which would have kept close at the top right.

  This revises hard rule 4's "never move CONFIRM or the close button". Jagex's rules restrict
  moving click zones only for the inventory, equipment, spellbook, prayer, combat options and 3D
  components. Moving the real widget is the precedent reviewers accept: Fairy Ring Map's
  travel-log rows, and plugin-hub #16240. The ops are untouched.
- **Teleport is covered unless worth pressing.** Teleport is a hole only when the dials show
  the selected ring or, with nothing selected, a real destination (`Scene.teleportShown`).
  Otherwise the bar draws a disabled "Teleport" in its place and owns its clicks, so a
  stale or invalid code can't be used by mistake.
- **Open at your ring** (`openAt`, default AROUND_YOU): the map opens centred on the ring
  you are standing at, or on the area you are in.
- **No mouse-over text over the map.** While the map shows, the game's top-left mouse-over
  text ("Select CKP ...", "Cancel") is turned off with `client.setMouseoverTextEnabled(false)`,
  because it overlapped the map's title and the map has its own hover card. It is turned back
  on (only if we turned it off) when the map closes, in Dial mode, on logout and on shutdown.
  This is display-only and sends nothing to the game.
