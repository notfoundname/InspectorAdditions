package com.notfoundname.inspectoradditions;

import net.coreprotect.CoreProtectAPI;
import net.coreprotect.utility.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Objects;

public class InspectorInventory implements InventoryHolder, Listener {

    private final InspectorAdditions plugin;
    private final Inventory inventory;
    private int currentPage = 0;
    private final int maxPage;
    private final int maxInventorySize = 45;
    private final List<String[]> data;

    public InspectorInventory(InspectorAdditions plugin, List<String[]> data) {
        this.plugin = plugin;
        this.data = data;
        this.maxPage = data.size() / maxInventorySize;
        this.inventory = plugin.getServer().createInventory(this, 54,
                Component.text(plugin.getConfig().getString("CoreProtect-InventoryName", "null")
                        .replace("<page>", Integer.toString(currentPage + 1))
                        .replace("<maxpage>", Integer.toString(maxPage + 1))));
        fill();
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void fill() {
        if (currentPage != maxPage) {
            inventory.setItem(53, InspectorAdditions.pageForwardItemStack);
        }
        if (currentPage > 0) {
            inventory.setItem(45, InspectorAdditions.pageBackItemStack);
        }
        for (int i = 0; i < maxInventorySize; i++) {
            if (data.size() <= i + (currentPage * maxInventorySize)) {
                break;
            }
            int currentNumber = i + (currentPage * maxInventorySize);
            CoreProtectAPI.ParseResult parseResult = InspectorAdditions.getInstance().getCoreProtectAPI()
                    .parseResult(data.get(currentNumber));
            int x = parseResult.getX();
            int y = parseResult.getY();
            int z = parseResult.getZ();
            String playerName = parseResult.getPlayer();
            String material = data.get(currentNumber)[5];
            String timestamp = Util.getTimeSince(parseResult.getTimestamp() / 1000L, System.currentTimeMillis() / 1000L, false)
                    .replace("ago", "назад")
                    .replace("/", "")
                    .replace("m", " минут")
                    .replace("h", " часов")
                    .replace("d", " дней");

            ItemStack itemStack;
            String configKey;
            Component name;

            switch (parseResult.getActionId()) {
                case 0:
                    itemStack = new ItemStack(parseResult.getType());
                    configKey = "CoreProtect-BlockDestroyed";
                    name = Component.translatable(parseResult.getType().translationKey());
                    break;
                case 1:
                    itemStack = new ItemStack(parseResult.getType());
                    configKey = "CoreProtect-BlockPlaced";
                    name = Component.translatable(parseResult.getType().translationKey());
                    break;
                case 2:
                    itemStack = new ItemStack(parseResult.getType());
                    configKey = "CoreProtect-BlockModified";
                    name = Component.translatable(parseResult.getType().translationKey());
                    break;
                case 3:
                    itemStack = new ItemStack(Material.IRON_SWORD);
                    configKey = "CoreProtect-EntityKilled";
                    name = Component.translatable(Util.getEntityType(data.get(currentNumber)[5]).translationKey());
                    break;
                default:
                    itemStack = new ItemStack(Material.BARRIER);
                    configKey = "null";
                    name = Component.text(parseResult.getActionId());
                    break;
            }

            if (parseResult.getItemMeta() != null) {
                itemStack.setItemMeta(parseResult.getItemMeta());
                if (parseResult.getItemMeta().hasDisplayName()) {
                    name = Objects.requireNonNull(parseResult.getItemMeta().displayName())
                            .append(Component.text(" x " + parseResult.getAmount()));
                } else {
                    name = name.append(Component.text(" x " + parseResult.getAmount()));
                }
            }
            ItemMeta itemMeta = itemStack.getItemMeta();

            itemMeta.displayName(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString(configKey, "null"),
                    Placeholder.unparsed("entry", Integer.toString(currentNumber + 1)),
                    Placeholder.component("name", name)
                    )
            );

            itemMeta.lore(plugin.getConfig().getStringList("CoreProtect-EntryLore").stream().map(entry ->
                    MiniMessage.miniMessage().deserialize(entry,
                    Placeholder.unparsed("x", Integer.toString(x)),
                    Placeholder.unparsed("y", Integer.toString(y)),
                    Placeholder.unparsed("z", Integer.toString(z)),
                    Placeholder.unparsed("player", playerName),
                    Placeholder.unparsed("time", timestamp),
                    Placeholder.unparsed("material", material),
                    Placeholder.unparsed("itemamount", parseResult.getItemMeta() != null ?
                            Integer.toString(parseResult.getAmount()) : "null"),
                    Placeholder.unparsed("itemmeta", parseResult.getItemMeta() != null ?
                            parseResult.getItemMeta().getAsString() : "null")
            )).toList());

            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
            itemStack.setItemMeta(itemMeta);
            inventory.setItem(i, itemStack);
        }
        inventory.getViewers().forEach(viewer -> viewer.getOpenInventory().setTitle(
                plugin.getConfig().getString("CoreProtect-InventoryName", "null")
                        .replace("<page>", Integer.toString(currentPage + 1))
                        .replace("<maxpage>", Integer.toString(maxPage + 1))));
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
}
