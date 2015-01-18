package lemon42.PvPTimer.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;

public enum FactionsUUID {;

	private static boolean enabled = false;

	static {
		enabled = Bukkit.getPluginManager().isPluginEnabled("Factions") || Bukkit.getPluginManager().isPluginEnabled("HCFactions");
	}


	public static boolean isInBlockedChunk(Player player, Location location) {
		if (!enabled) {
			return false; // Factions isn't loaded
		}
		FPlayer fplayer = FPlayers.getInstance().getByPlayer(player);
		FLocation flocation = new FLocation(location);
		
		Faction myFaction = fplayer.getFaction();
		Faction otherFaction = Board.getInstance().getFactionAt(flocation);
		
		return otherFaction.isNormal() && !otherFaction.equals(myFaction);
	}

}
