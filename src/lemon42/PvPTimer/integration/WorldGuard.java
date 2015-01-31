package lemon42.PvPTimer.integration;

import java.util.List;

import lemon42.PvPTimer.PvPTimer;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;

public enum WorldGuard {;

	private static PvPTimer plugin;
	private static boolean enabled;

	static {
		enabled = Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
	}


	public static void init(PvPTimer pvptimer) {
		plugin = pvptimer;
	}

	public static boolean isInBlockedRegion(Location location) {
		if (!enabled) {
			return false; // WorldGuard isn't loaded
		}
		RegionManager regionManager = WorldGuardPlugin.inst().getRegionManager(location.getWorld());
		Vector vector = new Vector(location.getX(), location.getY(), location.getZ());
		List<String> regionIdList = regionManager.getApplicableRegionsIDs(vector);

		for (String blockedId : plugin.blockedRegionIds) {
			if (regionIdList.contains(blockedId)) {
				return true;
			}
		}
		return false;
	}

}
