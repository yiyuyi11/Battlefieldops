# 656 kill-icon full port

## Decisions
- Port faithfully from decompiled `gd656killicon 1.3.3` in `.ref-killicon/` (197 files, 2.1 MB).
- Chosen scope: kill icons only, but FULL depth: 7 icon modes + 6 subtitle renderers + 37 presets
  + 177 textures + 87 sounds + honour/bonus engine (49 bonuses). Other 7 mods deferred.

## Assets moved in (from gd656killicon jar)
- 177 textures -> `assets/battlefield_ops/icons/` (real naming scheme, `honor/` subdir keeps 49 badges)
- 86 ogg -> `assets/battlefield_ops/sounds/kill/`
- 37 presets -> `assets/battlefield_ops/presets/official/`
- 4 formats, 4 lang -> `assets/battlefield_ops/formats/`, `.../lang/killicon/`

## Ported so far
- `KillType.java` - 11 kinds. NOTE pack bug: `destroy_vehicle` stem is `destroyvehicle` on disk.
  `textureCandidates()` gives ordered fallbacks because pack lacks `scrolling_medic`,
  `scrolling_vehicle_destroy_assist`, `scrolling_capture` (verified: 177 files).
- `ScreenAnchor.java` - 9 anchors; y_offset grows UP (subtracted).
- `KillIconScroll.java` - scrolling mode model: pending queue, target layout (pinned vs centred),
  quadratic position easing, cubic scale ease-out, blink fade 3-segment curve, forced fade on overflow.
  All pure math, clock injected.
- `KillIconPresets.java` - parses 37 presets. THREE schema violations in shipped data:
  1. `scroll_direction` is STRING "right" => -1 (not +1)
  2. `max_visible_icons` is FLOAT 5.0 (Gson getAsInt would throw)
  3. presets 00005/00006 have NO kill_icon mode (subtitle-only) - must not be dropped
- `sim/KillIconPortSim.java` - 326 checks against the real preset corpus. PASSES.

## Pack data bug fixed
- `killicon_df_medic.png.png` (doubled extension) renamed to `killicon_df_medic.png`.

## Status
- Build OK. Suite green: 38 sims / 3839 checks.
- Mode counts: 6 scrolling, 29 other-mode, 2 subtitle-only = 37.

## Still to do
- KillIconHud renderer (draw the strip), honour/bonus engine, 87 sounds wiring,
  other 6 icon modes, config GUI/preset picker, docs, install.

## Update: foundation + HUD + awards + sounds all landed
- `KillIconHud.java` - draws the strip (blit only; all logic stays in the model)
- `AwardCatalog.java` - 72 awards, score caps, merge behaviours, killType projection
- 86 kill sounds registered as `kill.*` events; sounds.json 120 -> 206; SoundCatalog regenerated
- `regen-sound-catalog.py` - regenerate the Java catalogue without a full asset import
- import-656-assets.py now imports kill sounds as a real, idempotent step

## Bugs found in the PACK (not our code) - all handled and documented
1. `killicon_df_medic.png.png` doubled extension -> renamed to `.png`
2. `scroll_direction` is the STRING "right" meaning -1
3. `max_visible_icons` is the FLOAT 5.0 (Gson getAsInt throws on it)
4. presets 00005/00006 have no kill_icon mode at all (subtitle-only)
5. `scrolling_medic` / `scrolling_vehicle_destroy_assist` / `scrolling_capture` do not exist ->
   explicit fallback chains in `KillType.textureCandidates`
6. honour badges are `honor_<lowercase>.png` keyed by `format_*`, NOT by the 72 award ids.
   Only AVENGE collides with an award id.

## Tests
40 sims / 4716 checks, exit 0. New: KillIconPortSim 326, KillIconHudSim 150,
AwardCatalogSim 722, SoundBankSim updated to 206 events.

## Still to do for the kill-icon goal
- wire kill triggers to actual gameplay (award events -> hud.trigger + sound cue)
- the other 6 icon modes (combo, card, card_bar, battlefield1, valorant, honor) renderers
- 6 subtitle renderers
- preset picker in the UI + ClientPrefs persistence
- remove the old KillIcon.java / KillIconRenderer.java (still referenced by AwardHud)
- docs + install

