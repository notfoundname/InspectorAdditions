package com.notfoundname.inspectoradditions;

import dev.geco.gsit.api.GSitAPI;
import dev.geco.gsit.api.event.PreEntitySitEvent;
import dev.geco.gsit.api.event.PrePlayerCrawlEvent;
import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import dev.geco.gsit.api.event.PrePlayerPoseEvent;
import dev.geco.gsit.objects.GetUpReason;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class InspectorLeashListener implements Listener {

    private final InspectorAdditions plugin;

    /**
     * List of leashed players
     */
    List<UUID> leashed = new ArrayList<>();

    public InspectorLeashListener(InspectorAdditions plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onLeashEvent(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getAttacked() instanceof Player leashedPlayer) {
            if (leashed.contains(leashedPlayer.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("Leash-PlayerUnleashed", "<player> был отвязан"),
                        Placeholder.unparsed("player", leashedPlayer.getName())));
                unleashPlayer(leashedPlayer);
                return;
            }

            if (plugin.isSpyglass(player.getInventory().getItemInMainHand()) && player.isSneaking()) {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока <player>"),
                        Placeholder.unparsed("player", leashedPlayer.getName())));
                // GSit shit
                GSitAPI.stopPlayerSit(leashedPlayer, GetUpReason.KICKED);
                GSitAPI.stopCrawl(leashedPlayer, GetUpReason.KICKED);
                GSitAPI.removePose(leashedPlayer, GetUpReason.KICKED);
                GSitAPI.removeSeat(leashedPlayer, GetUpReason.KICKED);

                leashed.add(leashedPlayer.getUniqueId());
                spawn(player, leashedPlayer);

                new BukkitRunnable() {
                    public void run() {
                        if (!leashed.contains(leashedPlayer.getUniqueId())) {
                            cancel();
                        }
                        if (player.getLocation().distanceSquared(leashedPlayer.getLocation()) > 10.0) {
                            leashedPlayer.setVelocity(player.getLocation().toVector().subtract(leashedPlayer.getLocation().toVector()).multiply(0.05));
                        }
                    }
                }.runTaskTimer(plugin, 0L, 0L);
            }
        }
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        if (leashed.contains(event.getPlayer().getUniqueId())) {
            Player leashedPlayer = event.getPlayer();
            for (Entity entity : leashedPlayer.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof Slime && entity.hasMetadata(leashedPlayer.getUniqueId().toString())) {
                    entity.teleport(leashedPlayer.getLocation().add(0.0, 1.0, 0.0),
                            TeleportFlag.Relative.YAW,
                            TeleportFlag.Relative.PITCH);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        unleashAllRelated(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        unleashAllRelated(event.getPlayer());
    }

    @EventHandler
    public void onHangingPlaceEvent(HangingPlaceEvent event) {
        if (event.getEntity() instanceof LeashHitch leash) {
            for (Entity entity : leash.getNearbyEntities(7.0, 7.0, 7.0)) {
                if (leashed.contains(entity.getUniqueId())) {
                    event.setCancelled(true);
                    return;
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

    private void spawn(Player player, Player target) {
        target.getWorld().spawn(
                target.getLocation().add(0.0, 1.0, 0.0),
                Slime.class,
                (entity -> {
                    entity.setSize(0);
                    entity.setAI(false);
                    entity.setGravity(false);
                    entity.setLeashHolder(player);
                    entity.setInvisible(true);
                    entity.setInvulnerable(true);
                    entity.setVisualFire(false);
                    entity.setSilent(true);
                    entity.setCollidable(false);
                    entity.setMetadata(target.getUniqueId().toString(), new FixedMetadataValue(plugin, "abobik"));
                    entity.setMetadata("disallowUnleash", new FixedMetadataValue(plugin, "apopik"));
                }));
        target.setMetadata(target.getUniqueId().toString(), new FixedMetadataValue(plugin,"aboba"));
        target.setAllowFlight(true);
    }

    private void unleashPlayer(Player target) {
        for (Entity entity : target.getNearbyEntities(1.0, 1.0, 1.0)) {
            if (entity instanceof Slime slime) {
                if (slime.hasMetadata(target.getUniqueId().toString())) {
                    slime.setLeashHolder(null);
                    slime.remove();
                    leashed.remove(target.getUniqueId());
                    target.setAllowFlight(false);
                }
            }
        }
    }

    private void unleashAllRelated(Player target) {
        if (leashed.contains(target.getUniqueId())) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasMetadata(target.getUniqueId().toString())) {
                    unleashPlayer(target);
                }
            }
        }
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashPlayerSit(PrePlayerPlayerSitEvent event) {
        event.setCancelled(leashed.contains(event.getTarget().getUniqueId()));
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashSit(PreEntitySitEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(leashed.contains(player.getUniqueId()));
        }
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashCrawl(PrePlayerCrawlEvent event) {
        event.setCancelled(leashed.contains(event.getPlayer().getUniqueId()));
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashPose(PrePlayerPoseEvent event) {
        event.setCancelled(leashed.contains(event.getPlayer().getUniqueId()));
    }
}
