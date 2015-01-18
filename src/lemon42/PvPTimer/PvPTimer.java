package lemon42.PvPTimer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lemon42.PvPTimer.TimeItem.TimeItem;
import lemon42.PvPTimer.TimeItem.TimeItemType;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.MetricsLite;

public class PvPTimer extends JavaPlugin {
	Logger log;
	HashMap<String, TimeItem> times;
	
	private PvPListener listener;
	private static LanguageProvider lang;
	private MetricsLite metrics;
	private Permission perms;
	public Config config;
	public List<String> excludedWorlds;
	public List<String> groups; //This contains the groups which, in the config, are not default.
	public Updater updater;
	
	public Set<String> blockedRegionIds;
	
	//These values are replaced by language loading, but kept for reference.
	public String prefix = ChatColor.BLUE + "[PvPTimer] ";
	public String cannotHurt = prefix + "You cannot hurt this player!";
	public String errorPrefix = ChatColor.RED + "Error: ";
	public String errorPlayer = errorPrefix + "Only players may use this command.";
	public String errorPerms = errorPrefix + "You don't have permissions to use this command.";
	public String errorArgs = errorPrefix + "Invalid arguments!";
	public String notProtected = errorPrefix + "You are not protected!";

	@Override
	public void onEnable() {
		log = this.getLogger();
		config = new Config(this);
		lang = new LanguageProvider(this);
		updater = new Updater(this);
		
		//Preinit
		times = new HashMap<String, TimeItem>();
		
		//Load config
		config.load();
		
		//Excluded worlds
		excludedWorlds = config.getStringList("excludedWorlds");
		
		// Load blocked region ids
		blockedRegionIds = new HashSet<String>(config.getStringList("blockedRegionIds"));
		
		//Load lang from file
		//This will save the default so no worries
		localFile("lang" + File.separator).mkdir();
		loadLang();
		
		//Metrics
		if(config.getBoolean("enableMetrics")) {
			try {
				metrics = new MetricsLite(this);
				metrics.start();
			} catch (IOException e) {}
		}

		//Load protections
		try {
			if (localFile("values.dat").exists()) times = loadMap(localFile("values.dat"));
		} catch (Exception e) {
			log.severe("Error while loading protections! Values will not be recovered!");
			log.severe("Exception details:");
			e.printStackTrace();
			times = new HashMap<String, TimeItem>(); //fallback
		}
		
		//Register events
		listener = new PvPListener(this);
		getServer().getPluginManager().registerEvents(listener, this);
		
		//Vault
		loadVault();
		
		//Check task
		try {
			getServer().getScheduler().runTaskTimerAsynchronously(this, new CheckTask(this), 0, config.getTime("checkEvery") / 1000 * 20);
		} catch(IllegalArgumentException e) {
			log.severe("/!\\ COULD NOT INITIALIZE CHECK THREAD! THIS BREAKS THE PLUGIN! /!\\"); //wat
		}
		
		//Save task
		try {
			long interval = config.getTime("saveEvery");
			
			if(interval != 0) //allows disabling autosave
				getServer().getScheduler().runTaskTimerAsynchronously(this, new WriteTask(this), 0, interval / 1000 * 20);
		} catch(IllegalArgumentException e) {
			log.severe("/!\\ COULD NOT INITIALIZE SAVE THREAD! /!\\");
		}
		
		//Check for updates
		if(config.getBoolean("checkForUpdates")) updater.check();
		
		//Yay!
		log.info("Version " + getDescription().getVersion() + " enabled!");
	}
	@Override
	public void onDisable() {
		log.info("Stopping tasks...");
		getServer().getScheduler().cancelTasks(this);
		
		//Save!
		try {
			saveTheMap();
		} catch (Exception e) {
			printException(e, "Error while saving protections! Protections will not be recovered on startup!");
		}
		
		log.info("Version " + getDescription().getVersion() + " disabled.");
	}
	
