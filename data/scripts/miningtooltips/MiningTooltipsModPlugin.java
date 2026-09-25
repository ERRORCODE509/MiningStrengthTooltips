package data.scripts.miningtooltips;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adds a "Mining strength: X" line to the ancillary section of the weapon stat block, and/or
 * a paragraph at the end of the weapon's description, for every weapon Nexerelin treats as a
 * mining tool. Which of the two is shown comes from LunaLib's in-game settings menu when LunaLib
 * is enabled, otherwise from data/config/miningtooltips.json. Applied once at startup, so a
 * change needs a restart.
 *
 * Both are appended at runtime to whatever text is loaded at that point, so descriptions and
 * stat text replaced by other mods' CSVs (including translations) are kept intact.
 *
 * Strengths are read the same way Nexerelin's MiningHelperLegacy reads them: the legacy
 * merged CSV first, then the "nexerelin" section of the merged data/config/modSettings.json
 * (which overrides the CSV). Nothing here links against Nexerelin or MagicLib, so the
 * plugin loads fine with either disabled.
 *
 * Kept Janino-friendly (loose script): no lambdas, no varargs helpers, explicit casts.
 * LunaLib values are read from the file it saves them to, not through its classes: Starsector's
 * script sandbox blocks reflection, and a direct reference wouldn't compile without LunaLib.
 */
public class MiningTooltipsModPlugin extends BaseModPlugin {

	private static final Logger log = Global.getLogger(MiningTooltipsModPlugin.class);

	private static final String NEX_ID = "nexerelin";
	private static final String MOD_SETTINGS = "data/config/modSettings.json";
	private static final String LEGACY_WEAPON_CSV = "data/config/exerelin/mining_weapons.csv";
	private static final String KEY_WEAPON_STRENGTHS = "mining_weapon_strengths";
	private static final String KEY_HIDDEN = "mining_hidden_ships_and_weapons";
	private static final String MOD_ID = "mining_strength_tooltips";
	private static final String OWN_CONFIG = "data/config/miningtooltips.json";
	private static final String LUNA_ID = "lunalib";
	// LunaLib stores values via LazyLib's JSONUtils in saves/common/LunaSettings/<modId>.json.data
	private static final String LUNA_SAVE_FILE = "LunaSettings/" + MOD_ID + ".json";

