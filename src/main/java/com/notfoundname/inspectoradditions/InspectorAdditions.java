package com.notfoundname.inspectoradditions;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.database.lookup.InteractionLookup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InspectorAdditions extends JavaPlugin implements Listener {

    private static InspectorAdditions instance;
    private CoreProtectAPI coreProtectAPI;

    List<Player> leashed = new ArrayList<>();
    List<LivingEntity> entityList = new ArrayList<>();
    List<Entity> distanceUnleash = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        try {
            Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
            if (plugin != null && plugin.isEnabled()) {
                coreProtectAPI = ((CoreProtect) plugin).getAPI();
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @EventHandler
    public void onLeash(PrePlayerAttackEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (event.getAttacked() instanceof Player) {
            Player player = event.getPlayer();
            Player target = (Player) event.getAttacked();
            ItemStack playerItem = player.getInventory().getItemInMainHand();

            if (target.getGameMode() != GameMode.SURVIVAL) return;

            if (playerItem.getType() != Material.AIR && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)) {
                if (MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"))) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока %player%")
                                    .replace("%player%", target.getName())));
                    if (leashed.contains(target)) {
                        leashed.remove(target);
                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                                getConfig().getString("Leash-PlayerUnleashed", "%player% был отвязан")
                                        .replace("%player%", target.getName())));
                        return;
                    }

                    LivingEntity entity = target.getWorld().spawn(target.getLocation(), Zombie.class, zombie -> {
                        zombie.getEquipment().setItemInMainHand(null);
                        zombie.getEquipment().setHelmet(null);
                        zombie.getEquipment().setChestplate(null);
                        zombie.getEquipment().setLeggings(null);
                        zombie.getEquipment().setBoots(null);
                        zombie.setCanPickupItems(false);
                        zombie.setShouldBurnInDay(false);
                        zombie.setAdult();
                        if(zombie.getVehicle() != null)
                            zombie.getVehicle().remove();
                        zombie.setSilent(true);
                        zombie.setAI(false);
                        zombie.setInvisible(true);
                        zombie.setCollidable(false);
                        zombie.setInvulnerable(true);
                        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));
                        zombie.setLeashHolder(player);
                    });

                    target.setAllowFlight(true);
                    leashed.add(target);
                    entityList.add(entity);

                    new BukkitRunnable() {
                        public void run() {
                            if (!target.isOnline() || !entity.isValid() || !entity.isLeashed() || !leashed.contains(target) || target.getGameMode() != GameMode.SURVIVAL) {
                                leashed.remove(target);
                                entityList.remove(entity);
                                entity.remove();
                                target.setAllowFlight(false);
                                distanceUnleash.remove(entity);
                                cancel();
                            }
                            Location location = target.getLocation();
                            location.setX(entity.getLocation().getX());
                            location.setY(entity.getLocation().getY());
                            location.setZ(entity.getLocation().getZ());
                            target.teleport(location, PlayerTeleportEvent.TeleportCause.UNKNOWN);
                        }
                    }.runTaskTimer(instance,0, 1);
                }
            }
        }
    }

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        if (event.getReason() == EntityUnleashEvent.UnleashReason.PLAYER_UNLEASH) return;
        distanceUnleash.add(event.getEntity());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack playerItem = player.getInventory().getItemInMainHand();
        if (playerItem.getType() != Material.AIR && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)) {
            if (MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"))) {
                List<String[]> data;
                switch (event.getAction()) {
                    case RIGHT_CLICK_AIR:
                        data = coreProtectAPI.performLookup(31104000, null, null, null, null, null, 15, player.getLocation());
                        if (data != null && !data.isEmpty()) {
                            new InspectorInventory(instance, data, player);
                        } else {
                            player.sendMessage(MiniMessage.miniMessage().deserialize(
                                    getConfig().getString("CoreProtect-NoHistory", "Нет истории")));
                        }
                        break;
                    case RIGHT_CLICK_BLOCK:
                        data = coreProtectAPI.blockLookup(event.getClickedBlock(), 0);
                        if (data != null && !data.isEmpty()) {
                            event.setCancelled(true);
                            new InspectorInventory(instance, data, player);
                        } else {
                            player.sendMessage(MiniMessage.miniMessage().deserialize(
                                    getConfig().getString("CoreProtect-NoHistory", "Нет истории")));
                        }
                        break;
                }
            }
        }
    }

    public static InspectorAdditions getInstance() {
        return instance;
    }

    public CoreProtectAPI getCoreProtectAPI() {
        return coreProtectAPI;
    }
}