	//Language!
	public void loadLang() {
		lang.load(config.getString("language"), false);
		
		prefix = lang("prefix");
		cannotHurt = prefix + lang("cannotHurtOthers");
		
		errorPrefix = lang("errorPrefix");
		errorPlayer = errorPrefix + lang("playersOnly");
		errorPerms = errorPrefix + lang("noPerms");
		errorArgs = errorPrefix + lang("invalidArgs");
		notProtected = errorPrefix + lang("notProtected");
	}
	public String lang(String key) {
		return lang.get(key);
	}
	public String lang(String key, String user) {
		return lang.get(key, user, null);
	}
	public String lang(String key, Long time) {
		return lang.get(key, null, time);
	}
	public String lang(String key, String user, Long time) {
		return lang.get(key, user, time);
	}

	public void loadVault() {
		groups = null;
		perms = null;
		
		Plugin plug = getServer().getPluginManager().getPlugin("Vault");
		
		if (plug == null) {
			log.warning("Vault plugin not found. Values will be fetched from timeAmounts.default!");
		} else {
			try {
		        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(Permission.class);
		        if (permissionProvider != null) {
		            perms = permissionProvider.getProvider();
		            
		            groups = new ArrayList<String>();
		            
		            //Load all groups :3
		            for(String g : perms.getGroups())
		            	if(config.contains("timeAmounts." + g)) groups.add(g);

		            log.info("Hooked to Vault, using permissions plugin: " + perms.getName());
		        } else {
		        	log.warning("Vault plugin found, but reported no permissions system. Values will be fetched from timeAmounts.default!");
		        }
			} catch (Exception e) {
				printException(e, "Couldn't load permissions handler from Vault. Values will be fetched from timeAmounts.default!");
			}
		}
	}
	public String getGroup(Player p) {
		if(groups == null) return "default";
		
		try {
			String g = perms.getPrimaryGroup(p);
			if(groups.contains(g)) return g;
		} catch(UnsupportedOperationException e) {
			// Probably no groups?
		}
		
		return "default";
	}
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		Player player = (sender instanceof Player ? (Player)sender : null); //replaces old if :L
		if (args.length == 0) {
			//No args? Help!
			args = new String[1];
			args[0] = "help";
		}
		
