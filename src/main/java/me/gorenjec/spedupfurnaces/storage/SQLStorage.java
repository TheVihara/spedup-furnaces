package me.gorenjec.spedupfurnaces.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.gorenjec.spedupfurnaces.SpedupFurnaces;
import me.gorenjec.spedupfurnaces.models.CustomFurnace;
import me.gorenjec.spedupfurnaces.models.HoloTextDisplay;
import net.minecraft.world.entity.Display;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SQLStorage {
    private HikariDataSource dataSource;
    private final SpedupFurnaces instance;
    private final FileConfiguration config;
    private static final String PLAYERDATA_TABLE = "furnace_data";

    public SQLStorage(SpedupFurnaces instance) {
        this.instance = instance;
        this.config = instance.getConfig();

        boolean useMySQL = config.getBoolean("data_storage.use_mysql");
        String path = instance.getDataFolder().getPath();

        HikariConfig hikariConfig = new HikariConfig();

        if (useMySQL) {
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setUsername(config.getString("data_storage.username"));
            hikariConfig.setPassword(config.getString("data_storage.password"));
            String hostname = config.getString("data_storage.ip");
            String port = config.getString("data_storage.port");
            String database = config.getString("data_storage.database");
            String useSSL = config.getBoolean("data_storage.database") ? "true" : "false";
            hikariConfig.setJdbcUrl("jdbc:mysql://" + hostname + ":" + port + "/" + database + "?useSSL=" + useSSL);
        } else {
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + path + "/database.sqlite");
        }

        hikariConfig.setPoolName("SpedupFurnacesPlugin");
        hikariConfig.setMaxLifetime(60000);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.addDataSourceProperty("database", config.getString("data_storage.database"));

        this.dataSource = new HikariDataSource(hikariConfig);

        constructTables();
    }

    private void constructTables() {
        boolean mysql = instance.getConfig().getBoolean("data_storage.use_mysql");
        String autoInc = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";

        try (Connection connection = dataSource.getConnection()) {
            String createTableSql = "CREATE TABLE IF NOT EXISTS " + PLAYERDATA_TABLE + "("
                    + "id INTEGER PRIMARY KEY " + autoInc + ","
                    + "type TEXT, "
                    + "level INT, "
                    + "loc_x INT, "
                    + "loc_y INT, "
                    + "loc_z INT, "
                    + "loc_facing TEXT, "
                    + "loc_world TEXT"
                    + ")";
            PreparedStatement statement = connection.prepareStatement(createTableSql);
            statement.execute();

            // Check if all columns exist in the table
            Set<String> existingColumns = getTableColumns(connection, PLAYERDATA_TABLE);
            Set<String> requiredColumns = Set.of("id", "type", "level", "loc_x", "loc_y", "loc_z", "loc_facing", "loc_world");
            if (!existingColumns.containsAll(requiredColumns)) {
                // Create a new table with all the required columns
                String newTableSql = "CREATE TABLE " + PLAYERDATA_TABLE + "_new("
                        + "id INTEGER PRIMARY KEY " + autoInc + ","
                        + "type TEXT, "
                        + "level INT, "
                        + "loc_x INT, "
                        + "loc_y INT, "
                        + "loc_z INT, "
                        + "loc_facing TEXT DEFAULT NULL, "
                        + "loc_world TEXT"
                        + ")";
                statement = connection.prepareStatement(newTableSql);
                statement.execute();

                // Copy the data from the old table to the new table
                String copyDataSql = "INSERT INTO " + PLAYERDATA_TABLE + "_new "
                        + "SELECT * FROM " + PLAYERDATA_TABLE;
                statement = connection.prepareStatement(copyDataSql);
                statement.execute();

                // Drop the old table and rename the new table
                String dropOldTableSql = "DROP TABLE " + PLAYERDATA_TABLE;
                statement = connection.prepareStatement(dropOldTableSql);
                statement.execute();

                String renameNewTableSql = "ALTER TABLE " + PLAYERDATA_TABLE + "_new "
                        + "RENAME TO " + PLAYERDATA_TABLE;
                statement = connection.prepareStatement(renameNewTableSql);
                statement.execute();

                instance.getLogger().info("Created new data table for " + PLAYERDATA_TABLE);
            } else {
                instance.getLogger().info("Verified data table for " + PLAYERDATA_TABLE);
            }
        } catch (SQLException e) {
            instance.getLogger().severe("Could not create or update tables!");
            e.printStackTrace();
        }
    }

    private Set<String> getTableColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        ResultSet resultSet = metadata.getColumns(null, null, tableName, null);
        while (resultSet.next()) {
            columns.add(resultSet.getString("COLUMN_NAME"));
        }
        return columns;
    }

    /*
    id
    type
    level
    loc_x
    loc_y
    loc_z
    loc_world
     */

    public void addFurnace(CustomFurnace customFurnace) {
        boolean mysql = instance.getConfig().getBoolean("data_storage.use_mysql");
        String sql = mysql ? "INSERT INTO " + PLAYERDATA_TABLE + " (type, level, loc_x, loc_y, loc_z, loc_facing, loc_world) VALUES (?, ?, ?, ?, ?, ?, ?)" : "INSERT INTO " + PLAYERDATA_TABLE + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            if (mysql) {
                statement.setString(1, customFurnace.getMaterial().name());
                statement.setInt(2, customFurnace.getLevel());
                statement.setInt(3, customFurnace.getLocation().getBlockX());
                statement.setInt(4, customFurnace.getLocation().getBlockY());
                statement.setInt(5, customFurnace.getLocation().getBlockZ());
                statement.setString(6, customFurnace.getFacing().name());
                statement.setString(7, customFurnace.getLocation().getWorld().getName());
            } else {
                statement.setString(2, customFurnace.getMaterial().name());
                statement.setInt(3, customFurnace.getLevel());
                statement.setInt(4, customFurnace.getLocation().getBlockX());
                statement.setInt(5, customFurnace.getLocation().getBlockY());
                statement.setInt(6, customFurnace.getLocation().getBlockZ());
                statement.setString(7, customFurnace.getFacing().name());
                statement.setString(8, customFurnace.getLocation().getWorld().getName());
            }

            statement.execute();
        } catch (SQLException e) {
            instance.getLogger().severe("Could not store furnace!");
            e.printStackTrace();
        }
    }

    public void updateFurnace(CustomFurnace customFurnace) {
        String sql = "UPDATE " + PLAYERDATA_TABLE + " SET level = ? WHERE loc_x = ? AND loc_y = ? AND loc_z = ? AND loc_facing = ? AND loc_world = ?;";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);
            Location location = customFurnace.getLocation();
            statement.setInt(1, customFurnace.getLevel());
            statement.setInt(2, location.getBlockX());
            statement.setInt(3, location.getBlockY());
            statement.setInt(4, location.getBlockZ());
            statement.setString(5, customFurnace.getFacing().name());
            statement.setString(6, location.getWorld().getName());

            statement.execute();
        } catch (SQLException e) {
            instance.getLogger().severe("Could not store furnace!");
            e.printStackTrace();
        }
    }

    public void removeFurnace(Location location) {
        String sql = "DELETE FROM " + PLAYERDATA_TABLE + " WHERE loc_x = ? AND loc_y = ? AND loc_z = ? AND loc_world = ?;";

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, location.getBlockX());
            statement.setInt(2, location.getBlockY());
            statement.setInt(3, location.getBlockZ());
            statement.setString(4, location.getWorld().getName());

            statement.execute();
        } catch (SQLException e) {
            instance.getLogger().severe("Could not remove furnace!");
            e.printStackTrace();
        }
    }

    public Map<Location, CustomFurnace> getFurnaces() {
        String sql = "SELECT * FROM " + PLAYERDATA_TABLE;
        Map<Location, CustomFurnace> customFurnaceMap = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            ResultSet playerDataSet = statement.executeQuery();

            while (playerDataSet.next()) {
                int x = playerDataSet.getInt("loc_x");
                int y = playerDataSet.getInt("loc_y");
                int z = playerDataSet.getInt("loc_z");
                BlockFace blockFace = BlockFace.valueOf(playerDataSet.getString("loc_facing"));
                String worldName = playerDataSet.getString("loc_world");
                World world = Bukkit.getWorld(worldName);
                Location location = new Location(world, x, y, z);
                String type = playerDataSet.getString("type");
                int level = playerDataSet.getInt("level");
                Material material = Material.valueOf(type.toUpperCase());
                CustomFurnace customFurnace = getFurnace(location, level, material, blockFace);

                customFurnaceMap.put(
                        location, customFurnace
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return customFurnaceMap;
    }
    public CustomFurnace getFurnace(Location location, int level, Material type, BlockFace blockFace) {
        Vector direction = blockFace.getDirection();
        float yaw = 0;

        switch (blockFace) {
            case NORTH -> {
                yaw = 180;
                direction.multiply(0.51);
            }
            case EAST -> {
                yaw = -90;
                direction.multiply(0.5);
            }
            case SOUTH -> {
                yaw = 0;
                direction.multiply(0.5);
            }
            case WEST -> {
                yaw = 90;
                direction.multiply(0.51);
            }
        }

        location.add(0.5, 0.3, 0.5);
        location.add(direction);

        CustomFurnace customFurnace = new CustomFurnace(location, type, level, new HoloTextDisplay(
                instance,
                "§bLevel " + level,
                location,
                10,
                yaw,
                0,
                Display.BillboardConstraints.FIXED
        ));

        return customFurnace;
    }

    public void clearFurnaces() {
        String sql = "DELETE FROM " + PLAYERDATA_TABLE;

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(sql);

            statement.execute();
        } catch (SQLException e) {
            instance.getLogger().severe("Could not store furnace!");
            e.printStackTrace();
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}

