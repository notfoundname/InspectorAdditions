package com.notfoundname.inspectoradditions;

import io.lumine.mythic.bukkit.MythicBukkit;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class InspectorAdditions extends JavaPlugin {

    private static InspectorAdditions instance;
    private InspectorLeashListener inspectorLeashListener;
    private InspectorInventoryManager inspectorInventoryManager;
    private InspectorSpyglassListener inspectorSpyglassListener;
    private CoreProtectAPI coreProtectAPI;

    @Override
    public void onEnable() {
        instance = this;

        inspectorLeashListener = new InspectorLeashListener(this);
        inspectorInventoryManager = new InspectorInventoryManager(this);
        inspectorSpyglassListener = new InspectorSpyglassListener(this);

        saveDefaultConfig();

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

    public boolean isSpyglass(ItemStack playerItem) {
        return playerItem != null
                && playerItem.getType() != Material.AIR
                && playerItem.hasItemMeta()
                && MythicBukkit.inst().getItemManager().isMythicItem(playerItem)
                && MythicBukkit.inst().getItemManager().getMythicTypeFromItem(playerItem).equals(
                        getConfig().getString("MythicMobs-Item", "InspectorsSpyglass"));
    }

    @SuppressWarnings("all")
    public boolean hasSpyglass(ItemStack playerItem) {
        if (playerItem == null) return false;
        if (playerItem.hasItemMeta()) {
            if (playerItem.getItemMeta() instanceof BundleMeta bundleMeta) {
                for (ItemStack bundleItem : bundleMeta.getItems()) {
                    if (isSpyglass(bundleItem)) {
                        return true;
                    }
                }
            } else if (playerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta) {
                if (blockStateMeta.getBlockState() instanceof Container container) {
                    for (ItemStack containerItem : container.getSnapshotInventory().getContents()) {
                        if (isSpyglass(containerItem)) {
                            return true;
                        } else if (containerItem != null && containerItem.hasItemMeta()) {
                            if (containerItem.getItemMeta() instanceof BundleMeta bundleMeta) {
                                for (ItemStack bundleItem : bundleMeta.getItems()) {
                                    if (isSpyglass(bundleItem)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return isSpyglass(playerItem);
    }

    public static InspectorAdditions getInstance() {
        return instance;
    }

    public InspectorInventoryManager getInventoryManager() {
        return inspectorInventoryManager;
    }

    public CoreProtectAPI getCoreProtectAPI() {
        return coreProtectAPI;
    }
}