		if (cmd.getName().equalsIgnoreCase("pvptimer")) {
			if (args[0].equalsIgnoreCase("help")) {
				//TODO: Should we have some language keys for these too? Hmmm...
				sender.sendMessage(ChatColor.BLUE + "PvPTimer " + getDescription().getVersion() + " help");
				sender.sendMessage(ChatColor.BLUE + "Currently protecting " + times.size() + " player" + (times.size() != 1 ? "s" : ""));

				sender.sendMessage(ChatColor.GRAY + "/pvptimer remove" + ChatColor.WHITE + " - remove your protection");
				if(sender.hasPermission("PvPTimer.removeOthers")) sender.sendMessage(ChatColor.RED + "/pvptimer remove [player]" + ChatColor.WHITE + " - remove another player's protection");
				
				sender.sendMessage(ChatColor.GRAY + "/pvptimer check" + ChatColor.WHITE + " - check how much time you have left protected");
				if(sender.hasPermission("PvPTimer.checkOthers")) sender.sendMessage(ChatColor.RED + "/pvptimer check [player]" + ChatColor.WHITE + " - checks another player's protection");
				
				if(sender.hasPermission("PvPTimer.grant")) sender.sendMessage(ChatColor.RED + "/pvptimer grant [player] [time]" + ChatColor.WHITE + " - gives protection to someone else");
				if(sender.hasPermission("PvPTimer.reset")) sender.sendMessage(ChatColor.RED + "/pvptimer reset" + ChatColor.WHITE + " - resets protection for all online players");
				if(sender.hasPermission("PvPTimer.reload")) sender.sendMessage(ChatColor.RED + "/pvptimer reload" + ChatColor.WHITE + " - reloads the plugin");
				sender.sendMessage(ChatColor.BLUE + "Plugin by lemon42 - http://dev.bukkit.org/profiles/lemon42");
			} else if (args[0].equalsIgnoreCase("remove")) {
				if(args.length == 1) {
					//Self remove
					if(player != null) {
						if(player.hasPermission("PvPTimer.remove")) {
							if (isProtected(player)) {
								times.remove(player.getName());
								player.sendMessage(prefix + lang("protectionRemoved"));
							} else player.sendMessage(notProtected);
						} else sender.sendMessage(errorPerms);
					} else sender.sendMessage(errorArgs);
				} else if(args.length == 2) {
					//Admin
					if(sender.hasPermission("PvPTimer.removeOthers")) {
						OfflinePlayer p = getServer().getOfflinePlayer(args[1]);
						if(isProtected(p)) {
							times.remove(p.getName());
							if(p.isOnline()) p.getPlayer().sendMessage(prefix + lang("protectionRemoved"));
							sender.sendMessage(prefix + lang("userProtectionRemoved", p.getName()));
						} else sender.sendMessage(errorPrefix + lang("userNotProtected", p.getName()));
					} else sender.sendMessage(errorPerms);
				} else sender.sendMessage(errorArgs);
			} else if (args[0].equalsIgnoreCase("check")) {
				if(args.length == 1) {
					//Self check
					if(player != null) {
						if (isProtected(player)) {
							player.sendMessage(prefix + lang("protectionLeft", player.getName(), getTimeLeft(player)));
						} else if(isTimeout(player)) {
							player.sendMessage(prefix + lang("checkTimeout", player.getName(), getTimeLeft(player)));
						} else player.sendMessage(notProtected);
					} else sender.sendMessage(errorArgs);
				} else if(args.length == 2) {
					//Admin
					if(sender.hasPermission("PvPTimer.checkOthers")) {
						OfflinePlayer p = getServer().getOfflinePlayer(args[1]);
						
						if(isProtected(p)) {
							sender.sendMessage(prefix + lang("userProtectionLeft", p.getName(), getTimeLeft(p)));
						} else sender.sendMessage(errorPrefix + lang("userNotProtected", p.getName()));
					} else sender.sendMessage(errorPerms);
				} else sender.sendMessage(errorArgs);
			} else if(args[0].equalsIgnoreCase("reload")) {
				if(args.length == 1) {
					if(sender.hasPermission("PvPTimer.reload")) {
						//TODO: Make an universal function to reload so I don't have to update this aswell as the first load ._.
						boolean error = false;
						
						//1) Save
						try {
							saveTheMap();
						} catch (Exception e) {
							sender.sendMessage(errorPrefix + "Could not save protections! Check the console for details.");
							log.severe("Error while saving protections! Values will not be recovered!");
							log.severe("Exception details:");
							e.printStackTrace();
							error = true;
						}
						
						//2) Load config
						sender.sendMessage(prefix + "Loading configuration...");
						config = new Config(this);
						config.load();
						
						//Update listener
						listener.update(this);

						if(!config.loaded) error = true;
						else excludedWorlds = config.getStringList("excludedWorlds");
						
						if(error) sender.sendMessage(errorPrefix + "Could not read configuration! Check the console for details.");
						
						//3) Load protections
						sender.sendMessage(prefix + "Loading protections...");
						try {
							if (localFile("values.dat").exists()) times = loadMap(localFile("values.dat"));
							else times = new HashMap<String, TimeItem>();
						} catch (Exception e) {
							sender.sendMessage(errorPrefix + "Could not read protections! Check the console for details. Cause: " + e.getMessage());
							log.severe("Error while loading protections! Values will not be loaded!");
							log.severe("Exception details:");
							e.printStackTrace();
							
							error = true;
							
							times = new HashMap<String, TimeItem>(); //fallback
						}
						sender.sendMessage(prefix + "Loaded " + times.size() + " protections.");
						
						//4) Load lang
						sender.sendMessage(prefix + "Loading language file...");
						loadLang();
						
						//5) Vault
						loadVault();
						
						//6) Check for updates
						if(config.getBoolean("checkForUpdates")) updater.check();
						
						//Done!
						if(!error) {
							sender.sendMessage(prefix + "Plugin reloaded.");
						} else {
							if(sender instanceof Player) {
								sender.sendMessage(ChatColor.RED + "An error occured. More details might be found at the console.");
								log.severe("An error occured while reloading the plugin in-game. Please check your files and contact the plugin developers if nescesary.");
							} else {
								sender.sendMessage(ChatColor.RED + "An error occured!");
								log.severe("An error occured while reloading the plugin. Please check your files and contact the plugin developers if nescesary.");
							}
						}
					} else sender.sendMessage(errorPerms);
				} else sender.sendMessage(errorArgs);
			} else if(args[0].equalsIgnoreCase("grant")) {
				if(args.length == 3) { //grant, player, time
					if(sender.hasPermission("PvPTimer.grant")) {
						OfflinePlayer p = getServer().getOfflinePlayer(args[1]);
						if(!p.isOnline()) {
							sender.sendMessage(errorPrefix + lang("playerNotFound", p.getName()));
							return true;
						}
						
						Long t = parseTime(args[2]);
						if(t == 0L) {
							sender.sendMessage(errorArgs);
							return true;
						}
						
						Player p2 = p.getPlayer();
						addPlayer(p2, System.currentTimeMillis() + t, TimeItemType.ADMIN);
						
						p2.sendMessage(prefix + lang("protectionGrantedBy", sender.getName(), t));
						sender.sendMessage(prefix + lang("protectionGranted", p.getName(), t));
					} else sender.sendMessage(errorPerms);
				} else sender.sendMessage(errorArgs);
			} else if(args[0].equalsIgnoreCase("reset")) {
				if(args.length == 1) {
					if(sender.hasPermission("PvPTimer.reset")) {
						for(Player p : getServer().getOnlinePlayers()) {
							addPlayer(p, System.currentTimeMillis(), TimeItemType.JOIN);
							p.sendMessage(prefix + lang("protectionReset", config.getTime(TimeItemType.getConfigNode(TimeItemType.JOIN, getGroup(p)))));
						}
						
						sender.sendMessage(prefix + lang("protectionResetForAll"));
					} else sender.sendMessage(errorPerms);
				} else sender.sendMessage(errorArgs);
			} else sender.sendMessage(errorArgs); //Subcommand
		} //Main command

