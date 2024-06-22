package com.notfoundname.inspectoradditions;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.utility.Util;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class InspectorCoreProtectLookup {

    private static final int CONNECTION_WAIT_TIME = 1000;

    public static List<String[]> performBlockLookup(CoreProtectAPI coreProtectAPI, Block block, int offset) {
        List<String[]> result = coreProtectAPI.blockLookup(block, offset);
        result.addAll(performContainerLookup(block, offset));
        // TODO result.addAll(performSignLookup(block, offset));
        return result.stream().sorted((value1, value2) ->
                Long.compare(Long.parseLong(value2[0]), Long.parseLong(value1[0])))
                .toList();
    }

    public static List<String[]> performContainerLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        if (block == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, CONNECTION_WAIT_TIME)) {
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();
            final int now = (int) (System.currentTimeMillis() / 1000L);
            final int worldId = Util.getWorldId(block.getWorld().getName());
            final int timeFrom = offset > 0 ? now - offset : 0;

            if (connection == null) {
                return result;
            }

            try (Statement statement = connection.createStatement()) {
                String query = "SELECT time,user,action,type,data,rolled_back,amount,metadata FROM " +
                        ConfigHandler.prefix + "container " + Util.getWidIndex("container") +
                        "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "'" +
                        " AND time > '" + timeFrom + "' ORDER BY rowid DESC";
                try (ResultSet resultSet = statement.executeQuery(query)) {

                    while (resultSet.next()) {
                        final String resultTime = resultSet.getString("time");
                        final int resultUserId = resultSet.getInt("user");
                        final String resultAction = resultSet.getString("action");
                        final int resultType = resultSet.getInt("type");
                        final String resultData = resultSet.getString("data");
                        final String resultRolledBack = resultSet.getString("rolled_back");
                        final int resultAmount = resultSet.getInt("amount");
                        final byte[] resultMetadata = resultSet.getBytes("metadata");
                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }
                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                        final String metadata = resultMetadata != null ? new String(resultMetadata, StandardCharsets.ISO_8859_1) : "";

                        String[] resultElement = new String[]{ resultTime, resultUser,
                                String.valueOf(x), String.valueOf(y), String.valueOf(z), String.valueOf(resultType),
                                resultData, resultAction, resultRolledBack, String.valueOf(worldId),
                                String.valueOf(resultAmount), metadata, "" };
                        result.add(resultElement);
                    }
                }
            }
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getLogger().log(Level.WARNING, e.toString(), e);
        }

        return result;
    }

    public static List<String[]> performSignLookup(Block block, int offset) {
        List<String[]> result = new ArrayList<>();

        if (block == null) {
            return result;
        }

        try (Connection connection = Database.getConnection(false, CONNECTION_WAIT_TIME)) {
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();
            final int now = (int) (System.currentTimeMillis() / 1000L);
            final int worldId = Util.getWorldId(block.getWorld().getName());
            final int timeFrom = offset > 0 ? now - offset : 0;

            if (connection == null) {
                return result;
            }

            try (Statement statement = connection.createStatement()) {
                String query = "SELECT time,user,face,line_1,line_2,line_3,line_4,line_5,line_6,line_7,line_8 FROM "
                        + ConfigHandler.prefix + "sign "
                        + Util.getWidIndex("sign")
                        + "WHERE wid = '" + worldId
                        + "' AND x = '" + x
                        + "' AND z = '" + z
                        + "' AND y = '" + y
                        + "' AND action = '1'" +
                        " AND (LENGTH(line_1) > 0" +
                        " OR LENGTH(line_2) > 0" +
                        " OR LENGTH(line_3) > 0" +
                        " OR LENGTH(line_4) > 0" +
                        " OR LENGTH(line_5) > 0" +
                        " OR LENGTH(line_6) > 0" +
                        " OR LENGTH(line_7) > 0" +
                        " OR LENGTH(line_8) > 0)" +
                        " ORDER BY rowid DESC";
                try (ResultSet resultSet = statement.executeQuery(query)) {

                    while (resultSet.next()) {
                        final String resultTime = resultSet.getString("time");
                        final int resultUserId = resultSet.getInt("user");
                        final String line1 = resultSet.getString("line_1");
                        final String line2 = resultSet.getString("line_2");
                        final String line3 = resultSet.getString("line_3");
                        final String line4 = resultSet.getString("line_4");
                        final String line5 = resultSet.getString("line_5");
                        final String line6 = resultSet.getString("line_6");
                        final String line7 = resultSet.getString("line_7");
                        final String line8 = resultSet.getString("line_8");
                        final boolean isFront = resultSet.getInt("face") == 0;
                        if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                            UserStatement.loadName(connection, resultUserId);
                        }
                        String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);

                        String[] resultElement = new String[]{
                                resultTime, resultUser,
                                String.valueOf(x), String.valueOf(y),
                                String.valueOf(z), "sign",
                                "null", "2",
                                line1, line2, line3, line4,
                                line5, line6, line7, line8};
                        result.add(resultElement);
                    }
                }
            }
        }
        catch (SQLException e) {
            CoreProtect.getInstance().getLogger().log(Level.WARNING, e.toString(), e);
        }

        return result;
    }

    public static List<String[]> performRadiusLookup(CoreProtectAPI coreProtectAPI, Location location) {
        List<String[]> result = coreProtectAPI.performLookup(
                31104000,
                null,
                null,
                null,
                null,
                Arrays.asList(0, 1, 2, 3),
                InspectorAdditions.getInstance().getConfig().getInt("CoreProtect-Radius", 10), location);
        // TODO
        return result;
    }
}
