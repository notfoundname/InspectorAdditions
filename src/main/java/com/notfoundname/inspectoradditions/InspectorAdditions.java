package com.notfoundname.inspectoradditions;

import io.lumine.mythic.bukkit.MythicBukkit;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class InspectorAdditions extends JavaPlugin implements Listener {

    private static InspectorAdditions instance;
    private static InspectorLeashListener inspectorLeashListener;
    private CoreProtectAPI coreProtectAPI;

    Map<UUID, InspectorInventory> openedInventories = new HashMap<>();

    public static final ItemStack pageForwardItemStack = new ItemStack(Material.ARROW);
    public static final ItemStack pageBackItemStack = new ItemStack(Material.ARROW);

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        inspectorLeashListener = new InspectorLeashListener(this);
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

    // SPYGLASS DISALLOW ENDERCHEST

    public boolean isSpyglass(ItemStack playerItem) {
        return playerItem != null
                && playerItem.getType() != Material.AIR
                && playerItem.hasItemMeta()
                && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)
                && MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(
                        getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"));
    }

    @EventHandler
    public void onSpyglassEnderChestOpenEvent(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (!(event.getInventory().getHolder(false) instanceof InspectorInventory)) {
                if (event.getInventory().equals(event.getPlayer().getEnderChest())) {
                    for (ItemStack itemStack : event.getInventory().getContents()) {
                        if (itemStack == null) continue;
                        if (itemStack.getType() == Material.BUNDLE) {
                            if (((BundleMeta) itemStack.getItemMeta()).hasItems()) {
                                ((BundleMeta) itemStack.getItemMeta()).getItems().forEach(item -> {
                                    if (isSpyglass(item)) {
                                        event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
                                        event.getInventory().remove(itemStack);
                                        Component itemName = Component.translatable(itemStack.getType().translationKey());
                                        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                                            itemName = itemStack.getItemMeta().displayName();
                                        }
                                        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(
                                                getConfig().getString("MythicMobs-ItemDropped", "<yellow>Вы обронили <item>!"),
                                                Placeholder.component("item", itemName)
                                        ));
                                    }
                                });
                            }
                        } else if (itemStack.hasItemMeta() && itemStack.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
                            if (blockStateMeta.getBlockState() instanceof Container container) {
                                Arrays.stream(container.getSnapshotInventory().getContents()).forEach(item -> {
                                    if (item != null) {
                                        if (item.hasItemMeta()) {
                                            if (isSpyglass(item)) {
                                                event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
                                                event.getInventory().remove(itemStack);
                                            } else if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
                                                if (bundleMeta.hasItems()) {
                                                    bundleMeta.getItems().forEach(bundleItem -> {
                                                        if (bundleItem != null) {
                                                            if (isSpyglass(bundleItem)) {
                                                                event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
                                                                event.getInventory().remove(itemStack);
                                                                Component itemName = Component.translatable(itemStack.getType().translationKey());
                                                                if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                                                                    itemName = itemStack.getItemMeta().displayName();
                                                                }
                                                                event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(
                                                                        getConfig().getString("MythicMobs-ItemDropped", "<yellow>Вы обронили <item>!"),
                                                                        Placeholder.component("item", itemName)
                                                                ));
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                        } else if (isSpyglass(itemStack)) {
                            event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
                            event.getInventory().remove(itemStack);
                            Component itemName = Component.translatable(itemStack.getType().translationKey());
                            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                                itemName = itemStack.getItemMeta().displayName();
                            }
                            event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(
                                    getConfig().getString("MythicMobs-ItemDropped", "<yellow>Вы обронили <item>!"),
                                    Placeholder.component("item", itemName)
                            ));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSpyglassEnderChestMoveAttempt(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (!(event.getClickedInventory().getHolder(false) instanceof InspectorInventory)) {
                if (event.getClickedInventory().equals(event.getWhoClicked().getEnderChest())) {
                    ItemStack itemStack = event.getCursor();
                    switch (event.getClick()) {
                        case SWAP_OFFHAND -> itemStack = event.getWhoClicked().getInventory().getItemInOffHand();
                        case NUMBER_KEY -> itemStack = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                        case SHIFT_LEFT, SHIFT_RIGHT -> itemStack = event.getCurrentItem();
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
                                if (item == null || !item.hasItemMeta()) continue;
                                if (isSpyglass(item)) {
                                    event.setCancelled(true);
                                    return;
                                } else if (item.getItemMeta() instanceof BundleMeta bundleMeta) {
                                    if (!bundleMeta.hasItems()) continue;
                                    for (ItemStack bundleItem : bundleMeta.getItems()) {
                                        if (isSpyglass(bundleItem)) {
                                            event.setCancelled(true);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } else event.setCancelled(isSpyglass(itemStack));
                }
            }
        }
    }

    // COREPROTECT GUI

    @EventHandler
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isSpyglass(player.getInventory().getItemInMainHand())) {
            List<String[]> data = new ArrayList<>();
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR -> {
                    event.setCancelled(true);
                    data = InspectorCoreProtectLookup.performRadiusLookup(coreProtectAPI, event.getPlayer().getLocation());
                }
                case RIGHT_CLICK_BLOCK -> {
                    event.setCancelled(true);
                    if (event.getClickedBlock() != null) {
                        data = InspectorCoreProtectLookup.performBlockLookup(coreProtectAPI, event.getClickedBlock(), 0);
                    }
                }
                default -> {
                    return;
                }
            }
            if (data != null && !data.isEmpty()) {
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
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getHolder(false) instanceof InspectorInventory inspectorInventory) {
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