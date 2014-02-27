package lemon42.PvPTimer;

import java.io.InputStream;
import java.net.URL;

public class Updater {
	//http://api.bukget.org/3/plugins/bukkit/pvptimer/latest?fields=versions.version,versions.download,versions.link,versions.type,versions.md5
	private boolean updateNeeded = false;
	private PvPTimer plugin;
	private String version, link;
	
	public Updater(PvPTimer instance) {
		plugin = instance;
	}
	
	private static String streamToString(InputStream is) {
	    java.util.Scanner s = new java.util.Scanner(is);
	    s.useDelimiter("\\A");
	    
	    String str = "";
	    while(s.hasNext())
	    	str += s.next();
	    
	    s.close();
	    
	    return str;
	}
	
	public void check() {
		//Start in a thread.
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {       		
            	InputStream is = null;
        		try {
        			//Get only the info we need.
        			is = new URL("http://dev.bukkit.org/bukkit-plugins/pvptimer/files.rss").openStream();
        			String rss = streamToString(is);
        		    rss = rss.substring(rss.indexOf("<item>"));
        			
        			version = getKey(rss, "title").replace("PvPTimer ", "");
        			link = shorten(getKey(rss, "link"));

        			updateNeeded = isVersionNewer(version);
        			
        			plugin.log.info(" >> " + link + " >> " + version);
        		} catch (Exception e) {
        			//Exception D:
        			updateNeeded = false;
        		} finally {
        			//Close dat stream
        			try {
        				is.close();
        			} catch (Exception e) {} //for null pointers!
        		}
        		
        		//Done! Print out something. :D
        		if(isUpdateNeeded()) plugin.log.info("An update is available! Download it here: " + getLink());
            }
        });
	}
	
	//Creates a shorter BukkitDev file link.
	private String shorten(String link) {
		return "http://dev.bukkit.org/bukkit-plugins/pvptimer/files/" + inBetween(link, "files/", "-");
	}
	
	private String getKey(String src, String key) {
		return inBetween(src, "<" + key + ">", "</" + key + ">");
	}
	private String inBetween(String str, String before, String after) {
		if (str == null || before == null || after == null || str == "" || before == "" || after == "" || !(str.contains(before) || str.contains(after))) return "";
		str = str.substring(str.indexOf(before) + before.length());
		if (!str.contains(after)) return "";
		return str.substring(0, str.indexOf(after));
	}
	private boolean isVersionNewer(String version) {
		if (version == null) return false;

		return versionToInt(version) > versionToInt(plugin.getDescription().getVersion().replace("dev", ""));
	}
	private int versionToInt(String version) {
		if(version == null) return 0;
		
		String[] split = version.split("\\.");
		
		int result = 0;
		
		//Find the important first three digits
		for (int i = 0; i < split.length; i++)
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
}