	@Override
	public void onApplicationLoad() throws Exception {
		SettingsAPI settings = Global.getSettings();
		if (!settings.getModManager().isModEnabled(NEX_ID)) {
			log.info("Nexerelin not enabled; no mining strengths to show");
			return;
		}

		Map strengths = new HashMap();
		Set hidden = new HashSet();
		loadLegacyCsv(settings, strengths, hidden);
		loadModSettings(settings, strengths, hidden);

		boolean showInStatBlock = getToggle(settings, "showInStatBlock");
		boolean showInDescription = getToggle(settings, "showInDescription");

		String label = settings.getString("miningtooltips", "miningStrength");
		String descLine = settings.getString("miningtooltips", "descriptionLine");
		int statCount = 0;
		int descCount = 0;
		Iterator iter = strengths.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String weaponId = (String) entry.getKey();
			float strength = ((Float) entry.getValue()).floatValue();
			if (strength <= 0 || hidden.contains(weaponId)) continue;

			WeaponSpecAPI spec;
			try {
				spec = settings.getWeaponSpec(weaponId);
			} catch (RuntimeException ex) {
				spec = null;
			}
			if (spec == null) continue;	// listed by a mod that isn't enabled

			String value = formatStrength(strength);
			if (showInStatBlock && addStatLine(spec, label, value)) statCount++;
			if (showInDescription && addDescriptionLine(weaponId, descLine.replace("%s", value))) descCount++;
		}
		log.info("Added mining strength to " + statCount + " weapon stat blocks and " + descCount + " descriptions");
	}

	/**
	 * LunaLib setting if LunaLib is enabled and has a value, else our JSON config, else true.
	 * LunaSettings.csv field IDs are the JSON keys prefixed with "mst_".
	 */
	protected boolean getToggle(SettingsAPI settings, String key) {
		if (settings.getModManager().isModEnabled(LUNA_ID)) {
			try {
				if (settings.fileExistsInCommon(LUNA_SAVE_FILE)) {
					String raw = settings.readTextFileFromCommon(LUNA_SAVE_FILE);
					if (raw != null && raw.trim().length() > 0) {
						JSONObject saved = new JSONObject(raw);
						if (saved.has("mst_" + key)) return saved.optBoolean("mst_" + key, true);
					}
				}
			} catch (Exception ex) {
				log.warn("Failed to read LunaLib setting mst_" + key + ", falling back to " + OWN_CONFIG, ex);
			}
		}
		try {
			return settings.getMergedJSONForMod(OWN_CONFIG, MOD_ID).optBoolean(key, true);
		} catch (Exception ex) {
			log.warn("Failed to load " + OWN_CONFIG + ", using default for " + key, ex);
			return true;
		}
	}

	protected void loadLegacyCsv(SettingsAPI settings, Map strengths, Set hidden) {
		try {
			JSONArray csv = settings.getMergedSpreadsheetDataForMod("id", LEGACY_WEAPON_CSV, NEX_ID);
			for (int i = 0; i < csv.length(); i++) {
				JSONObject row = csv.getJSONObject(i);
				String id = row.optString("id", "");
				if (id.length() == 0 || id.startsWith("#")) continue;
				try {
					strengths.put(id, Float.valueOf((float) row.getDouble("strength")));
				} catch (Exception ex) {
					continue;
				}
				if (row.optBoolean("hidden", false)) hidden.add(id);
			}
		} catch (Exception ex) {
			// file absent or empty in current Nexerelin versions; not an error
		}
	}

	protected void loadModSettings(SettingsAPI settings, Map strengths, Set hidden) {
		JSONObject nexSettings;
		try {
			JSONObject merged = settings.getMergedJSONForMod(MOD_SETTINGS, NEX_ID);
			nexSettings = merged.optJSONObject(NEX_ID);
		} catch (Exception ex) {
			log.error("Failed to load " + MOD_SETTINGS, ex);
			return;
		}
		if (nexSettings == null) return;

		JSONObject weapons = nexSettings.optJSONObject(KEY_WEAPON_STRENGTHS);
		if (weapons != null) {
			Iterator keys = weapons.keys();
			while (keys.hasNext()) {
				String id = (String) keys.next();
				try {
					strengths.put(id, Float.valueOf((float) weapons.getDouble(id)));
				} catch (Exception ex) {
					log.warn("Bad mining strength for weapon " + id, ex);
				}
			}
		}

		JSONArray hiddenArr = nexSettings.optJSONArray(KEY_HIDDEN);
		if (hiddenArr != null) {
			for (int i = 0; i < hiddenArr.length(); i++) {
				String id = hiddenArr.optString(i, null);
				if (id != null) hidden.add(id);
			}
		}
	}

	/**
	 * Appends the line as plain text and adds the value to the highlight list. Vanilla uses both
	 * %s placeholders and literal highlighted substrings in these fields; a literal line doesn't
	 * shift any existing %s substitutions, and the extra highlight arg is ignored by the formatter.
	 */
	protected boolean addStatLine(WeaponSpecAPI spec, String label, String value) {
		String text = spec.getCustomAncillary();
		String hl = spec.getCustomAncillaryHL();
		if (text != null && text.indexOf(label) >= 0) return false;	// already applied

		boolean hasText = text != null && text.trim().length() > 0;
		boolean hasHL = hl != null && hl.trim().length() > 0;

		StringBuilder newText = new StringBuilder();
		if (hasText) {
			// once there is a highlight list the text gets run through a formatter, so a lone % would break it
			newText.append(hasHL ? text : text.replace("%", "%%"));
			newText.append("\n\n");
		}
		newText.append(label).append(" ").append(value);

		spec.setCustomAncillary(newText.toString());
		spec.setCustomAncillaryHL(hasHL ? hl + " | " + value : value);
		return true;
	}

	/**
	 * Appends a paragraph to the description's main text. getDescription returns the shared
	 * instance the game displays, so the edit sticks for the whole session.
	 */
	protected boolean addDescriptionLine(String weaponId, String line) {
		Description desc;
		try {
			desc = Global.getSettings().getDescription(weaponId, Description.Type.WEAPON);
		} catch (RuntimeException ex) {
			desc = null;
		}
		if (desc == null) return false;

		String text = desc.getText1();
		if (text != null && text.indexOf(line) >= 0) return false;	// already applied

		// setText1 trims, and swaps an empty string for the "No description... yet" placeholder
		desc.setText1(desc.hasText1() ? text + "\n\n" + line : line);
		return true;
	}

	/** 10 -> "10", 1.5 -> "1.5", 0.75 -> "0.75" */
	protected static String formatStrength(float strength) {
		BigDecimal bd = new BigDecimal(Float.toString(strength)).setScale(2, RoundingMode.HALF_UP);
		return bd.stripTrailingZeros().toPlainString();
	}
}
