package com.notfoundname.inspectoradditions;

import dev.geco.gsit.api.GSitAPI;
import dev.geco.gsit.api.event.PreEntitySitEvent;
import dev.geco.gsit.api.event.PrePlayerCrawlEvent;
import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import dev.geco.gsit.api.event.PrePlayerPoseEvent;
import dev.geco.gsit.objects.GetUpReason;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
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

    // Every player has multiple leashed entities
    Map<Player, List<LivingEntity>> playersAndLeashed = new HashMap<>();
    // Every leashed player has one invisible entity
    Map<LivingEntity, Player> playersAttachedToEntity = new HashMap<>();

    Map<UUID, InspectorInventory> openedInventories = new HashMap<>();

    public static final ItemStack pageForwardItemStack = new ItemStack(Material.ARROW);
    public static final ItemStack pageBackItemStack = new ItemStack(Material.ARROW);

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

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

    // PLAYER LEASHING

    public boolean isSpyglass(ItemStack playerItem) {
        return playerItem != null
                && playerItem.getType() != Material.AIR
                && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)
                && MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(
                        getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"));
    }

    @EventHandler
    public void onSpyglassClick(InventoryClickEvent event) {
        assert event.getClickedInventory() != null;
        if (event.getClickedInventory().getType() == InventoryType.ENDER_CHEST) {
            ItemStack itemStack = event.getCursor();
            switch (event.getClick()) {
                case SWAP_OFFHAND -> itemStack = event.getWhoClicked().getInventory().getItemInOffHand();
                case NUMBER_KEY -> itemStack = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            }
            assert itemStack != null;
            if (itemStack.getType() == Material.BUNDLE) {
                if (((BundleMeta) itemStack.getItemMeta()).hasItems()) {
                    for (ItemStack item : ((BundleMeta) itemStack.getItemMeta()).getItems()) {
                        if (isSpyglass(item)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            } else if (itemStack.hasItemMeta() && itemStack.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
                if (blockStateMeta.getBlockState() instanceof Container container) {
                    for (ItemStack item : container.getSnapshotInventory().getContents()) {
                        if (isSpyglass(item)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            } else if (isSpyglass(itemStack)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onLeashAttempt(PrePlayerAttackEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (event.getAttacked() instanceof Player target) {
            Player player = event.getPlayer();
            if (playersAttachedToEntity.containsValue(target)) return;
            if (isSpyglass(player.getInventory().getItemInMainHand())) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        getConfig().getString("Leash-PlayerLeashed", "Вы привязали игрока <player>"),
                        Placeholder.unparsed("player", target.getName())));

                if (playersAndLeashed.get(event.getPlayer()).contains(target)) {
                    playersAndLeashed.get(event.getPlayer()).remove(target);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            getConfig().getString("Leash-PlayerUnleashed", "<player> был отвязан"),
                            Placeholder.unparsed("player", target.getName())));
                    return;
                }

                LivingEntity entity = target.getWorld().spawn(target.getLocation(), Zombie.class, zombie -> {
                    zombie.getEquipment().setItemInMainHand(null);
                    zombie.getEquipment().setHelmet(null);
                    zombie.getEquipment().setChestplate(null);
                    zombie.getEquipment().setLeggings(null);
                    zombie.getEquipment().setBoots(null);
                    zombie.setCanPickupItems(false);
                    zombie.setAdult();
                    if (zombie.getVehicle() != null)
                        zombie.getVehicle().remove();
                    zombie.setSilent(true);
                    zombie.setInvisible(true);
                    zombie.setCollidable(false);
                    zombie.setInvulnerable(true);
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));
                    zombie.setLeashHolder(player);
                });

                target.setAllowFlight(true);
                playersAttachedToEntity.put(entity, target);

                if (playersAndLeashed.containsKey(player)) {
                    playersAndLeashed.get(player).add(entity);
                } else {
                    playersAndLeashed.put(player, List.of(entity));
                }

                // GSit shit
                GSitAPI.stopPlayerSit(target, GetUpReason.KICKED);
                GSitAPI.stopCrawl(target, GetUpReason.KICKED);
                GSitAPI.removePose(target, GetUpReason.KICKED);
                GSitAPI.removeSeat(target, GetUpReason.KICKED);

                new BukkitRunnable() {
                    public void run() {
                        if (!target.isOnline() || !entity.isValid() || !entity.isLeashed() || !playersAttachedToEntity.containsValue(target)) {
                            playersAndLeashed.get(player).remove(target);
                            if (playersAndLeashed.get(player).isEmpty()) {
                                playersAndLeashed.remove(player);
                            }
                            playersAttachedToEntity.remove(entity);
                            entity.remove();
                            target.setAllowFlight(false);
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

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            if (playersAttachedToEntity.containsKey(entity)) {
                event.setDropLeash(false);
            }
        }
    }

    @EventHandler
    public void onFlame(EntityCombustEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            event.setCancelled(playersAttachedToEntity.containsKey(entity));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity entity) {
            event.setCancelled(playersAttachedToEntity.containsKey(entity));
        }
    }

    @EventHandler
    public void onLeashingToFence(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType().toString().endsWith("FENCE")) {
                event.setCancelled(playersAndLeashed.containsKey(event.getPlayer()));
            }
        }
    }

    // Disallow any interaction by a leashed player
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashInteract(PlayerInteractEvent event) {
        event.setCancelled(playersAttachedToEntity.containsValue(event.getPlayer()));
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashPlayerSit(PrePlayerPlayerSitEvent event) {
        event.setCancelled(playersAttachedToEntity.containsValue(event.getTarget()) || playersAttachedToEntity.containsValue(event.getPlayer()));
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashSit(PreEntitySitEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(playersAttachedToEntity.containsValue(player));
        }
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashCrawl(PrePlayerCrawlEvent event) {
        event.setCancelled(playersAttachedToEntity.containsValue(event.getPlayer()));
    }

    // GSit shit
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeashPose(PrePlayerPoseEvent event) {
        event.setCancelled(playersAttachedToEntity.containsValue(event.getPlayer()));
    }

    // COREPROTECT GUI

    @EventHandler
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isSpyglass(player.getInventory().getItemInMainHand())) {
            List<String[]> data;
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR:
                    data = coreProtectAPI.performLookup(31104000, null, null, null, null,
                            Arrays.asList(0, 1, 2, 3),
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

    @EventHandler
    public void onInspectorInventoryClick(InventoryClickEvent event) {
        assert event.getClickedInventory() != null;
        if (event.getClickedInventory().getHolder(false) instanceof InspectorInventory inspectorInventory) {
            getLogger().info("onInspectorInventoryClick");
            event.setCancelled(true);
            if (!openedInventories.containsKey(event.getWhoClicked().getUniqueId())
                     || event.getCurrentItem() == null) {
                return;
            }
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
    }

    @EventHandler
    public void onInspectorInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof InspectorInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof InspectorInventory) {
            openedInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    public static InspectorAdditions getInstance() {
        return instance;
    }

    public CoreProtectAPI getCoreProtectAPI() {
        return coreProtectAPI;
    }
}