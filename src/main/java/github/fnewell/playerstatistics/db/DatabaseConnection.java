package github.fnewell.playerstatistics.db;

import github.fnewell.playerstatistics.utils.ConfigUtils;
import github.fnewell.playerstatistics.utils.PSLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static github.fnewell.playerstatistics.db.DriverUtils.customDriverShim;

public class DatabaseConnection {

    private static final Path SQLITE_DB_PATH = FabricLoader.getInstance().getGameDir()
            .resolve("mods/player-statistics/player-statistics.db");
    private static final String DB_TYPE = ConfigUtils.config.getConfig("database").getString("type");
    private static final String DB_NAME = ConfigUtils.config.getConfig("database").getString("name");
    private static final String DB_HOST = ConfigUtils.config.getConfig("database").getString("host");
    private static final int DB_PORT = ConfigUtils.config.getConfig("database").getInt("port");
    private static final String DB_USERNAME = ConfigUtils.config.getConfig("database").getString("username");
    private static final String DB_PASSWORD = ConfigUtils.config.getConfig("database").getString("password");
    private static final Boolean DB_SSL = ConfigUtils.config.getConfig("database").getBoolean("ssl");

    /**
     * Returns the database connection based on the selected type
     * 
     * @param dbType        Database type ("SQLITE", "MYSQL", "MARIADB", "POSTGRESQL")
     * @return              Connection object for the selected database
     * @throws SQLException If a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        PSLogger.debug("Connecting to {} database ...", DB_TYPE);

        try {
            Properties properties = new Properties();
            String url;

            switch (DB_TYPE) {
                case "SQLITE":
                    url = "jdbc:sqlite:" + SQLITE_DB_PATH;
                    return customDriverShim.connect(url, properties);

                case "MYSQL":
                case "MARIADB":
                    url = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
                            + "?ssl-mode=" + (DB_SSL ? "REQUIRED" : "DISABLED");
                    properties.setProperty("user", DB_USERNAME);
                    properties.setProperty("password", DB_PASSWORD);
                    return customDriverShim.connect(url, properties);

                case "POSTGRESQL":
                    url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?ssl="
                            + (DB_SSL ? "require" : "disable");
                    properties.setProperty("user", DB_USERNAME);
                    properties.setProperty("password", DB_PASSWORD);
                    return customDriverShim.connect(url, properties);

                default:
                    throw new IllegalArgumentException("Unsupported database type: " + DB_TYPE);
            }
        } catch (SQLException e) {
            PSLogger.debug("Trace: ", e);
            throw new SQLException("Failed to connect to " + DB_TYPE + " database: " + e.getMessage());
        }
    }
}
