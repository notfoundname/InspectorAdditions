package com.notfoundname.inspectoradditions;

import net.coreprotect.CoreProtectAPI;
import net.coreprotect.utility.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InspectorInventory implements InventoryHolder, Listener {

    private final InspectorAdditions plugin;
    private Inventory inventory;
    private int currentPage = 0;
    private int maxPage = 0;
    private final int maxInventorySize = 45;
    private List<String[]> data;
    private static final ItemStack pageForwardItemStack = new ItemStack(Material.ARROW);
    private static final ItemStack pageBackItemStack = new ItemStack(Material.ARROW);

    public InspectorInventory(InspectorAdditions plugin, List<String[]> data, Player player) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (data != null && player != null) {
            this.data = data;
            maxPage = data.size() / maxInventorySize;
            inventory = plugin.getServer().createInventory(this, 54,
                    Component.text(plugin.getConfig().getString("CoreProtect-InventoryName", "null")
                            .replace("%page%", Integer.toString(currentPage + 1))
                            .replace("%maxpage%", Integer.toString(maxPage + 1))));
            flush();
            fill();
            open(player);
        }
    }

    static {
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

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void fill() {
        if (currentPage != maxPage) {
            inventory.setItem(53, pageForwardItemStack);
        }
        if (currentPage > 0) {
            inventory.setItem(45, pageBackItemStack);
        }
        for (int i = 0; i < maxInventorySize; i++) {
            if (data.size() <= i + (currentPage * maxInventorySize)) {
                break;
            }
            CoreProtectAPI.ParseResult parseResult = InspectorAdditions.getInstance().getCoreProtectAPI()
                    .parseResult(data.get(i + (currentPage * maxInventorySize)));
            int x = parseResult.getX();
            int y = parseResult.getY();
            int z = parseResult.getZ();
            String playerName = parseResult.getPlayer();
            String material = parseResult.getType().getKey().asString();
            String timestamp = Util.getTimeSince(parseResult.getTimestamp() / 1000L, System.currentTimeMillis() / 1000L, false)
                    .replace("ago", "назад")
                    .replace("/", "")
                    .replace("m", " минут")
                    .replace("h", " часов")
                    .replace("d", " дней");

            ItemStack itemStack = new ItemStack(parseResult.getType());
            ItemMeta itemMeta = itemStack.getItemMeta();
            List<Component> newLore = new ArrayList<>();

            switch (parseResult.getActionId()) {
                case 0:
                    itemMeta.displayName(
                            MiniMessage.miniMessage().deserialize(
                                    plugin.getConfig().getString("CoreProtect-BlockDestroyed", "null")
                                            .replace("%entry%", Integer.toString(1 + i + (currentPage * maxInventorySize)))));
                    break;
                case 1:
                    itemMeta.displayName(
                            MiniMessage.miniMessage().deserialize(
                                    plugin.getConfig().getString("CoreProtect-BlockPlaced", "null")
                                            .replace("%entry%", Integer.toString(1 + i + (currentPage * maxInventorySize)))));
                    break;
                case 2:
                    itemMeta.displayName(
                            MiniMessage.miniMessage().deserialize(
                                    plugin.getConfig().getString("CoreProtect-BlockModified", "null")
                                            .replace("%entry%", Integer.toString(1 + i + (currentPage * maxInventorySize)))));
                    break;
                case 3:
                    itemMeta.displayName(
                            MiniMessage.miniMessage().deserialize(
                                    plugin.getConfig().getString("CoreProtect-EntityKilled", "null")
                                            .replace("%entry%", Integer.toString(1 + i + (currentPage * maxInventorySize)))));
                    break;
                default:
                    itemStack.setType(Material.PAPER);
                    itemMeta.displayName(Component.text(parseResult.getActionId()));
                    break;
            }
            for (String loreEntry : plugin.getConfig().getStringList("CoreProtect-EntryLore")) {
                newLore.add(MiniMessage.miniMessage().deserialize(loreEntry
                        .replace("%x%", Integer.toString(x))
                        .replace("%y%", Integer.toString(y))
                        .replace("%z%", Integer.toString(z))
                        .replace("%player%", playerName)
                        .replace("%time%", timestamp)
                        .replace("%material%", material)));
            }
            itemMeta.lore(newLore);

            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(itemMeta);
            inventory.setItem(i, itemStack);
        }
        inventory.getViewers().forEach(viewer -> viewer.getOpenInventory().setTitle(
                plugin.getConfig().getString("CoreProtect-InventoryName", "null")
                        .replace("%page%", Integer.toString(currentPage + 1))
                        .replace("%maxpage%", Integer.toString(maxPage + 1))));
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void flush() {
        inventory.clear();
    }

    public void delete() {
        flush();
        inventory = null;
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder(false) instanceof InspectorInventory)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        if (event.getCurrentItem().equals(pageForwardItemStack)) {
            currentPage++;
            flush();
            fill();
        } else if (event.getCurrentItem().equals(pageBackItemStack)) {
            currentPage--;
            flush();
            fill();
        }
    }

    @EventHandler
    public void onInventoryModify(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder(false) instanceof InspectorInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof InspectorInventory) {
            plugin.getLogger().info("Deleting " + inventory);
            delete();
        }
    }
}
