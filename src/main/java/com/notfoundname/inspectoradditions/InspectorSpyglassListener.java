package com.notfoundname.inspectoradditions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class InspectorSpyglassListener implements Listener {

    private final InspectorAdditions plugin;

    public InspectorSpyglassListener(InspectorAdditions plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onSpyglassEnderChestOpenEvent(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (!(event.getInventory().getHolder(false) instanceof InspectorInventory)) {
                if (event.getInventory().equals(event.getPlayer().getEnderChest())) {
                    for (ItemStack itemStack : event.getInventory().getContents()) {
                        if (plugin.hasSpyglass(itemStack)) {
                            event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
                            event.getInventory().remove(itemStack);
                            Component itemName = Component.translatable(itemStack.getType().translationKey());
                            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                                itemName = itemStack.getItemMeta().displayName();
                            }
                            event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(
                                    plugin.getConfig().getString("MythicMobs-ItemDropped", "<yellow>Вы обронили <item>!"),
                                    Placeholder.component("item", itemName)
                            ));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSpyglassEnderChestClickAttempt(InventoryClickEvent event) {
        ItemStack item = event.getCursor();

        boolean isEnderChest = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.ENDER_CHEST;

        switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                item = event.getCurrentItem();
                isEnderChest = event.getInventory().getType() == InventoryType.ENDER_CHEST;
            }
            case SWAP_OFFHAND -> item = event.getWhoClicked().getInventory().getItemInOffHand();
            case NUMBER_KEY -> item = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        }

        if (isEnderChest && plugin.hasSpyglass(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack dragged = event.getOldCursor();
        if (plugin.hasSpyglass(dragged)) {
            int inventorySize = event.getInventory().getSize();
            for (int i : event.getRawSlots()) {
                if (i < inventorySize) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    // COREPROTECT GUI

    @EventHandler
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.isSpyglass(player.getInventory().getItemInMainHand()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            plugin.getInventoryManager().put(
                    player.getUniqueId(),
                    new InspectorInventory(plugin, player, event.getClickedBlock(), event.getClickedBlock() != null));
        }
    }
}
