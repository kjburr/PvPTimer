package lemon42.PvPTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;

import lemon42.PvPTimer.PvPTimer;
import lemon42.PvPTimer.Utils.FlatListReader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

class Config {
	private FileConfiguration config;
	private FileConfiguration curConfig;
	private PvPTimer plugin;
	public boolean loaded;
	
	public Config(PvPTimer p) {
		plugin = p;
		loaded = false;
		config = null;
		curConfig = null;
	}
	
	public void load() {
		if(plugin == null) return;
		
		//Initialize objects
		loaded = false;
		config = null;
		curConfig = null;
		
		//Does a config already exist?
		boolean exist = plugin.localFile("config.yml").exists();
		if(!exist) {
			plugin.log.info("No configuration detected, saving defaults.");
			plugin.saveDefaultConfig();
		}
		//Make objects
		config = YamlConfiguration.loadConfiguration(plugin.localFile("config.yml"));
		curConfig = YamlConfiguration.loadConfiguration(plugin.getResource("config.yml"));
		
		plugin.log.info(config.getString("allowPickup"));
		//Get paths
		Set<String> thisPaths = curConfig.getKeys(true);
		Set<String> paths = config.getKeys(true);
		
		//Get revision numbers
		int thisRev = curConfig.getInt("revision");
		int rev = paths.contains("revision") ? config.getInt("revision") : -1;
		
		if(exist && thisRev > rev) {
			//Start migration process
			
			//1) Set all the old values to new values in config
			//*Old configurations don't have any matching paths*
			if(rev > 0)
				for(String key : thisPaths)
					if(paths.contains(key) && key != "revision") curConfig.set(key, config.get(key));

			//2) Migrate values from old revisions
			if(rev == -1) {				
				//Before new config was introduced			
				if(paths.contains("config.timeAmount") && paths.contains("config.timeMeasure")) {
					int amount = config.getInt("config.timeAmount");
					String type = config.getString("config.timeMeasure");
					
					if(type == null || type == "") type = "s";
					
					if(PvPTimer.parseTime(amount + type.substring(0, 1).toLowerCase()) != null)
						curConfig.set("timeAmounts.newPlayers", "" + amount + type.substring(0, 1).toLowerCase());
				}
				
				if(paths.contains("config.checkEvery"))
					curConfig.set("checkEvery", "" + config.getInt("config.checkEvery") + "s");
				
				if(paths.contains("config.disallowDamage"))
					curConfig.set("damageType", config.getString("config.disallowDamage"));
			} else if(rev == 1) {
				//1.0 config
				List<String> excludedWorlds = FlatListReader.readList(plugin.localFile("worlds.txt"));
				if(excludedWorlds == null) excludedWorlds = new ArrayList<String>();
				
				curConfig.set("excludedWorlds", excludedWorlds);
				
				//Time amounts
				if(paths.contains("timeAmounts")) {
					for(String p : config.getConfigurationSection("timeAmounts").getKeys(true))
						if(p == "newPlayers") continue;
						else curConfig.set("timeAmounts.default." + p.replace("timeAmounts", ""), config.getString(p));
					
					int amount = config.getInt("config.timeAmount");
					String type = config.getString("config.timeMeasure");
					
					if(type != null)
						if(PvPTimer.parseTime(amount + type.substring(0, 1)) != null)
							curConfig.set("timeAmounts.newPlayers", "" + amount + type.substring(0, 1).toLowerCase());
				}
			}
			
			curConfig.set("migratedFrom", rev);
			
			//3) Save new config using custom method to keep comments :)
			try {
				plugin.log.info("Backing up old configuration...");
				//4) BACKUP
				File theConfig = new File(plugin.getDataFolder(), "config.yml");
				File theConfigBackup = new File(plugin.getDataFolder(), "config_rev_" + rev + ".backup");
				try {
					if(theConfigBackup.exists()) theConfigBackup.delete();
					theConfigBackup.createNewFile();
					
					FileChannel source = null;
					FileChannel destination = null;
					try {
						source = new FileInputStream(theConfig).getChannel();
						destination = new FileOutputStream(theConfigBackup).getChannel();
						
						long count = 0;
						while((count += destination.transferFrom(source, count, source.size()-count))<source.size());
					} finally {
						if(source != null) source.close();
						if(destination != null) destination.close();
					}
				} catch (Exception e) {
					plugin.printException(e, "Error while creating backup!");
				}
				//5) Save config
				plugin.log.info("Saving new configuration...");
				
				writeConfig(curConfig, plugin.localFile("config.yml"));
				//6) Reload the object from disk.
				config = YamlConfiguration.loadConfiguration(plugin.localFile("config.yml"));
			} catch (IOException e) {
				plugin.printException(e, "Error while saving new configuration!");
				return;
			}
		} else if(thisRev < rev) {
			plugin.log.severe("Can't load newer configuration with revision " + rev + " (file) > " + thisRev + " (loaded)!");
			return;
		}
		
		loaded = true;
	}
	
	//Left for debugging purposes.
	@SuppressWarnings("unused")
	private void dumpConfig() {
		for(String s : config.getKeys(true)) {
			if(config.get(s) != null) {
				plugin.log.info(s + " = " + config.get(s));
			} else {
				plugin.log.info(s + " = NULL");
			}
		}
		
		plugin.log.info("HASHCODE = " + config.hashCode());
	}

	public void printf(BufferedWriter bw, String s) throws IOException {
		bw.write(s);
		bw.newLine();
	}
	
	//Main method.
	private void writeConfig(FileConfiguration theConfig, File dest) throws IOException {
		if(dest.exists()) dest.delete();
		
		//Store the config in a string
		String strConfig = theConfig.saveToString();
		
		//Streams
		FileWriter fstream = new FileWriter(dest, true);
		BufferedWriter fbw = new BufferedWriter(fstream);
		InputStream is = new ByteArrayInputStream(strConfig.getBytes());
		BufferedReader fbr = new BufferedReader(new InputStreamReader(is));
		
		//Precomments
		printf(fbw, "#PvPTimer " + plugin.getDescription().getVersion() + " configuration");
		printf(fbw, "#Please refer to http://dev.bukkit.org/server-mods/pvptimer/pages/configuration for help.");
		fbw.newLine();
		printf(fbw, "#NOTE: Remember to use only WHOLE NUMBERS for numerical values!");
		fbw.newLine();
		
		//Config
		String line;
		
		while((line = fbr.readLine()) != null) {
			if(line.length() == 0) fbw.newLine();
			else if(!line.startsWith("#")) printf(fbw, line);
		}
		
		fbw.newLine();
		
		//Postcomments
		fbw.write("#Thanks for using PvPTimer :D");
		
		//Close streams
		fbw.close();
		fbr.close();
		is.close();
		fstream.close();
	}

	//Shortcuts
	public boolean contains(String path) {
		return config.contains(path);
	}
	public int getInt(String path) {
		return config.getInt(path);
	}
	public String getString(String path) {
		return config.getString(path);
	}
	public List<String> getStringList(String path) {
		return config.getStringList(path);
	}
	public boolean getBoolean(String path) {
		return config.getBoolean(path);
	}
}
