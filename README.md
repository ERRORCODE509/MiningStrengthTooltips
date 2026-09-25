# Mining Strength Tooltips (Starsector 0.98a)

Shows each weapon's Nexerelin mining strength in two places, both on by default:

- **Stat block:** a highlighted **Mining strength: 10** line in the ancillary section.
- **Description:** a closing paragraph, **Mining strength: 10**.

Both appear wherever the game shows weapon info: the refit screen, cargo and market tooltips,
and the codex. Both texts can be changed or translated in `data/strings/strings.json`.

## Settings
Either display can be turned off:

- **With [LunaLib](https://github.com/Lukas22041/LunaLib) enabled:** use its in-game settings menu
  (F2 in the campaign, or "Mod Settings" when starting a new game) under *Mining Strength Tooltips*.
  LunaLib is optional.
- **Without LunaLib:** edit `data/config/miningtooltips.json`.

**Either way, a change only takes effect after you restart the game.** The text is added once
at startup. The in-game menu says this too.

- Reads the same data Nexerelin does: `mining_weapon_strengths` in the merged
  `data/config/modSettings.json`, plus the legacy `data/config/exerelin/mining_weapons.csv`.
  Weapons added by other mods through either file are picked up automatically.
- Weapons Nexerelin marks as hidden (`mining_hidden_ships_and_weapons`) are left alone.
- The number is the weapon's base strength. In the campaign, each ship's total is also scaled
  by CR (CR / 70%), and fleet output depends on heavy machinery.
- Compatible with other mods that change weapon text. Both lines are appended at startup,
  after every mod's `weapon_data.csv` and `descriptions.csv` have been merged, so replacement
  descriptions and translations are kept. The only exception is a mod that overwrites a mining
  weapon's text from its own code *after* this mod runs. That would remove the mining line but
  wouldn't break anything.
- No hard dependencies. Without Nexerelin enabled it does nothing.
- Utility mod: safe to add to or remove from an existing save.

## Install
Copy the `MiningStrengthTooltips` folder into `Starsector/mods/` and enable it in the launcher.
The script is compiled by the game at startup, so there is no jar.
