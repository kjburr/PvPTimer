package lemon42.PvPTimer;

import java.util.HashMap;
import lemon42.PvPTimer.TimeItem.TimeItemType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

class PvPListener implements Listener {
	PvPTimer plugin;
	Config config;
	
	public PvPListener(PvPTimer p) {
		update(p);
	}
	
	public void update(PvPTimer p) {
		plugin = p;
		config = plugin.config;
	}
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPvP(EntityDamageByEntityEvent event) {
		if ((event.getEntity() instanceof Player || event.getEntityType() == EntityType.PLAYER) && plugin.excludedWorlds.contains(event.getEntity().getWorld().getName())) return;
		
		if (event.getDamager() instanceof Player) {
			//DAMAGER IS PLAYER
			Player damager = (Player)event.getDamager();
			if (plugin.isProtected(damager)) {
				if (event.getEntity() instanceof Player) {
					//DAMAGER PROTECTED HITS OTHER PLAYER
					event.setCancelled(true);
					damager.sendMessage(plugin.prefix + plugin.lang("cannotHurtWhileProtected", plugin.getTimeLeft((Player)event.getEntity())));
				}
			}
		} else if (event.getDamager() instanceof Projectile) {
			//DAMAGER IS PROJECTILE
			LivingEntity shooter = ((Projectile)event.getDamager()).getShooter();
			if (shooter instanceof Player && event.getEntity() instanceof Player) {
				//DAMAGER IS PLAYER - HURT IS PLAYER
				Player pShooter = (Player)shooter;
				if (plugin.isProtected(pShooter)) {
					//DAMAGER PROTECTED HITS OTHER PLAYER (PROJECTILE)
					event.setCancelled(true);
					pShooter.sendMessage(plugin.prefix + (plugin.lang("cannotHurtWhileProtected", plugin.getTimeLeft(pShooter))));
				} else {
					//HURT IS PLAYER
					Player hurtPlayer = (Player)event.getEntity();
					if (plugin.isProtected(hurtPlayer)) {
						//PLAYER HITS PROTECTED (PROJECTILE)
						event.setCancelled(true);
						pShooter.sendMessage(plugin.cannotHurt);
					}
				}
			}
		}
		
		if (event.getEntity() instanceof Player && !event.isCancelled()) {
			//HURT IS PLAYER
			Player hurtPlayer = (Player)event.getEntity();
			if (plugin.isProtected(hurtPlayer)) { //damage by entity
				//HURT IS PROTECTED, DISALLOW ALL DAMAGE IF CONFIG
				if (config.getString("damageType") == "all") event.setCancelled(true);
				else if (event.getDamager() instanceof Player) { //damage by player
					//DAMAGER PLAYER HURTS PLAYER
					event.setCancelled(true);
					Player damager = (Player)event.getDamager();
					damager.sendMessage(plugin.cannotHurt);
				}
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent event) {
		if (PvPTimer.parseTime(plugin.config.getString(TimeItemType.getConfigNode(TimeItemType.JOIN, plugin.getGroup(event.getPlayer())))) != 0) {
			Player p = event.getPlayer();
			plugin.checkPlayer(p, false);
			//plugin.log.info("Player " + p.getName() + " timestamp is " +p.getFirstPlayed() + ", current timestamp is " + System.currentTimeMillis() + ", diff is " + (System.currentTimeMillis() - p.getFirstPlayed()));
			if ((System.currentTimeMillis() - p.getFirstPlayed()) <= 2000 && !plugin.times.containsKey(p.getName())) { //New player... woo.
				plugin.addPlayer(p, p.getFirstPlayed(), TimeItemType.FIRSTJOIN);
				p.sendMessage(plugin.prefix + plugin.lang("firstTime", p.getName(), PvPTimer.parseTime(config.getString("timeAmounts.newPlayers"))));
			} else {
				if (!plugin.times.containsKey(p.getName()) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < PvPTimer.parseTime(plugin.config.getString(TimeItemType.getConfigNode(TimeItemType.JOIN, plugin.getGroup(p)))))) {
					plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.JOIN);
				}
				
				if (plugin.times.containsKey(p.getName())) p.sendMessage(plugin.prefix + plugin.lang("protected", p.getName(), plugin.getTimeLeft(p)));
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onRespawn(PlayerRespawnEvent event) {
		Player p = event.getPlayer();
		
		plugin.checkPlayer(p, false);
		
		if (!plugin.times.containsKey(p.getName()) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < PvPTimer.parseTime(plugin.config.getString(TimeItemType.getConfigNode(TimeItemType.RESPAWN, plugin.getGroup(p)))))) {
			if (PvPTimer.parseTime(config.getString(TimeItemType.getConfigNode(TimeItemType.RESPAWN, plugin.getGroup(p)))) != 0) {
				plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.RESPAWN);
				p.sendMessage(plugin.prefix + plugin.lang("respawn", p.getName(), plugin.getTimeLeft(p)));
			}
		} else {
			p.sendMessage(plugin.prefix + plugin.lang("protected", p.getName(), plugin.getTimeLeft(p)));
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (PvPTimer.parseTime(config.getString(TimeItemType.getConfigNode(TimeItemType.TELEPORT, plugin.getGroup(event.getPlayer())))) != 0) {
			Player p = event.getPlayer();
			
			plugin.checkPlayer(p, false);
			
			if (!plugin.times.containsKey(p.getName()) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < PvPTimer.parseTime(plugin.config.getString(TimeItemType.getConfigNode(TimeItemType.TELEPORT, plugin.getGroup(p)))))) {
				
				//Less than 5 blocks away :P
				if (event.getFrom().distanceSquared(event.getTo()) <= 25) return;
				
				plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.RESPAWN);
				p.sendMessage(plugin.prefix + plugin.lang("teleport", p.getName(), plugin.getTimeLeft(p)));
			}
		}
	}
	
	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent event) {
		Player p = event.getPlayer();
		
		String w = p.getWorld().getName();
		
		if (plugin.isWorldExcluded(p.getWorld())) {
			if (!plugin.isWorldExcluded(event.getFrom()) && plugin.isProtected(p))
				p.sendMessage(plugin.prefix + plugin.lang("protectionInactiveHere", p.getName(), plugin.getTimeLeft(p)));
		} else {
			if (plugin.isProtected(p)) {
				if (plugin.isWorldExcluded(event.getFrom()))
					p.sendMessage(plugin.prefix + plugin.lang("protectionActiveAgain", p.getName(), plugin.getTimeLeft(p)));
			} else {
				if (PvPTimer.parseTime(config.getString(TimeItemType.getConfigNode(TimeItemType.WORLDCHANGE, plugin.getGroup(event.getPlayer())) + w)) != 0) {
					plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.WORLDCHANGE);
					p.sendMessage(plugin.prefix + plugin.lang("worldChange", p.getName(), plugin.getTimeLeft(p)));
				}
			}
		}
	}
	
	private HashMap<String, Long> lastSent = new HashMap<String, Long>();
	private Long cap = 5000L;
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPickup(PlayerPickupItemEvent event) {
		Player p = event.getPlayer();
		if (!config.getBoolean("allowPickup") && plugin.isProtected(p)) {
			event.setCancelled(true);
			boolean send = false;
			if (lastSent.containsKey(event.getPlayer().getName())) {
				if (System.currentTimeMillis() - lastSent.get(p.getName()) > cap) send = true;
			} else send = true;
			
			if (send) {
				lastSent.put(p.getName(), System.currentTimeMillis());
				p.sendMessage(plugin.prefix + plugin.lang("noPickup", p.getName(), plugin.getTimeLeft(p)));
			}
		}
	}
	
	//Prevents opening containers
	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		Player player = (Player)event.getPlayer();
		Player invHolder = null;
		if((event.getInventory().getHolder() instanceof Player)) invHolder = (Player)event.getInventory().getHolder();
		if(invHolder != player && player != null && plugin.isProtected(player)){
			event.setCancelled(true);
			player.sendMessage(plugin.prefix + plugin.lang("noContainer", player.getName(), plugin.getTimeLeft(player)));
		}
	}
	//Prevents the animation
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChestOpen(PlayerInteractEvent event) {
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if(event.getClickedBlock().getTypeId() == 54 || event.getClickedBlock().getTypeId() ==  130) {
				Player player = (Player)event.getPlayer();
				if(player != null && plugin.isProtected(player)) {
					event.setCancelled(true);
					player.sendMessage(plugin.prefix + plugin.lang("noContainer", player.getName(), plugin.getTimeLeft(player)));
				}
			}
		}
	}
}
