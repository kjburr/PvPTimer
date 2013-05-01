package lemon42.PvPTimer;
class CheckTask implements Runnable {
	private PvPTimer plugin;
	
	public CheckTask(PvPTimer p) {
		this.plugin = p;
	}
	
	@Override
	public void run() {
		if(!plugin.times.isEmpty())
			for(Object p : plugin.times.keySet().toArray()) //Not sure about the toArray, I think it fixes the concurrent modification?
				try {
					plugin.checkPlayer(plugin.getServer().getOfflinePlayer((String) p));
				} catch (Exception e) {} //Exception should never happen, but... better safe than sorry :)
	}
}
