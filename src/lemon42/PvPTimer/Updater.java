package lemon42.PvPTimer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

public class Updater {
	//http://api.bukget.org/3/plugins/bukkit/pvptimer/latest?fields=versions.version,versions.download,versions.link,versions.type,versions.md5
	boolean updateNeeded = false;
	PvPTimer plugin;
	String version,
			link,
			type;
	
	public Updater(PvPTimer instance) {
		plugin = instance;
	}
	
	//TODO: Does this need to be moved to a thread? :0
	public void check() {
		if (!plugin.config.getBoolean("checkForUpdates")) return;
		
		URL url;
		InputStream is = null;
		BufferedReader br;
		String json;
		
		try {
			//Get only the info we need.
			url = new URL("http://api.bukget.org/3/plugins/bukkit/pvptimer/latest?fields=versions.version,versions.link,versions.type");
			is = url.openStream();
			br = new BufferedReader(new InputStreamReader(is));
			json = br.readLine();
			
			//Now we have the source. Let's use it!
			version = getKey(json, "version");
			link = getKey(json, "link");
			type = getKey(json, "type");
			
			updateNeeded = isVersionNewer(version);
		} catch (MalformedURLException e) {
			//Won't happen! :L
		} catch (IOException ioe) {
			//Silently go away.
		} finally {
			//Close dat stream
			try {
				is.close();
			} catch (IOException ioe) {}
		}
	}
	
	//I know this isn't how you get a JSON key, however it works for this output.
	private String getKey(String json, String key) {
		return inBetween(json, "\"" + key + "\": ", "\"");
	}
	private String inBetween(String str, String before, String after) {
		if (!(str.contains(before) || str.contains(after))) return "";
		str = str.substring(str.indexOf(before) + before.length());
		if (!str.contains(after)) return "";
		return str.substring(0, str.indexOf(before));
	}
	private boolean isVersionNewer(String version) {
		if (version == null) return false;
		
		String curVersion = plugin.getDescription().getVersion().replace("dev", "");
		
		return versionToInt(version) > versionToInt(curVersion);
	}
	private int versionToInt(String version) {
		String[] split = version.split("\\.");
		
		int result = 0;
		
		//Find the important first two digits
		for (int i = 0; i < 2; i++)
			try {
				result += Integer.parseInt(split[i]) * Math.pow(10, 2 - i); //Purposely did 2, so that 1.1 would return 110
			} catch (Exception e) {
				return 0;
			}
		
		return result;
	}
	
	public boolean isUpdateNeeded() {
		return updateNeeded;
	}
	public String getVersion() {
		return version;
	}
	public String getLink() {
		return link;
	}
	public String getType() {
		return type;
	}
}
