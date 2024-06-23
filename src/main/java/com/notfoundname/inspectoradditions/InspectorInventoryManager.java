package com.notfoundname.inspectoradditions;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InspectorInventoryManager implements Listener {

    private final Map<UUID, InspectorInventory> openedInventories = new HashMap<>();

    public static final ItemStack pageForwardItemStack = new ItemStack(Material.ARROW);
    public static final ItemStack pageBackItemStack = new ItemStack(Material.ARROW);

    public InspectorInventoryManager(InspectorAdditions plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

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
    }

    public void put(UUID uuid, InspectorInventory inventory) {
        openedInventories.remove(uuid);
        openedInventories.put(uuid, inventory);
    }

    public void remove(UUID uuid) {
        openedInventories.remove(uuid);
    }

    public InspectorInventory get(UUID uuid) {
        return openedInventories.get(uuid);
    }

    @EventHandler
    public void onInspectorInventoryClick(InventoryClickEvent event) {
        boolean isInspectorInventory = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder(false) instanceof InspectorInventory;
        InventoryHolder inventoryHolder = event.getClickedInventory().getHolder(false);

        if (event.getClick().isShiftClick()) {
            isInspectorInventory = event.getInventory().getHolder(false) instanceof InspectorInventory;
            inventoryHolder = event.getInventory().getHolder(false);
        }

        if (isInspectorInventory) {
            InspectorInventory inventory = (InspectorInventory) inventoryHolder;
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                if (event.getCurrentItem().equals(pageForwardItemStack)) {
                    inventory.setCurrentPage(inventory.getCurrentPage() + 1);
                } else if (event.getCurrentItem().equals(pageBackItemStack)) {
                    inventory.setCurrentPage(inventory.getCurrentPage() - 1);
                } else {
                    return;
                }
                inventory.getInventory().clear();
                inventory.fill();
            }
        }
    }

    @EventHandler
    public void onInspectorInventoryDrag(InventoryDragEvent event) {
        event.setCancelled(event.getInventory().getHolder(false) instanceof InspectorInventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof InspectorInventory) {
            remove(event.getPlayer().getUniqueId());
        }
    }
}
