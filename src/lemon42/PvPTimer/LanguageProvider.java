package lemon42.PvPTimer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

class LanguageProvider {
	private boolean loaded;
	private FileConfiguration lang;
	private FileConfiguration def;
	private String loadedLocale;
	private PvPTimer plugin;
	
	public LanguageProvider(PvPTimer instance) {
		//constructor
		plugin = instance;
		def = YamlConfiguration.loadConfiguration(plugin.getResource("English.yml"));
	}

	public void saveDefault() {
		try {
			save(def, plugin.localFile("lang" + File.separator + "English.yml"), false);
		} catch (IOException e) {
			plugin.log.warning("Couldn't save default!");
			e.printStackTrace();
		}
	}
	
	public void load(String localeFile, boolean inJar) {
		loaded = false;
		loadedLocale = null;
		lang = null;
		
		String path = "lang" + File.separator + localeFile + ".yml";
		File f = plugin.localFile(path);
		
		if(!f.exists()) {
			if(localeFile == "English") {
				saveDefault();
			} else {
				plugin.log.warning("No such locale '" + localeFile + "'. Using default.");
			}
			load("English", true);
			return;
		}
		
		//Load.
		if(inJar) lang = YamlConfiguration.loadConfiguration(plugin.getResource(localeFile + ".yml"));
		else lang = YamlConfiguration.loadConfiguration(f);
		try {
			def = YamlConfiguration.loadConfiguration(plugin.getResource(localeFile + ".yml"));
		} catch(Exception e) {
			def = YamlConfiguration.loadConfiguration(plugin.getResource("English.yml"));
		}
		
		if(lang.contains("revision") && lang.contains("customFile")) {
			//Valid file
			int currentRev = def.getInt("revision");
			int langRev = lang.getInt("revision");
			if(langRev < currentRev) {
				//Old, migrate
				migrate(lang, def, localeFile);
			} else if(langRev > currentRev) {
				//More recent, wtf?
				plugin.log.warning("Language file '" + localeFile + "' has a revision of " + langRev + " > current revision of " + currentRev + ". Using default.");
				load("English", true);
				return;
			}
		} else {
			//Missing some keys...
			plugin.log.warning("Language file '" + localeFile + "' is not valid. Using default.");
			load("English", true);
			return;
		}
		
		loadedLocale = localeFile;
		loaded = true;
	}
	
	private String getRaw(String key) {
		//Testing.
		if(!loaded || lang == null || lang.getString(key) == null) return "MISSING: " + key;
		if(key == "revision" || key == "customFile") return "";
		return ChatColor.translateAlternateColorCodes('&', lang.getString(key)).replace("\\n", "\n");
	}
	public String get(String key) {
		String ret = getRaw(key).replace("%version%", plugin.getDescription().getVersion());
		return ret.replace("%count%", "" + plugin.times.size());
	}
	public String get(String key, String user, Long time) {
		String ret = get(key);
		if(user != null) ret = ret.replace("%user%", user);
		if(time != null) ret = ret.replace("%time%", PvPTimer.formatTime(time));
		return ret;
	}
	public int getRevision() {
		if(!loaded || lang == null) return -1;
		return lang.getInt("revision");
	}
	public boolean isLoaded() {
		return loaded;
	}
	public String getLoadedLocale() {
		return loadedLocale;
	}

	public void printf(BufferedWriter bw, String s) throws IOException {
		bw.write(s);
		bw.newLine();
	}
	private void save(FileConfiguration conf, File dest, boolean changed) throws IOException {
		if(dest.exists()) dest.delete();
		
		//Store the config in a string
		String strConfig = conf.saveToString();
		
		//Streams
		FileWriter fstream = new FileWriter(dest, true);
		BufferedWriter fbw = new BufferedWriter(fstream);
		InputStream is = new ByteArrayInputStream(strConfig.getBytes());
		BufferedReader fbr = new BufferedReader(new InputStreamReader(is));
		
		//Precomments
		printf(fbw, "#PvPTimer language file");
		printf(fbw, "#Additional languages can be obtained from http://dev.bukkit.org/server-mods/pvptimer/pages/languages");
		fbw.newLine();
		printf(fbw, "#Variables you can use:");
		printf(fbw, "# %time%    - time left of protection");
		printf(fbw, "# %user%    - the user involved");
		printf(fbw, "# %count%   - the total number of protected users");
		printf(fbw, "# %version% - PvPTimer's version number");
		printf(fbw, "#Certain variables will not be available in all cases.");
		printf(fbw, "#You may also use colors, in the format &[colorcode]");
		fbw.newLine();
		
		if(changed) {
			printf(fbw, "#NOTE: This language file was changed by PvPTimer. Please check for any changes!");
			fbw.newLine();
		}
		
		//Config
		String line;
		
		while((line = fbr.readLine()) != null) {
			if(line.length() == 0) fbw.newLine();
			else if(!line.startsWith("#")) printf(fbw, line);
		}
		
		fbw.write("#End of file");
		
		fbw.close();
		fbr.close();
		is.close();
		fstream.close();
	}
	public void migrate(FileConfiguration conf, FileConfiguration def, String locale) {
		//Remove keys that aren't present anymore
		boolean changed = false;
		boolean customFile = conf.getBoolean("customFile");
		
		for(String k : conf.getKeys(true))
			if(k != "revision" && k != "customFile" && !def.contains(k)) { conf.set(k, null); changed = true; }
		
		//Set new keys
		for(String k : def.getKeys(true))
			if(k != "revision" && k != "customFile" && (!conf.contains(k) || (!customFile || conf.get(k) != def.get(k))))
			{ conf.set(k, def.get(k)); changed = true; }
		
		try {
			save(lang, plugin.localFile("lang" + File.separator + locale + ".yml"), changed);
		} catch (IOException e) {
			plugin.log.warning("Error while migrating language file " + locale);
			e.printStackTrace();
		}
	}

}
