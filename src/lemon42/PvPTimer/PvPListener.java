package lemon42.PvPTimer;

import java.util.HashMap;

import lemon42.PvPTimer.TimeItem.TimeItemType;
import lemon42.PvPTimer.integration.WorldGuard;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;

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
	
	public boolean checkWorld(EntityEvent event) {
		return (event.getEntity() instanceof Player || event.getEntityType() == EntityType.PLAYER)
				&& plugin.isWorldExcluded(event.getEntity().getWorld());
	}
	public boolean checkWorld(PlayerEvent event) {
		return plugin.isWorldExcluded(event.getPlayer().getWorld());
	}
	public boolean checkWorld(InventoryOpenEvent event) {
		return plugin.isWorldExcluded(event.getPlayer().getWorld());
	}
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPvP(EntityDamageByEntityEvent event) {
		if (checkWorld(event)) return;
		

		if (event.getDamager() instanceof Player) {
			//DAMAGER IS PLAYER
			Player damager = (Player)event.getDamager();
			if (plugin.isProtected(damager)) {
				if (event.getEntity() instanceof Player) {
					//DAMAGER PROTECTED HITS PLAYER		
					event.setCancelled(true);
					damager.sendMessage(plugin.prefix + plugin.lang("cannotHurtWhileProtected", plugin.getTimeLeft((Player)event.getEntity())));
				}
			}
		} else if (event.getDamager() instanceof Projectile) {
			//DAMAGER IS PROJECTILE
			LivingEntity shooter = (LivingEntity) ((Projectile)event.getDamager()).getShooter();
			if (shooter instanceof Player && event.getEntity() instanceof Player) {
				//DAMAGER IS PLAYER - HURT IS PLAYER
				Player pShooter = (Player)shooter;
				
				if(pShooter.getName().equals(((Player)event.getEntity()).getName())) {
					//same player => enderpearl!
					return;
				}
				
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
				if (config.getString("damageType").equalsIgnoreCase("all")) event.setCancelled(true);
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
		if (checkWorld(event)) return;
		
				
		Player p = event.getPlayer();
		
		if(plugin.isTimeout(p)) return;
		plugin.checkPlayer(p, false);
		//plugin.log.info("Player " + p.getName() + " timestamp is " +p.getFirstPlayed() + ", current timestamp is " + System.currentTimeMillis() + ", diff is " + (System.currentTimeMillis() - p.getFirstPlayed()));
		if ((System.currentTimeMillis() - p.getFirstPlayed()) <= 2000 && !plugin.times.containsKey(p.getName())) { //New player... woo.
			if(config.getTime(TimeItemType.getConfigNode(TimeItemType.FIRSTJOIN, plugin.getGroup(event.getPlayer()))) == 0) return;
			
			plugin.addPlayer(p, p.getFirstPlayed(), TimeItemType.FIRSTJOIN);
			
			//Handles join message delay
			new BukkitRunnable() {
				Player p;
				public BukkitRunnable init(Player player) {
					p = player;
					return this;
				}
				
	            @Override
	            public void run() {
	            	p.sendMessage(plugin.prefix + plugin.lang("firstTime", p.getName(), config.getTime("timeAmounts.newPlayers")));
	            }	 
	        }.init(p).runTaskLater(this.plugin, config.getTime("joinMessageDelay") / 1000 * 20);
		} else {
			if(config.getTime(TimeItemType.getConfigNode(TimeItemType.JOIN, plugin.getGroup(event.getPlayer()))) == 0) return;
			
			if (!plugin.times.containsKey(p.getName()) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < config.getTime(TimeItemType.getConfigNode(TimeItemType.JOIN, plugin.getGroup(p))))) {
				plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.JOIN);
			}
			
			if (plugin.times.containsKey(p.getName())) p.sendMessage(plugin.prefix + plugin.lang("protected", p.getName(), plugin.getTimeLeft(p)));
		}
		
		//Update notification!
		if(plugin.updater.isUpdateNeeded() && p.hasPermission("PvPTimer.updateNotify"))
			p.sendMessage(plugin.prefix + plugin.lang("updateAvailable").replace("%link%", plugin.updater.getLink()));
	}

	@EventHandler(ignoreCancelled = true)
	public void onRespawn(PlayerRespawnEvent event) {
		if (checkWorld(event)) return;
		
		Player p = event.getPlayer();
		if(plugin.isTimeout(p)) return;
		plugin.checkPlayer(p, false);
		
		//suicide protection + other entities
		if(!config.getBoolean("allowNonPlayerDeath"))
			if(p.getKiller() == null || p.getKiller() == p) return;
		
		if (!plugin.isProtected(p) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < config.getTime(TimeItemType.getConfigNode(TimeItemType.RESPAWN, plugin.getGroup(p))))) {
			if (config.getTime(TimeItemType.getConfigNode(TimeItemType.RESPAWN, plugin.getGroup(p))) != 0) {
				plugin.addPlayer(p, System.currentTimeMillis(), TimeItemType.RESPAWN);
				p.sendMessage(plugin.prefix + plugin.lang("respawn", p.getName(), plugin.getTimeLeft(p)));
			}
		} else {
			p.sendMessage(plugin.prefix + plugin.lang("protected", p.getName(), plugin.getTimeLeft(p)));
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (checkWorld(event)) return;
		
		//Enderpearl check
		if(event.getCause() == TeleportCause.ENDER_PEARL && !config.getBoolean("allowEnderpearl")) return;
		
		if (config.getTime(TimeItemType.getConfigNode(TimeItemType.TELEPORT, plugin.getGroup(event.getPlayer()))) != 0) {
			Player p = event.getPlayer();
			if(plugin.isTimeout(p)) return;
			
			//Already handled by other event
			if(event.getFrom().getWorld() != event.getTo().getWorld()) return;
			
			plugin.checkPlayer(p, false);
			
			if (!plugin.times.containsKey(p.getName()) || (plugin.times.get(p.getName()).getEndTime() - System.currentTimeMillis() < config.getTime(TimeItemType.getConfigNode(TimeItemType.TELEPORT, plugin.getGroup(p))))) {
				
				//Less than 5 blocks away :P
				//Distance squared is faster :)
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
				if(plugin.isTimeout(p)) return;
				
				if (config.getTime(TimeItemType.getConfigNode(TimeItemType.WORLDCHANGE, plugin.getGroup(event.getPlayer())) + w) != 0) {
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
		if (checkWorld(event)) return;
		if(config.getBoolean("allowPickup")) return;
		
		Player p = event.getPlayer();
		if (plugin.isProtected(p)) {
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
		if (checkWorld(event)) return;
		if(config.getBoolean("allowContainer")) return;
		
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
		if (checkWorld(event)) return;
		if(config.getBoolean("allowContainer")) return;
		
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if(event.getClickedBlock().getType() == Material.CHEST ||
					event.getClickedBlock().getType() == Material.ENDER_CHEST ||
					event.getClickedBlock().getType() == Material.TRAPPED_CHEST) {
				Player player = (Player)event.getPlayer();
				if(player != null && plugin.isProtected(player)) {
					event.setCancelled(true);
					player.sendMessage(plugin.prefix + plugin.lang("noContainer", player.getName(), plugin.getTimeLeft(player)));
				}
			}
		}
	}
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (this.isSameBlockLocation(event.getTo(), event.getFrom())) return; // Player didn't move a full block
		if (plugin.blockedRegionIds.isEmpty()) return; // No region ids are blocked
		if (!plugin.isProtected(player)) return; // Player isn't protected
		
		boolean cancelled = WorldGuard.isInBlockedRegion(event.getTo());
		event.setCancelled(cancelled);
		if (cancelled) {
			player.sendMessage(plugin.lang("attemptEnterBlockedRegion"));
		}
	}
	
	private boolean isSameBlockLocation(Location loc1, Location loc2) {
		return loc1.getBlockX() == loc2.getBlockX() && loc1.getBlockY() == loc2.getBlockY() && loc1.getBlockZ() == loc2.getBlockZ();
	}
}
