package com.notfoundname.inspectoradditions;

import net.coreprotect.database.rollback.Rollback;
import net.coreprotect.utility.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class InspectorInventory implements InventoryHolder, Listener {

    private final InspectorAdditions plugin;
    private final Inventory inventory;
    private int currentPage = 0;
    private int maxPage = 0;
    private final int maxInventorySize = 44;
    private final List<String[]> data = new ArrayList<>();
    private final List<ItemStack> items = new ArrayList<>();

    public InspectorInventory(InspectorAdditions plugin, Player player, Block block, boolean blockLookup) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, 54,
                Component.text(plugin.getConfig().getString("CoreProtect-InventoryName", "null")
                        .replace("<page>", Integer.toString(currentPage + 1))
                        .replace("<maxpage>", Integer.toString(maxPage + 1))));
        loadCoreProtect(player, block, blockLookup);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void loadCoreProtect(Player player, Block block, boolean blockLookup) {
        inventory.setMaxStackSize(1);
        player.openInventory(inventory);
        inventory.getViewers().forEach(viewer -> viewer.getOpenInventory().setTitle("Подождите..."));
        if (blockLookup) {
            data.addAll(InspectorCoreProtectLookup.performBlockLookup(plugin.getCoreProtectAPI(), block, 0));
        } else {
            data.addAll(InspectorCoreProtectLookup.performRadiusLookup(plugin.getCoreProtectAPI(), player.getLocation()));
        }
        if (!data.isEmpty()) {
            maxPage = data.size() / maxInventorySize;
            populateItems();
            fill();
        } else if (!inventory.getViewers().isEmpty()){
            inventory.close();
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("CoreProtect-NoHistory", "Нет истории")));
        }
    }

    public void populateItems() {
        for (String[] entry : data) {
            if (entry[0].isEmpty()) continue;
            String timestamp = Util.getTimeSince(Long.parseLong(entry[0]), System.currentTimeMillis() / 1000L, false)
                    .replace("ago", "назад")
                    .replace("/", "")
                    .replace("m", " минут")
                    .replace("h", " часов")
                    .replace("d", " дней");
            String source = entry[1];
            String x = entry[2];
            String y = entry[3];
            String z = entry[4];
            String type = entry[5];

            boolean isSignLookup = type.equals("sign");

            /*
            if (!isSignLookup) {
                if (Util.getType(type) != null) {
                    if (Tag.SIGNS.isTagged(Util.getType(type)) && entry[7].equals("2")) {
                        continue;
                    }
                }
            }
            */

            ItemStack itemStack;
            Component name;
            try {
                if (isSignLookup) {
                    itemStack = new ItemStack(Material.OAK_SIGN);
                } else {
                    itemStack = new ItemStack(plugin.getCoreProtectAPI().parseResult(entry).getType());
                }
                name = Component.translatable(itemStack.getType().translationKey());
            } catch (IllegalArgumentException | NullPointerException e) {
                itemStack = new ItemStack(Material.IRON_SWORD);
                name = Component.text(entry[5]);
            }
            String configKey;


            switch (entry[7]) {
                case "0" -> {
                    configKey = "CoreProtect-BlockDestroyed";
                }
                case "1" -> {
                    configKey = "CoreProtect-BlockPlaced";
                }
                case "2" -> {
                    configKey = "CoreProtect-BlockModified";
                }
                case "3" -> {
                    configKey = "CoreProtect-EntityKilled";
                    try {
                        name = Component.translatable(Util.getEntityType(Integer.parseInt(entry[5])).translationKey());
                    } catch (Throwable e) {
                        name = Component.text(entry[5]);
                    }
                }
                default -> {
                    itemStack = new ItemStack(Material.BARRIER);
                    configKey = "null";
                    name = Component.text(entry[7]);
                }
            }
            if (!isSignLookup) {
                if (entry.length >= 13 && !entry[7].equals("3")) {
                    if (entry[11] != null && !entry[11].isEmpty()) {
                        final byte[] metadata = entry[11].getBytes(StandardCharsets.ISO_8859_1);
                        ItemStack item = (ItemStack) Rollback.populateItemStack(new ItemStack(Util.getType(Integer.parseInt(type)), 1), metadata)[2];
                        itemStack.setItemMeta(item.getItemMeta());
                        if (item.getItemMeta().hasDisplayName()) {
                            name = Objects.requireNonNull(item.getItemMeta().displayName());
                        }
                    }
                }

                if (!entry[10].isEmpty()) {
                    if (!(Integer.parseInt(entry[10]) <= 0)) {
                        name = name.append(Component.text(" x " + entry[10]));
                    }
                }
            }
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.displayName(MiniMessage.miniMessage().deserialize(
                            plugin.getConfig().getString(configKey, configKey),
                            Placeholder.unparsed("entry", Integer.toString(data.indexOf(entry))),
                            Placeholder.component("name", name)
                    )
            );

            List<Component> lore = new ArrayList<>();

            if (itemMeta.hasLore()) {
                lore.addAll(itemMeta.lore());
            }

            lore.addAll(plugin.getConfig().getStringList("CoreProtect-EntryLore").stream().map(line ->
                    MiniMessage.miniMessage().deserialize(line,
                            Placeholder.unparsed("x", x),
                            Placeholder.unparsed("y", y),
                            Placeholder.unparsed("z", z),
                            Placeholder.unparsed("player", source),
                            Placeholder.unparsed("time", timestamp),
                            Placeholder.unparsed("material", type)
                    )).toList());
            if (isSignLookup) {
                lore.add(Component.text(" "));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false><white>Спереди:"));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[8] != null ?
                                entry[8] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[9] != null ?
                                entry[9] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[10] != null ?
                                entry[10] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[11] != null ?
                                entry[11] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false><white>Сзади:"));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[12] != null ?
                                entry[12] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[13] != null ?
                                entry[13] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[14] != null ?
                                entry[14] : " ")));
                lore.add(MiniMessage.miniMessage().deserialize("<i:false>")
                        .append(LegacyComponentSerializer.legacySection().deserialize(entry[15] != null ?
                entry[15] : " ")));
            }
            itemMeta.lore(lore);

            itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
            itemStack.setItemMeta(itemMeta);
            if (itemStack.getType() != Material.AIR) {
                items.add(itemStack);
            }
        }
    }

    public void fill() {
        if (currentPage != maxPage) {
            inventory.setItem(53, InspectorInventoryManager.pageForwardItemStack);
        }
        if (currentPage > 0) {
            inventory.setItem(45, InspectorInventoryManager.pageBackItemStack);
        }
        for (int i = 0; i < maxInventorySize; i++) {
            int currentNumber = i + (currentPage * maxInventorySize);
            if (items.size() <= currentNumber) {
                break;
            }
            inventory.addItem(items.get(currentNumber));
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
