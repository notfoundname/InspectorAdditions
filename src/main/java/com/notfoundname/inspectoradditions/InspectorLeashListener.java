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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InspectorLeashListener implements Listener {

    private final InspectorAdditions plugin;

    private final List<LeashBond> leashBonds = new ArrayList<>();

    public InspectorLeashListener(InspectorAdditions plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onLeashEvent(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getAttacked() instanceof Player leashedPlayer) {
            for (LeashBond leashBond : leashBonds) {
                if (leashBond.getLeashed().getUniqueId().equals(leashedPlayer.getUniqueId())
                        && leashBond.getOwner().getUniqueId().equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            plugin.getConfig().getString("Leash-PlayerUnleashed", "<player> был отвязан"),
                            Placeholder.unparsed("player", leashedPlayer.getName())));
                    leashBond.remove();
                    return;
                }
            }
            if (plugin.isSpyglass(player.getInventory().getItemInMainHand())) {
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            plugin.getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока <player>"),
                            Placeholder.unparsed("player", leashedPlayer.getName())));

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
                for (LeashBond leashBond : leashBonds) {
                    if (leashBond.getOwner().getUniqueId().equals(event.getPlayer().getUniqueId())) {
                        event.setCancelled(true);
                    }
                }
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

    public class LeashBond {

        private final Player owner;
        private final Player leashed;
        private Entity entity;
        private BukkitTask bukkitTask;

        public LeashBond(Player owner, Player leashed) {
            this.owner = owner;
            this.leashed = leashed;
            this.entity = leashed.getWorld().spawn(
                    leashed.getLocation().add(0.0, 1.0, 0.0),
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
                        entity.setMetadata("disallowUnleash", new FixedMetadataValue(plugin, "aboba"));
                    }));
            this.bukkitTask = new BukkitRunnable() {
                public void run() {
                    if (!owner.isOnline() || !leashed.isOnline() || owner.getHealth() == 0 || leashed.getHealth() == 0 || !entity.isValid()) {
                        remove();
                    }
                    entity.teleport(leashed.getLocation().add(0.0, 1.0, 0.0));
                    if (owner.getLocation().distanceSquared(leashed.getLocation()) > 10.0) {
                        leashed.setVelocity(owner.getLocation().toVector().subtract(leashed.getLocation().toVector()).multiply(0.05));
                    }
                }
            }.runTaskTimer(plugin, 0L, 0L);
            leashed.setMetadata("isLeashed", new FixedMetadataValue(plugin, "aboba"));
        }

        public Player getOwner() {
            return owner;
        }

        public Player getLeashed() {
            return leashed;
        }

        public Entity getEntity() {
            return entity;
        }

        public void remove() {
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
