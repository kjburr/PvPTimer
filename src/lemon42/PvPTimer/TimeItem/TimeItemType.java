private package lemon42.PvPTimer.TimeItem;

public enum TimeItemType {
	FIRSTJOIN(0), //Protection for being a newbie
	RESPAWN(1), //Respawn protection
	JOIN(2), //Consecutive joins
	ADMIN(3), //Admin given protections
	TELEPORT(4),//Teleporting :0
	WORLDCHANGE(5), //On changing worlds.
	TIMEOUT(255); //Disallow protection when in timeout. Added for next update?
	
	//Constructor
	TimeItemType(int id) {
		this.id = id;
	}
	//private stuff
	private int id;
	public int getId() {
		return id;
	}
	//static stuff
	public static TimeItemType fromId(int i) {
		for(TimeItemType t : TimeItemType.values())
			if(t.getId() == i) return t;
		return null;
	}
	public static String getConfigNode(TimeItemType t) {
		return getConfigNode(t, "default");
	}
	public static String getConfigNode(TimeItemType t, String group) {
		if(t == TimeItemType.FIRSTJOIN) return "timeAmounts.newPlayers";
		//Groups
		if(t == TimeItemType.RESPAWN) return "timeAmounts." + group + ".respawn";
		if(t == TimeItemType.JOIN) return "timeAmounts." + group + ".join";
		//Admin does not have a configuration node!
		if(t == TimeItemType.TELEPORT) return "timeAmounts." + group + ".teleport";
		if(t == TimeItemType.WORLDCHANGE) return "timeAmounts." + group + ".worldChange"; //Append .world :0
		return "";
	}
}