## Update 2: old layer deleted, new layer wired end to end
Deleted: KillIcon.java (16.4 KB), KillIconRenderer.java (8.4 KB). Kept the one still-real piece
(the Valorant finish axis) as KillIconFinishes.java; rewrote KillIconSim against what survives.

New: KillIcons (award->KillType map + texture/bitmap fallback), KillIconStore (preset corpus loader +
live strip owner), ValorantPresets (26 preset -> 10 used skins table).

Wired: award payload -> KillIconStore.trigger -> animated strip + mode/finish-appropriate stinger.
F9 now cycles the 37 real presets by their pack names. HUD draws the strip after the text feed and
skips it under a vehicle optic.

## Real bug found in my own model: presets are MULTI-ELEMENT
`Preset` kept only the first kill_icon/* key, so 00003's `card_bar` and 00037's `honor` were being
silently dropped. Now `Preset.elements` keeps every configured element and `element(key)/has(key)`
read them. Element census (verified against all 37 files):
  kill_icon/scrolling 6, valorant 26, card 1, card_bar 1, combo 1, battlefield1 1, honor 1
  subtitle/kill_feed 7, bonus_list 9, score 9, hit_info 1, combo 1
So the goal's "7 icon modes" is right (card_bar is separate from card; honor is a second element on
00037, not a preset of its own).

## Texture chain ordering bug (real, fixed)
When a kind's own art IS the family default (NORMAL everywhere; ASSIST in battlefield1; CAPTURE in
battlefield1), the default landed in slot 0 and the chain ended on something more specific than the
default. Now the family default is forced to the LAST slot, which also promotes the `df` stand-in
ahead of it so a capture draws capture art instead of a generic icon.

## Valorant skin table bug (real, fixed)
Presets 15/16/17 were filed under `gaia`; their own display_name says "Gaia's Vengeance". Fixed, and
the test now DERIVES the expected skin from display_name instead of trusting the table.
26 presets -> 10 used skins; the pack ships 11 sound sets because `gaia` has audio but no preset.

## Tests
41 sims / 5565 checks, exit 0.

## Remaining for the goal
- renderers for card, card_bar, combo, valorant, honor (they currently borrow battlefield6 art)
- the 6 subtitle renderers (kill_feed, bonus_list, score, hit_info, combo + honor text)
- preset picker / config GUI
- docs + install

## Update 3: all 7 icon modes, 6 subtitle renderers, translations fixed
New: ModeLayout (shared layout/time reads, tolerant of the pack's real JSON shapes), KillIconModes
(card, card_bar, combo, valorant, honor), KillIconSubtitles (kill_feed, bonus_list, score,
hit_info, combo, honour hint box), ValorantPresets, KillIconStore.

KillIconHud now dispatches over EVERY configured kill_icon/* element, not a single "primary mode".
That matters: 00037 configures scrolling AND honor (its badge row was invisible before), and 00003
configures card AND card_bar (a light bar with nothing above it before).

## Three more real defects fixed
1. Duplicate 7.5 MB texture tree: `textures/killicon/` was a byte-identical copy of `icons/` (177/177
   files, verified by sha256). No code referenced it. Removed; importer now writes only `icons/`.
2. lang files were extracted to `lang/killicon/*.json` -- a path Minecraft never loads, so ALL 1,577
   kill-icon translation keys were dead. Merged into `lang/{en_us,zh_cn,zh_tw}.json` with the
   namespace rewritten gd656killicon.* -> battlefield_ops.*. zh_tw created (did not exist before).
3. `AwardCatalog` built keys as `battlefield_ops.award.<ID>.name`, but the pack's keys are
   `battlefield_ops.bonus.<ID>.name`. Every one of the 72 awards would have rendered as its raw key.
   Also added the 86 missing sound subtitles and relabelled the F9 keybind (said "style", now "preset").

## Tests
43 sims / 10,955 checks, exit 0. New: ModeLayoutSim 103, LangSim 5,287, PresetCorpusSim 558.
jar 22.8 MB: 202 classes, 177 icons, 206 sounds, 37 presets, 4 formats, 3 locales.

## Remaining for the goal
- preset picker / in-game config GUI (F9 cycles; there is no list UI yet)
- honour badge row only draws for kinds that happen to be titles; needs award->title wiring
- docs (README, 使用说明, 更新说明-0.7.x) + install with hash verify
- the 6 deferred addon mods (user chose kill icons first)

## Update 4: picker UI, honour catalogue, docs, install (0.7.1)

### Shipped
- `KillIconPickerScreen` — F8 opens a searchable list of all 37 presets. Search matches display
  name, preset id and mode key. Choosing writes the pref and rebuilds the live strip immediately.
  F9 still cycles, because the two answer different questions ("next look" vs "show me all of them").
- `HonourCatalog` — the 49 honour titles as a real table (id, streak/conditional, the reference's own
  condition string, score). `KillType.honourTitles()` now delegates to it instead of holding a second
  hand-written copy that could drift.
- Docs: `更新说明-0.7.1.md` (new), README and 使用说明 updated — the guide gained a full
  "9.8 击杀图标与击杀音效" section and chapter 12 now cross-references it, because the mod has two
  separate things called "kill card" and conflating them was a real reader trap.
- Bumped to 0.7.1 (build.py VERSION + neoforge.mods.toml).

### Installed
`E:\...\Battlefield656\mods\battlefield-ops-0.7.1-neoforge-1.21.1.jar`
sha256 B5B623E72B723CF60D2D58E500C521BCDA157451B7B9369B0961C8A929BE0CB9, source and dest identical.
Verified contents: 205 classes, 177 icons (128 kill + 49 honour), 86 kill sounds, 206 total sound
events, 37 presets, 4 formats, 3 locales. No duplicate texture tree, no nested lang dir.

### Tests
44 sims / 11,455 checks, exit 0. New this round: LangSim 5,287, HonourCatalogSim 497.

### Still open
- The 86 kill sounds and 177 textures are verified as *reachable*, not as *good*: nobody has looked at
  a 2048px valorant texture scaled to 19px, or listened to the 86 stings mixed together.
- 6 addon mods deferred by the user's own choice (frontvoice, lightcore, motionhud, peek,
  taczcrawtweak, taczculling).
- `AwardCatalog` caps are still all 0f and `priorityKill` all false — the real values live in
  `BonusDefinitionsArea` and have not been read across yet.

## Update 5: award table rebuilt from the reference (a real self-correction)

Writing the docs exposed a hole: `AwardCatalog`'s caps were all `0f` and `priorityKill` all `false`.
Reading `BonusDefinitionsArea` + `BonusDefinition.Builder` showed the earlier table was not merely
incomplete, it was invented:

| field | reference default | overridden by | what the old table had |
|---|---|---|---|
| mergeBehavior | BY_TYPE_EXTRA | 1 (KILL_COMBO -> BY_COMBO) | three invented values spread over 72 |
| scoreCap | -1 (uncapped) | 1 (VALUE_TARGET_DESTROYED -> 2000) | all 0 |
| factor | NONE | 12 | not modelled at all |
| statCategory | NONE | 6 | not modelled at all |
| priorityKill | false | 6 | all false |

The reference's `MergeBehavior` has exactly TWO members, not the three I had written. `gen-awards.py` now
parses the decompiled source and emits the 72 rows, so the table cannot drift from the source again.

Also learned while reading `BonusEngine`: only 4 of 72 bonuses produce a kill-feed line (2 with an
`iconKillType`, 2 with a fixed `killFeedPayload`); the other 68 are pure score feedback. And
`scoreExpression` is a configurable *string* the server overrides per player, not a formula.

`AwardCatalogSim` rewritten for the real model (1,050 checks), included `declaredFields`.

## Final state
44 sims / 11,783 checks, exit 0. Build OK.
Installed `battlefield-ops-0.7.1-neoforge-1.21.1.jar` (23,880,054 bytes), sha256
AA2CA57B64F4533BB0FBFB2223EB2BD5D14BFC0775F004D6A8F295D956D7DF53, source and dest identical.
Docs: 更新说明-0.7.1.md (with the merge/cap correction written down), README, 使用说明 9.8.

## Update 6: two count corrections found by auditing against the objective

1. **lzh locale was missing.** The reference ships FOUR locales (en_us / zh_cn / zh_tw / lzh); the port had
   three, because the merge step enumerated the locales it expected rather than the ones present. Added
   `lang/lzh.json` (1,670 keys, 1,563 from the reference's Literary Chinese, 107 carried from zh_cn).
   The reference has 11 `null` values in its lzh file -- those fall back to zh_cn rather than shipping nulls,
   which would break the loader rather than the screen.

2. **"6 subtitle renderers" was wrong; it is 5.** The number came from counting the six keys in
   `formats/*.json` and assuming they were all subtitles -- one is `kill_icon/honor`. The reference's own
   manifest says 1 icon + 5 subtitle. The seven combo streak strings are a field of `bonus.KILL_COMBO`,
   not a sixth element. Docs corrected in all three files.

   Added `FormatManifestSim` (41 checks) so this cannot recur: it reads the real `formats/*.json` and
   asserts the renderer's `KillIconSubtitles.supported()` set matches it exactly, that all four format
   locales declare the same 6 keys, and that all 12 element labels resolve.

## Final verified state
45 sims / 13,511 checks, exit 0.
jar 23,911,733 bytes, sha256 78DB6E91CFF743361870AECE43A3EB21377309B6DF3B536F9631C067C5ACE11F,
installed to the pack with source/dest hash match.

Objective audit against the built jar:
  7 icon modes (scrolling 6, valorant 26, card 1, card_bar 1, combo 1, battlefield1 1, honor 1)
  5 subtitle kinds, exactly the reference's formats manifest
  37 presets, none missing vs the reference
  177 textures (128 kill + 49 honour); only diff = the reference's `killicon_df_medic.png.png` typo, fixed
  86 kill sounds, none missing, none extra, all 86 registered in sounds.json
  72 bonuses (144 keys) + 7 combo streak keys + 49 honours (98 keys)
  4 locales; 206 sound events total

## Honest limits (do not claim these)
Nobody has run this in game. The tests prove paths resolve, tables match the reference, and pure functions
behave; they prove nothing about how a 2048px Valorant texture looks at 19px, how the scroll animation
feels, or whether the 86 stings balance against each other in play.


## Update 7: loading crash fixed; conquest/crawl port continued (0.7.2, 2026-10-26)

The attached crash is a cascade: latest.log first rejects battlefield-ops-0.7.1 because
neoforge.mods.toml begins with UTF-8 BOM (Invalid bare key: modLoader), then Sodium fails
its late config registration in a broken mod state. Fixed metadata encoding; build now
rejects BOM and parses TOML before compilation. PackagingSim uses the real NightConfig parser.

Crawl implementation translated from local gd656taczcrawtweak 0.0.7 using the actual local
MCP+Mojang 1.20.1 maps, adapted to NeoForge 1.21.1 and installed TaCZ hotfix-r1.
Includes head/feet parts, collision/rotation/entry recovery, mouse damping, crawl state
hooks, original-key replacement, client/server transition shooting guards. Guards exempt
downed players and do not block an initial standing player. New server config registered.

Conquest: 140-tick combat lock for teammate deployment; distinguish dynamic deployment
glyphs by server target kind. More reference classes inspected in .ref-conquest. Full
conquest parity remains unfinished; see 更新说明-0.7.2.md for precise scope and limits.

62 simulation programs pass; new CrawlPortSim 29, CrawlMixinAuditSim 20 installed-class
signature checks, PackagingSim parses actual jar metadata and inventories. Final build
rechecked after cache cleanup. No runtime injection or gameplay validation claimed.


## Update 8: full Conquest inventory and support gameplay (0.8.0)

Full reference decompiled into .ref-conquest-full (630 top-level sources; earlier class counts
included nested classes). Machine index conquest-port-inventory.json records paths/hashes and
adaptation evidence, not a completion percentage. Full port remains unfinished.

This batch: server-authored vanilla-carrier equipment for ammo/medical packs and boxes, radio
beacon and ground sensor. Kit editor shortcut + searchable virtual item entries. Box uses=16,
per-recipient CD=600 ticks, pack CD=100; sensor scan=80 ticks, radius=10, mark=40 ticks.
Beacon consumes configured equipment and six successful deployments; reload restores dedicated
server behaviour. Team/map checks, cleanup, reward throttling; support bonuses exclude kill-icon
trigger. Registered existing cues wired with user audio switches; sensor audio is positional.
HUD/minimap/big map show friendly devices. Mark lifetime now decrements between snapshots.

Vanilla carriers preserve optional-client intent; original Geo models are NOT ported.
Five native HUD sprites + generated sensor glyph. Full remaining modules and deviations are
explicit in Conquest全量移植进度.md / 更新说明-0.8.0.md. No in-game validation claimed.


## Update 9: runtime annotation schema repair (0.8.1)

0.8.0 was compiled against cached Mixin 0.17.4, whereas the launch profile uses
0.15.2. Redirect.at/ModifyVariable.at array encoding broke MixinExtras 0.5.3
FactoryRedirectWrapper preApply with ArrayList-to-AnnotationNode ClassCastException.
Build now resolves the exact profile Mixin and excludes other cached Mixin jars.
All 65 simulation programs pass. New schema regression invokes the real NeoForge
MixinExtras transformer and reproduces the old array crash. This is a transformer
prepass test, not full runtime application or in-game verification.


## Update 10: team deathmatch core (0.8.2)

Adapted DeathmatchGameplayRuntime target score, timeout/draw and once-per-team milestone/
lead reversal semantics into per-room Rules state. Engine's existing claimKill path pays
team kills at knockdown per user requirements, never again at bleed-out. Separate teamScores
snapshot and shared MatchScore semantics feed combat HUD, held scoreboard, stats and results.
Editor supports mode/target, zero objectives, both team spawns and activity zones. Retained
objectives are inactive for capture/deploy/world map. No-objective world marker early return
fixed; mode changes invalidate map bake. Bots pursue enemies instead of inactive flags.

All 66 simulation programs pass, with DeathmatchSim's 36 checks covering rule completion,
score freeze, final conquest ticket personal credit, persistence, room state separation,
spawn filtering, zones and HUD semantics. MixinExtras runtime schema regression retained.
This is not complete deathmatch parity: dedicated initial countdown, automatic balancing
window (300 sec / 75 percent), 90 percent music and in-game validation remain outstanding.
See 更新说明-0.8.2.md. Gunmaster, Rush and other full-port modules remain unfinished.


## Update 11: complete interrupted marker wiring (0.8.3, 2026-10-27)

Native CaptureDiamondRenderer, TacticalMarkerRenderer, ClientCrosshairFadeSupport and
ClientWeaponAimCompat adapted to 1.21.1 vertex APIs. DeployIcon is a thin renderer adapter,
not reimplemented geometry. Viewer-relative progress conversion fixes reversed neutral
capture sectors. Finished deploy yaw/progress routing and deduplicated objective/dynamic
site markers. World persistent friend/class/down markers and support props use original
marker pipeline; class masks keep original alpha but blue/cyan user palette.

Reference crosshair curve and 0.12s fade; 14-tick entry, scoped base/objective fade, room/side
animation cleanup; native HUD queue sizes/alpha/0.18s interpolation (explicit +8px vertical
adaptation to current ticket bar, wrapping for 24 flags). Fixed subtitle-only kill stats,
combo-window override, death reset hook, hit victim ID/fractional damage and vehicle cosmetic
icon type. Existing optional-client/server contract and non-player zero-score policy retained.

68 simulation programs pass. NativeMarkerSim: 6287 geometry/perspective/alpha/layout/lifecycle/
asset checks. NativeHudWiringSim: 15 compiled-class wiring checks. Existing runtime MixinExtras
schema regression retained. No Minecraft/GPU or all-37-preset frame-by-frame parity claimed.
Previous handover's all-elements-complete assertion was not accepted as evidence. Other
Conquest modules, all honour/gameplay evidence and complete map visual parity remain open.
