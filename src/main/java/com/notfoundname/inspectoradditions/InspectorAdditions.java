package com.notfoundname.inspectoradditions;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class InspectorAdditions extends JavaPlugin implements Listener {

    private static InspectorAdditions instance;
    private CoreProtectAPI coreProtectAPI;

    List<Player> leashed = new ArrayList<>();
    List<LivingEntity> entityList = new ArrayList<>();
    List<Entity> distanceUnleash = new ArrayList<>();

    Map<UUID, InspectorInventory> openedInventories = new HashMap<>();

    public static final ItemStack pageForwardItemStack = new ItemStack(Material.ARROW);
    public static final ItemStack pageBackItemStack = new ItemStack(Material.ARROW);

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);

        saveResource("config.yml", getConfig().getInt("ConfigVersion") != 1);

        ItemMeta pageForwardMeta = pageForwardItemStack.getItemMeta();
        pageForwardMeta.displayName(
                MiniMessage.miniMessage().deserialize(
                        InspectorAdditions.getInstance().getConfig().getString("CoreProtect-PageForward", "Вперёд")));
        pageForwardItemStack.setItemMeta(pageForwardMeta);

        ItemMeta pageBackMeta = pageBackItemStack.getItemMeta();
        pageBackMeta.displayName(
                MiniMessage.miniMessage().deserialize(
                        InspectorAdditions.getInstance().getConfig().getString("CoreProtect-PageBack", "Назад")));
        pageBackItemStack.setItemMeta(pageBackMeta);

        try {
            Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
            if (plugin != null && plugin.isEnabled()) {
                coreProtectAPI = ((CoreProtect) plugin).getAPI();
            }
        } catch (Exception exception) {
            for (StackTraceElement stackTraceElement : exception.getStackTrace()) {
                getLogger().severe(stackTraceElement.toString());
            }
        }
    }

    @EventHandler
    public void onLeash(PrePlayerAttackEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (leashed.contains(event.getPlayer())) return;
        if (event.getAttacked() instanceof Player target) {
            Player player = event.getPlayer();
            ItemStack playerItem = player.getInventory().getItemInMainHand();
            if (playerItem.getType() != Material.AIR && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)) {
                if (MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(
                        getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"))) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока <player>"),
                            Placeholder.unparsed("player", target.getName())));
                    if (leashed.contains(target)) {
                        leashed.remove(target);
                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                                getConfig().getString("Leash-PlayerUnleashed", "<player> был отвязан"),
                                Placeholder.unparsed("player", target.getName())));
                        return;
                    }

                    LivingEntity entity = target.getWorld().spawn(target.getLocation(), Zombie.class, zombie -> {
                        zombie.getEquipment().setItemInMainHand(null);
                        zombie.getEquipment().setHelmet(null);
                        zombie.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                        zombie.getEquipment().setLeggings(null);
                        zombie.getEquipment().setBoots(null);
                        zombie.setCanPickupItems(false);
                        zombie.setAdult();
                        if(zombie.getVehicle() != null)
                            zombie.getVehicle().remove();
                        zombie.setSilent(true);
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
                            if (!target.isOnline() || !entity.isValid() || !entity.isLeashed() || !leashed.contains(target)) {
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
                            location.setYaw(target.getYaw()); // probably not needed
                            location.setPitch(target.getPitch()); // probably not needed
                            target.teleport(location,
                                    PlayerTeleportEvent.TeleportCause.UNKNOWN,
                                    TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY,
                                    TeleportFlag.Relative.YAW,
                                    TeleportFlag.Relative.PITCH);
                        }
                    }.runTaskTimer(instance,0, 1);
                }
            }
        }
    }

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            if (entityList.contains((LivingEntity) event.getEntity())) {
                distanceUnleash.add(event.getEntity());
                event.setDropLeash(false);
            }
        }
    }

    @EventHandler
    public void onFlame(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (entityList.contains((LivingEntity) event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity)) return;
        if (entityList.contains((LivingEntity) event.getDamager())) event.setCancelled(true);
    }

    // Disallow any interaction by a leashed player
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashInteract(PlayerInteractEvent event) {
        event.setCancelled(leashed.contains(event.getPlayer()));
    }

    @EventHandler
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack playerItem = player.getInventory().getItemInMainHand();
        if (playerItem.getType() != Material.AIR && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)) {
            if (MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(
                    getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"))) {
                List<String[]> data;
                switch (event.getAction()) {
                    case RIGHT_CLICK_AIR:
                        data = coreProtectAPI.performLookup(31104000, null, null, null, null,
                                List.of(0, 1, 2, 3, 4, 5),
                                getConfig().getInt("CoreProtect-Radius", 10), player.getLocation());
                        break;
                    case RIGHT_CLICK_BLOCK:
                        if (event.getClickedBlock() != null && event.getClickedBlock().getState() instanceof InventoryHolder) {
                            data = coreProtectAPI.containerTransactionsLookup(event.getClickedBlock().getLocation(), 0);
                        } else {
                            data = coreProtectAPI.blockLookup(event.getClickedBlock(), 0);
                        }
                        break;
                    default:
                        return;
                }
                if (data != null && !data.isEmpty()) {
                    event.setCancelled(true);
                    openedInventories.put(player.getUniqueId(), new InspectorInventory(instance, data));
                    player.openInventory(openedInventories.get(player.getUniqueId()).getInventory());
                } else {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            getConfig().getString("CoreProtect-NoHistory", "Нет истории")));
                }
            }
        }
    }

    @EventHandler
    public void onInspectorInventoryModify(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof InspectorInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder(false) instanceof InspectorInventory)) {
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        if (!openedInventories.containsKey(event.getWhoClicked().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        InspectorInventory inspectorInventory = openedInventories.get(event.getWhoClicked().getUniqueId());

        if (event.getCurrentItem().equals(pageForwardItemStack)) {
            inspectorInventory.setCurrentPage(inspectorInventory.getCurrentPage() + 1);
        } else if (event.getCurrentItem().equals(pageBackItemStack)) {
            inspectorInventory.setCurrentPage(inspectorInventory.getCurrentPage() - 1);
        } else {
            return;
        }

        inspectorInventory.getInventory().clear();
        inspectorInventory.fill();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof InspectorInventory) {
            getLogger().info("Deleting " + event.getInventory().getHolder());
            openedInventories.remove(event.getPlayer().getUniqueId()).close();
        }
    }

    public static InspectorAdditions getInstance() {
        return instance;
    }

    public CoreProtectAPI getCoreProtectAPI() {
        return coreProtectAPI;
    }
}