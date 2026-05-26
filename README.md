# Bed Back Better
Never worry about your friend breaking your bed! Your last 5 valid spawn points (configurable) are now recorded so that, even if your bed breaks, you've always got a (hopefully) nearby bed to fallback to.

A set-it-and-forget-it, vanilla-ish Paper plugin for Minecraft 1.21.5+ that lets you fallback to previous beds and respawn anchors.

Tested to work on Paper 1.21.5 and 26.1.2.

Idea stolen from [this Reddit post](https://old.reddit.com/r/admincraft/comments/1tbfibr/use_last_5respawnpoints/).

**See also:**
- For (Neo)Forge: https://modrinth.com/mod/back-up-beds
- For Fabric: https://modrinth.com/mod/bedfallback

## Features
- **Keep a buffer of your spawns!** On death, you have a much bigger buffer (default: last 5 valid spawn locations) against world spawn stranding - now you get not just one, but five (configurable) respawn points!
- **Never fret on death with automatic fallback!** Broke your current bed? Don't worry! You'll just spawn at the previous bed you slept at!
- **Respawn across dimensions!** If your bed breaks in the Overworld, you can fallback to a respawn anchor in the Nether and vice versa!

## FAQ
- **How do I use this?**
  - Right-click a bed. Right-click another bed, then break it. Die. Now you spawn at the first bed!
- **Does it work across dimensions?**
  - By default, yes! If your bed breaks in the Overworld, you can respawn at a respawn anchor in the Nether, provided that it is (1) charged, (2) not obstructed, and (3) your plugin configuration allows cross-dimension travel!
- **What happens if I break a bed and place it in the same place?**
  - The plugin follows vanilla mechanics closely (as long as the Minecraft Wiki was right for [bed logic](https://minecraft.wiki/w/Bed#Setting_the_spawn_point) and [respawn anchor logic](https://minecraft.wiki/w/Respawn_Anchor#Respawning)!). As long as you replace the exact block where the former head of the bed was with the new bed, you should be able to fallback to that bed!
- **Is it the 'last N valid beds that I slept in' or 'last N beds total' (including broken ones)?**
  - By default, it is the last N valid spawn points at the time of respawn. You can change it to be plain N spawn points in the config.
- **Does it keep tracking after server shutdown?**
  - Duh, of course! It'd be pretty useless otherwise!
- **How vanilla-ish is this plugin?**
  - Pretty vanilla. Spawn points are only considered at respawn time. If a spawn point is invalid at respawn time, it is deleted from memory (like in vanilla). The plugin will continue to drop records until it finds a valid spawn point, at respawn time.
- **What happens if I just never die?**
  - Then you don't get to take advantage of this plugin!
- **Did you use AI?**
  - Yeah, I definitely did - mostly for the bed and respawn anchor validity (because geometry is a pain!). Don't fret, the actual respawn logic was written and tested by me, so if anything is wrong there, it's because I, the faulty human, overlooked it. Take solace knowing that whatever bug you encountered is my fault and not a machine's!
- **Can you make this for below 1.21.5?**
  - Maybe if you can tell me the alternative to the function call `isMissingRespawnBlock()` that was added in [this commit](https://github.com/PaperMC/Paper/pull/12422) in Paper 1.21.5. I don't want to work around that.

## Configuration options
```yaml
# How many previous spawn points to consider when finding a fallback respawn.
fallback-candidates: 5

# Maximum spawn points to store per player. Oldest are dropped when exceeded.
# Set to 0 for unlimited.
max-stored-spawns: 100

# Behavior on fallback:
# - LAST_N: Consider only the N most recent spawn points (incl. invalid)
# - LAST_N_VALID: Consider only the N most recent *valid* spawn points
# - LAST_N_IN_DIMENSION: Consider only the N most recent spawn points in that dimension (incl. invalid)
# - LAST_N_VALID_IN_DIMENSION: Consider only the N most recent *valid* spawn points in that dimension
# Note: this policy affects what records are safe to clean up at respawn time, independent of max-stored-spawns.
# For example, LAST_N_VALID will drop all records, both valid and invalid past the Nth valid spawn point it finds.
fallback-policy: LAST_N_VALID

# How often to autosave spawn records (in minutes). Set to 0 to never autosave.
autosave-interval: 5

# What to say to players when they fall back to a new location. Set to "" to omit a message.
fallback-respawn-message: "Your respawn was updated to a new location"

# Enable debug functionality (server logs when spawn changes, record changes, etc.)
debug-mode: false
```

## Command usage
This plugin does not use any commands.

## How do I tell you that your plugin is broken?

Report issues with my spaghetti code through the [issue tracker](https://github.com/ganyuke/BedBackBetter/issues) on the [plugin's GitHub repository](https://github.com/ganyuke/BedBackBetter/).

## How do I build this plugin myself?

1. Clone this repository: `git clone https://github.com/ganyuke/BedBackBetter.git`
2. Change directory: `cd BedBackBetter`
3. Build the `.jar`: `./gradlew build`
4. Find the built `.jar` in `build/libs/`

## How do I submit features for this plugin?

If you wrote the code for it, I'll happily accept a pull request if I feel like it doesn't bloat the scope of this plugin. Ensure that you use the [pull request template](https://raw.githubusercontent.com/ganyuke/BedBackBetter/refs/heads/main/.github/pull_request_template.md).

By submitting a contribution to this repository, you agree that your contribution is licensed under the same license as this repository, as published in the [`LICENSE`](https://raw.githubusercontent.com/ganyuke/BedBackBetter/refs/heads/main/LICENSE) file.

## License
Unless otherwise noted, all source code in this repository is licensed under the **Mozilla Public License 2.0** (SPDX: **MPL-2.0**). Please view the [`LICENSE`](https://raw.githubusercontent.com/ganyuke/BedBackBetter/refs/heads/main/LICENSE) file for the terms you are afforded under the MPL-2.0.
