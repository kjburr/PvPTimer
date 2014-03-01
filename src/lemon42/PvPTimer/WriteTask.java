package lemon42.PvPTimer;

import lemon42.PvPTimer.Utils.Checksum;

class WriteTask implements Runnable {
	private PvPTimer plugin;
	private String lastChecksum = "this is not a checksum dummy string"; //definitely not
	
	WriteTask(PvPTimer p) {
		this.plugin = p;
		lastChecksum = Checksum.compute(plugin.times);
	}
	

	@Override
	public void run() {
		String newChecksum = Checksum.compute(plugin.times);
		
		if(!newChecksum.equalsIgnoreCase(lastChecksum)) {
			lastChecksum = newChecksum;
			
			try {
				plugin.saveTheMap();
			} catch (Exception e) {
				plugin.log.severe("Error while saving values! Values will not be recovered on startup!");
				plugin.log.severe("Exception details:");
				e.printStackTrace();
			}
		}
		
	}
}
