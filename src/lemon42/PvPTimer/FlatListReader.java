package lemon42.PvPTimer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;

class FlatListReader {
	public static List<String> readList(File f) {
		if(!f.exists() || !f.canRead()) return null;
		
		boolean closeError = false;
		
		List<String> list = new ArrayList<String>();
		
		try {
			InputStreamReader r = new InputStreamReader(new FileInputStream(f));
			BufferedReader fbr = new BufferedReader(r);
			
			String line;
			
			while((line = fbr.readLine()) != null) {
				if(line.startsWith("#")) continue;
				if(Bukkit.getServer().getWorld(line) != null) list.add(line);
			}
			
			closeError = true;			
			fbr.close();
			r.close();
		} catch (Exception e) { if(!closeError) return null; }
		
		return list;

	}
}