		return true;
	}
	
	void printException(Exception e, String msg) {
		log.severe(msg);
		log.severe("Exception details:");
		log.severe(e.toString());
	}
	
	//Time functions
	static Long parseTime(String time) {
		if(time == null) return 0L;
		//Example strings: 1h, 10m, 30s, 1h10m, 1h10m30s, 10m30s, etc.
		time = time.toLowerCase().trim(); // Do some firt time checks.
		if(time.equals("0")) return 0L;
		//Variables to hold values ;D
		Long hours = 0L, minutes = 0L, seconds = 0L;
		String curVal;
		//Pattern and matcher
		Pattern p = Pattern.compile("\\d+[msh]", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(time);
		//Start looking for values
		boolean found = false;
		while (m.find()) {
			found = true;
			curVal = m.group();
			
			if (curVal.endsWith("s") && seconds == 0) seconds = Long.parseLong(curVal.replace("s", ""));
			else if (curVal.endsWith("m") && minutes == 0) minutes = Long.parseLong(curVal.replace("m", ""));
			else if (curVal.endsWith("h") && hours == 0) hours = Long.parseLong(curVal.replace("h", ""));
		}
		
		if (!found) {
			//Fallback for seconds
			try {
				Long value = Long.parseLong(time) * 1000;
				if(value < 0) return 0L;
				return value;
			} catch(Exception e) {
				return 0L;
			}
		}
		
		//Found, it seems.
		if(seconds < 0 || minutes < 0 || seconds < 0) return 0L;
		return (Long)(seconds * 1000 + minutes * 1000 * 60 + hours * 1000 * 60 * 60);
	}
	public String formatTime(Long time) {
		if(time == null || time < 0) return "";
		
		int seconds = (int)(Math.ceil(time / 1000) % 60);
		int minutes = (int)(Math.ceil(time / 1000 / 60) % 60);
		int hours = (int)(Math.ceil(time / 1000 / 60 / 60));
		if (time < 1000) seconds = 1;
		
		ArrayList<String> arr = new ArrayList<String>();
		if (hours > 0) arr.add(hours + " " + (hours > 1 ? lang("hours") : lang("hour")));
		if (minutes > 0) arr.add(minutes + " " + (minutes > 1 ? lang("minutes") : lang("minute")));
		if (seconds > 0) arr.add(seconds + " " + (seconds > 1 ? lang("seconds") : lang("second")));
		int i = (seconds > 0 ? 1 : 0) + (minutes > 0 ? 1 : 0) + (hours > 0 ? 1 : 0);
		
		if (i == 0) return "1 " + lang("second");
		String returnStr = "";
		int cur = 0;
		for (String s : arr) {
			cur++;
			if (s != null) returnStr += s + (cur == i ? "" : (cur == i - 1 ? " " + lang("and") + " " : ", "));
		}
		return returnStr;
	}
	
	//API STUFF 
	public void addPlayer(Player p, Long timeStamp, TimeItemType type) {
		if(p == null) return;
		long time = config.getTime(TimeItemType.getConfigNode(type, getGroup(p)));
		if(time == 0) return;
		
		times.put(p.getName(), new TimeItem(timeStamp + time, type));
	}
	
	public void checkPlayer(OfflinePlayer p) {
		if (p.isOnline()) {
			checkPlayer(getServer().getPlayerExact(p.getName()));
			return;
		}
		String name = p.getName();
		if (times.containsKey(name))
			if (times.get(name).getEndTime() <= System.currentTimeMillis()) {
				times.remove(name);
				//Add timeout when not.... online? 3:
			}
	}
	public void checkPlayer(Player p) {
		checkPlayer(p, true);
	}
	public void checkPlayer(Player p, boolean send) {
		if (!p.isOnline()) {
			checkPlayer(getServer().getOfflinePlayer(p.getName()));
			return;
		}
		String name = p.getName();
		if (times.containsKey(name)) {
			if (times.get(name).getEndTime() <= System.currentTimeMillis()) {
				if(times.get(name).getType() != TimeItemType.TIMEOUT) {
					if(send) p.sendMessage(prefix + lang("protectionExpired"));
					
					addPlayer(p, System.currentTimeMillis(), TimeItemType.TIMEOUT);
					p.sendMessage(prefix + lang("timedOut", p.getName(), getTimeLeft(p)));
				} else {
					//remove timeout
					times.remove(name);
					p.sendMessage(prefix + lang("notTimedOut", p.getName(), getTimeLeft(p)));
				}
			}
		}
	}
	
	public Long getTimeLeft(OfflinePlayer p) {
		//the OR fixes timeout :)
		if(isProtected(p) || times.containsKey(p.getName())) return times.get(p.getName()).getEndTime() - System.currentTimeMillis();
		return 0L;
	}
	public Long getTimeLeft(Player p) {
		return getTimeLeft(getServer().getOfflinePlayer(p.getName()));
	}
	
	public boolean isProtected(OfflinePlayer p) {
		checkPlayer(p);
		return times.containsKey(p.getName()) && times.get(p.getName()).getType() != TimeItemType.TIMEOUT;
	}
	public boolean isProtected(Player p) {
		checkPlayer(p);
		return times.containsKey(p.getName()) && times.get(p.getName()).getType() != TimeItemType.TIMEOUT;
	}
	public boolean isTimeout(OfflinePlayer p) {
		checkPlayer(p);
		return times.containsKey(p.getName()) && times.get(p.getName()).getType() == TimeItemType.TIMEOUT;
	}
	public boolean isTimeout(Player p) {
		checkPlayer(p);
		return times.containsKey(p.getName()) && times.get(p.getName()).getType() == TimeItemType.TIMEOUT;
	}
	
	public boolean isWorldExcluded(World w) {
		return isWorldExcluded(w.getName());
	}
	public boolean isWorldExcluded(String w) {
		return excludedWorlds.contains(w);
	}
	
	File localFile(String f) {
		return new File(getDataFolder(), f);
	}
	
	void saveTheMap() throws Exception {
		if (localFile("values.dat").exists()) localFile("values.dat").delete();
		saveMap(times, localFile("values.dat"));
	}
	
	//Hashmap functions
	void saveMap(HashMap<String, TimeItem> theMap, File f) throws Exception {
		DataOutputStream s = new DataOutputStream(new FileOutputStream(f));
		//Write all items
		for(Entry<String, TimeItem> entry : theMap.entrySet()) {
			s.writeUTF(entry.getKey()); // Write the key - name
			entry.getValue().write(s); // Write the value - TimeItem
		}
		
		s.flush();
		s.close();
	}
	private HashMap<String, TimeItem> loadMap(File f) throws Exception {
		DataInputStream s = new DataInputStream(new FileInputStream(f));
		
		HashMap<String, TimeItem> result = new HashMap<String, TimeItem>();
		
		while (s.available() != 0) //Still gotta read
			result.put(s.readUTF(), TimeItem.read(s));
		
		s.close();
		return result; 
	}
}
