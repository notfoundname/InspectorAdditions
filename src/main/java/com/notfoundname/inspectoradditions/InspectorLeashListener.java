package com.notfoundname.inspectoradditions;

import dev.geco.gsit.api.GSitAPI;
import dev.geco.gsit.api.event.PreEntitySitEvent;
import dev.geco.gsit.api.event.PrePlayerCrawlEvent;
import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import dev.geco.gsit.api.event.PrePlayerPoseEvent;
import dev.geco.gsit.objects.GetUpReason;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Tag;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InspectorLeashListener implements Listener {

    private final InspectorAdditions plugin;

    private final Set<LeashBond> leashBonds = new HashSet<>();

    public InspectorLeashListener(InspectorAdditions plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onLeashEvent(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("isLeashed")) {
            event.setCancelled(true);
            return;
        }
        if (event.getAttacked() instanceof Player leashedPlayer) {
            if (plugin.isSpyglass(player.getInventory().getItemInMainHand())) {
                if (player.isSneaking()) {
                    for (LeashBond leashBond : leashBonds) {
                        if (leashBond.getLeashed().getUniqueId().equals(leashedPlayer.getUniqueId())
                                && leashBond.getOwner().getUniqueId().equals(player.getUniqueId())) {
                            event.setCancelled(true);
                            leashBond.remove();
                            return;
                        }
                    }
                    event.setCancelled(true);

                    // GSit shit
                    GSitAPI.stopPlayerSit(leashedPlayer, GetUpReason.KICKED);
                    GSitAPI.stopCrawl(leashedPlayer, GetUpReason.KICKED);
                    GSitAPI.removePose(leashedPlayer, GetUpReason.KICKED);
                    GSitAPI.removeSeat(leashedPlayer, GetUpReason.KICKED);

                    leashBonds.add(new LeashBond(player, leashedPlayer));
                }
            }
        }
    }

    @EventHandler
    public void onHangingPlaceEvent(HangingPlaceEvent event) {
        if (event.getEntity() instanceof LeashHitch) {
            if (event.getPlayer() != null) {
                event.setCancelled(hasLeashBond(event.getPlayer()));
            }
        }
    }

    @EventHandler
    public void onUnleashSlimeEvent(EntityUnleashEvent event) {
        if (event.getEntity() instanceof Slime slime) {
            if (slime.hasMetadata("disallowUnleash")) {
                event.setDropLeash(false);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerTeleportEvent(PlayerTeleportEvent event) {
        if (event.getPlayer().hasMetadata("isLeashed")) {
            switch (event.getCause()) {
                case ENDER_PEARL,END_GATEWAY,END_PORTAL,NETHER_PORTAL,CHORUS_FRUIT,COMMAND -> event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityTeleportPortalEvent(EntityPortalEvent event) {
        event.setCancelled(event.getEntity().hasMetadata("disallowUnleash"));
    }

    @EventHandler
    public void onLeashedInteractEvent(PlayerInteractEvent event) {
        if (event.getPlayer().hasMetadata("isLeashed")) {
            event.setCancelled(true);
        }
        if (hasLeashBond(event.getPlayer())) {
            if (event.getClickedBlock() != null) {
                event.setCancelled(Tag.FENCES.isTagged(event.getClickedBlock().getType()));
            }
        }
    }

    @EventHandler
    public void onLeashedInteractEntityEvent(PlayerInteractEntityEvent event) {
        if (event.getPlayer().hasMetadata("isLeashed")) {
            event.setCancelled(true);
            return;
        }
        if (hasLeashBond(event.getPlayer())) {
            event.setCancelled(event.getRightClicked() instanceof LeashHitch);
        }
    }

    // GSit shit
    @EventHandler
    public void onLeashPlayerSit(PrePlayerPlayerSitEvent event) {
        event.setCancelled(event.getPlayer().hasMetadata("isLeashed") || event.getTarget().hasMetadata("isLeashed"));
    }

    // GSit shit
    @EventHandler
    public void onLeashSit(PreEntitySitEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(player.hasMetadata("isLeashed"));
        }
    }

    // GSit shit
    @EventHandler
    public void onLeashCrawl(PrePlayerCrawlEvent event) {
        event.setCancelled(event.getPlayer().hasMetadata("isLeashed"));
    }

    // GSit shit
    @EventHandler
    public void onLeashPose(PrePlayerPoseEvent event) {
        event.setCancelled(event.getPlayer().hasMetadata("isLeashed"));
    }

    public boolean hasLeashBond(Player owner) {
        for (LeashBond leashBond : leashBonds) {
            if (leashBond.getOwner().getUniqueId().equals(owner.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public class LeashBond {

        private final Player owner;
        private final Player leashed;
        private final Entity entity;
        private final BukkitTask bukkitTask;

        public LeashBond(Player owner, Player leashed) {
            this.owner = owner;
            this.leashed = leashed;
            this.entity = leashed.getWorld().spawn(
                    leashed.getLocation().add(0.0, 0.85, 0.0),
                    Slime.class,
                    (entity -> {
                        entity.setSize(0);
                        entity.setAI(false);
                        entity.setGravity(false);
                        entity.setLeashHolder(owner);
                        entity.setInvisible(true);
                        entity.setInvulnerable(true);
                        entity.setVisualFire(false);
                        entity.setSilent(true);
                        entity.setCollidable(false);
                        entity.setMetadata("disallowUnleash", new FixedMetadataValue(plugin, true));
                    }));
            this.bukkitTask = new BukkitRunnable() {
                public void run() {
                    if (!owner.isOnline() || !leashed.isOnline() || owner.getHealth() == 0 || leashed.getHealth() == 0 || !entity.isValid()) {
                        remove();
                        return;
                    }
                    if (!leashed.getWorld().equals(owner.getWorld()) || !entity.getWorld().equals(leashed.getWorld())) {
                        ((Slime) entity).setLeashHolder(null);
                        leashed.teleport(owner.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        entity.teleport(leashed.getLocation().add(0.0, 0.85, 0.0), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        ((Slime) entity).setLeashHolder(owner);
                    } else {
                        double distance = owner.getLocation().distanceSquared(leashed.getLocation());
                        if (distance >= 12.0 && distance < 200.0) {
                            leashed.getLocation().distanceSquared(owner.getLocation());
                            leashed.setVelocity(owner.getLocation().toVector().subtract(leashed.getLocation().toVector())
                                    .multiply(owner.getLocation().distanceSquared(leashed.getLocation()) * 0.005));
                        }
                        if (distance >= 200.0) {
                            leashed.teleportAsync(owner.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        }
                        entity.teleportAsync(leashed.getLocation().add(0.0, 0.85, 0.0), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    leashed.setFallDistance(0);
                }
            }.runTaskTimer(plugin, 0L, 0L);

            owner.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока <player>"),
                    Placeholder.unparsed("player", leashed.getName())));
            leashed.setMetadata("isLeashed", new FixedMetadataValue(plugin, true));
        }

        public Player getOwner() {
            return owner;
        }

        public Player getLeashed() {
            return leashed;
        }

        public void remove() {
            owner.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("Leash-PlayerUnleashed", "<player> был отвязан"),
                    Placeholder.unparsed("player", leashed.getName())));
            if (entity instanceof Slime slime) {
                slime.setLeashHolder(null);
            }
            entity.remove();
            leashed.removeMetadata("isLeashed", plugin);
            bukkitTask.cancel();
            leashBonds.remove(this);
        }
    }
}
