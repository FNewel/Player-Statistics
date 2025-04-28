package github.fnewell.playerstatistics.utils;

import com.fasterxml.jackson.databind.JsonNode;
import github.fnewell.playerstatistics.PlayerStatistics;
import github.fnewell.playerstatistics.db.LocalDatabase;

import java.net.HttpURLConnection;
import java.net.URI;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseUtils {

    public static final String DB_LOCATION = ConfigUtils.config.getString("database.location"); // LOCAL or REMOTE
    private static final String DB_TYPE = ConfigUtils.config.getString("database.type"); // Database type (MySQL,
                                                                                         // MariaDB, SQLite, PostgreSQL)

    private static final List<String> TABLE_NAMES = Arrays.asList(
            "broken", "crafted", "custom", "dropped", "killed", "killed_by", "mined", "picked_up", "used");

    /**
     * Get a connection to the database based on the configuration.
     * This method is used to connect to a local or remote database based on the
     * configuration.
     *
     * @return The database connection.
     * @throws Exception If an error occurs while connecting to the database.
     */
    public static Connection getDatabaseConnection() throws Exception {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Connecting to the database ...");
        }

        // Connect to the database based on the configuration
        if ("REMOTE".equals(DB_LOCATION)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Connecting to remote database ...");
            }

            String DbType = ConfigUtils.config.getString("database.type");
            String DbName = ConfigUtils.config.getString("database.name");
            String DbHost = ConfigUtils.config.getString("database.host");
            String DbPort = ConfigUtils.config.getString("database.port");
            String DbUser = ConfigUtils.config.getString("database.username");
            String DbPassword = ConfigUtils.config.getString("database.password");

            // Construct the JDBC URL based on the database type
            String url = switch (DbType) {
                case "MARIADB", "MYSQL", "POSTGRESQL" ->
                    "jdbc:" + DbType.toLowerCase() + "://" + DbHost + ":" + DbPort + "/" + DbName;
                case "SQLITE" -> "jdbc:sqlserver://" + DbHost + ":" + DbPort + ";databaseName=" + DbName;
                default -> throw new IllegalArgumentException("Unexpected value: " + DbType);
            };

            return DriverManager.getConnection(url, DbUser, DbPassword);
        } else if ("LOCAL".equals(DB_LOCATION)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Connecting to local database ...");
            }
            return LocalDatabase.getConnection();
        } else {
            throw new IllegalArgumentException("Unsupported database location: " + DB_LOCATION);
        }
    }

    /**
     * Fetch the list of players (UUID and last online time) from the database.
     *
     * @param connection The database connection to use.
     * @return A map containing player UUIDs as keys and their last online times as
     *         values.
     */
    public static Map<String, Timestamp> fetchPlayerDataFromDatabase(Connection connection) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching player data from database...");
        }

        String query = "SELECT player_uuid, player_last_online FROM uuid_map";
        Map<String, Timestamp> playerDataMap = new HashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String playerUUID = resultSet.getString("player_uuid");
                long lastOnlineMillis = resultSet.getLong("player_last_online");
                Timestamp lastOnline = new Timestamp(lastOnlineMillis);

                playerDataMap.put(playerUUID, lastOnline);

                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Fetched player: UUID = {}, Last Online = {}", playerUUID, lastOnline);
                }
            }

        } catch (SQLException e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error fetching player data from database: {}", e.getMessage());
        }

        return playerDataMap;
    }

    /**
     * Fetch and update missing player nicknames in the database.
     * This method is called after all player statistics have been synchronized, to
     * fetch all missing player nicknames.
     *
     * @param connection The connection to the database.
     */
    public static void fetchAndUpdateMissingPlayerNicks(Connection connection) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching missing player nicks ...");
        }

        String countSQL = "SELECT COUNT(*) AS total FROM uuid_map WHERE player_nick IS NULL";
        String fetchMissingNicksSQL = "SELECT id, player_uuid FROM uuid_map WHERE player_nick IS NULL";

        try (ExecutorService executor = Executors.newFixedThreadPool(ConfigUtils.config.getInt("sync-thread-count"))) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Executor created (missing nicks)");
            }
            PlayerStatistics.executors.add(executor); // Add the executor to the list of executors for cleanup

            // Get total number of missing player nicks
            try (PreparedStatement countStmt = connection.prepareStatement(countSQL);
                    ResultSet countRs = countStmt.executeQuery()) {

                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Counting missing player nicks ...");
                }

                if (countRs.next()) {
                    StatSyncTask.progressTo = countRs.getInt("total");
                }
            } catch (SQLException e) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Trace: ", e);
                }
                PlayerStatistics.LOGGER.error("Error counting missing player nicks: {}", e.getMessage());
            }

            // Fetch missing player nicks
            try (PreparedStatement fetchStmt = connection.prepareStatement(fetchMissingNicksSQL);
                    ResultSet rs = fetchStmt.executeQuery()) {

                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Fetching missing player nicks ...");
                }

                while (rs.next()) {
                    int playerId = rs.getInt("id");
                    String playerUUID = rs.getString("player_uuid");

                    // Process each player in a separate thread
                    executor.submit(() -> {
                        if (PlayerStatistics.DEBUG) {
                            PlayerStatistics.LOGGER.info("Executor running (missing nicks)");
                        }
                        try {
                            String playerNick;

                            // Check if the player UUID starts with '00000000-0000-0000-'
                            if (playerUUID.startsWith("00000000-0000-0000-")) {
                                playerNick = fetchBedrockPlayerNickFromAPI(playerUUID);
                            } else {
                                playerNick = fetchJavaPlayerNickFromAPI(playerUUID);
                                if (playerNick == null) {
                                    playerNick = fetchJavaPlayerNickFromElybyAPI(playerUUID);
                                }
                            }

                            // String playerNick = fetchJavaPlayerNickFromAPI(playerUUID);
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Fetched nick for UUID: {} ({})", playerUUID, playerNick);
                            }
                            if (playerNick != null) {
                                updatePlayerNickInDatabase(connection, playerId, playerNick);
                                if (PlayerStatistics.DEBUG) {
                                    PlayerStatistics.LOGGER.info("Updated nick for UUID: {} ({})", playerUUID,
                                            playerNick);
                                }
                            }
                        } catch (Exception e) {
                            PlayerStatistics.LOGGER.error("Error fetching/updating nick for UUID: {}", playerUUID);
                        }
                    });
                }
            } catch (SQLException e) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Trace: ", e);
                }
                PlayerStatistics.LOGGER.error("Error fetching missing player nicks: {}", e.getMessage());
            }

            // Wait for all threads to finish
            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Executor interrupted (missing nicks): {}", e.getMessage());
        }
    }

    /**
     * Get or insert (new) the player ID for the given UUID.
     *
     * @param connection The connection to the database.
     * @param playerUUID The UUID of the player.
     * @param lastOnline The last online timestamp of the player.
     * @return The player ID.
     * @throws SQLException If an SQL error occurs.
     */
    public static int getOrInsertPlayerId(Connection connection, UUID playerUUID, Timestamp lastOnline)
            throws SQLException {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Getting/Inserting player ID for UUID: {}", playerUUID);
        }

        String selectSQL = "SELECT id FROM uuid_map WHERE player_uuid = ?";
        try (PreparedStatement selectStmt = connection.prepareStatement(selectSQL)) {
            selectStmt.setString(1, playerUUID.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Player ID found for UUID: {}", playerUUID);
                    }

                    int playerId = rs.getInt("id");

                    // Update the last online timestamp
                    String updateSQL = "UPDATE uuid_map SET player_last_online = ? WHERE id = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSQL)) {
                        updateStmt.setTimestamp(1, lastOnline);
                        updateStmt.setInt(2, playerId);
                        updateStmt.executeUpdate();
                    }

                    return playerId;
                }
            }
        }

        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Player ID not found for UUID: {} (insert)", playerUUID);
        }

        String insertSQL = "INSERT INTO uuid_map (player_uuid, player_last_online) VALUES (?, ?)";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSQL,
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, playerUUID.toString()); // Set the player UUID
            insertStmt.setTimestamp(2, lastOnline); // Set the last online timestamp
            insertStmt.executeUpdate();

            try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Player ID inserted for UUID: {}", playerUUID);
                    }
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException("Failed to insert or retrieve player ID for UUID: " + playerUUID);
    }

    /**
     * Get the last global synchronization time from the database.
     * This method is used to determine which player statistics need to be
     * synchronized.
     *
     * @param connection The connection to the database.
     */
    public static Timestamp getLastSyncTime(Connection connection) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching last sync time ...");
        }

        String sql = "SELECT last_update FROM sync_metadata";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // Try to fetch the last update time as a Timestamp
                    try {
                        if (PlayerStatistics.DEBUG) {
                            PlayerStatistics.LOGGER.info("Last sync time found as Timestamp");
                        }
                        return resultSet.getTimestamp("last_update");
                    } catch (Exception e) {
                        // If the timestamp is null or invalid, try to fetch it as a string
                        String lastUpdate = resultSet.getString("last_update");
                        if (lastUpdate != null) {
                            // Replace 'T' with ' ' for SQLite databases
                            if (lastUpdate.contains("T")) {
                                lastUpdate = lastUpdate.replace("T", " ");
                            }
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Last sync time found as String: {}", lastUpdate);
                            }
                            return Timestamp.valueOf(lastUpdate); // Convert to Timestamp
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error fetching last sync time: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Update Sync Metadata in the database.
     * This method is called after all player statistics have been synchronized.
     * And it updates the last update time, server name, server description, server
     * url and server icon.
     *
     * @param connection        The connection to the database.
     * @param serverName        The name of the server.
     * @param serverDescription The description of the server.
     * @param serverUrl         The url of the server.
     * @param serverIcon        The icon of the server.
     */
    public static void updateSyncMetadata(Connection connection, String serverName, String serverDescription,
            String serverUrl, byte[] serverIcon) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Updating sync metadata ...");
        }

        String sql = "UPDATE sync_metadata SET last_update = ?, server_name = ?, server_desc = ?, server_url = ?, server_icon = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Updating sync metadata (statement) ...");
            }

            // Set the current timestamp as the last update time
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            String dbType = ConfigUtils.config.getString("database.type");
            if ("LOCAL".equals(DB_LOCATION)) {
                dbType = "SQLITE";
            }

            // Set the current timestamp by database type
            if ("SQLITE".equals(dbType)) {
                // For SQLite, we set the time as a string in ISO 8601 format
                String isoTime = now.toLocalDateTime().toString(); // Format: YYYY-MM-DDTHH:MM:SS
                statement.setString(1, isoTime);
            } else {
                // For MySQL/MariaDB/PostgreSQL set Timestamp directly
                statement.setTimestamp(1, now);
            }

            // Set the server name, server description, server url and server icon
            statement.setString(2, serverName);
            statement.setString(3, serverDescription);
            statement.setString(4, serverUrl);
            statement.setBytes(5, serverIcon);

            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Executing update statement ...");
            }
            statement.executeUpdate();

            // Update last sync time (remove milliseconds)
            StatSyncTask.lastSync = String.valueOf(now).split("\\.")[0];
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error updating sync metadata: {}", e.getMessage());
        }
    }

    /**
     * Update the player nickname in the database.
     *
     * @param connection The connection to the database.
     * @param playerId   The ID of the player.
     * @param playerNick The nickname of the player.
     * @throws SQLException If an SQL error occurs.
     */
    public static void updatePlayerNickInDatabase(Connection connection, int playerId, String playerNick)
            throws SQLException {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Updating player nick in database ...");
        }

        String updateSQL = "UPDATE uuid_map SET player_nick = ? WHERE id = ?";
        try (PreparedStatement updateStmt = connection.prepareStatement(updateSQL)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Updating player nick in database (statement) ...");
            }

            updateStmt.setString(1, playerNick);
            updateStmt.setInt(2, playerId);
            updateStmt.executeUpdate();

            // Increment synced players
            StatSyncTask.progressFrom++;
        }
    }

    /**
     * Synchronize player statistics with the database.
     * This method is called for each player file in a separate thread.
     *
     * @param connection The connection to the database.
     * @param playerUUID The UUID of the player.
     * @param lastOnline The last online timestamp of the player.
     * @param stats      The player statistics.
     */
    public static void syncPlayerStats(Connection connection, UUID playerUUID, Timestamp lastOnline, JsonNode stats) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Synchronizing player stats ...");
        }

        try {
            int playerId = getOrInsertPlayerId(connection, playerUUID, lastOnline);

            Map<String, List<String>> batchStatements = new HashMap<>();

            // Create batch statements for each stat type
            for (Iterator<String> it = stats.fieldNames(); it.hasNext();) {
                String statType = it.next();
                JsonNode statDetails = stats.get(statType);
                String tableName = "`" + statType.replace("minecraft:", "") + "`";
                List<String> statements = batchStatements.computeIfAbsent(tableName, k -> new ArrayList<>());

                statDetails.fields().forEachRemaining(entry -> {
                    String statName = entry.getKey();
                    int amount = entry.getValue().asInt();
                    statements
                            .add(String.format("(%d, '%s', %d)", playerId, statName.replace("minecraft:", ""), amount));
                });
            }

            // Syntax differences for different database types
            String dbType = ConfigUtils.config.getString("database.type");
            if ("LOCAL".equals(DB_LOCATION)) {
                dbType = "SQLITE";
            }

            String finalDbType = dbType;
            batchStatements.forEach((tableName, statements) -> {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Synchronizing player stats ({}) - {} / {} ...", finalDbType,
                            tableName, statements);
                }

                String sql = switch (finalDbType) {
                    case "MARIADB", "MYSQL" ->
                        """
                                    INSERT INTO %s (player_id, stat_name, amount)
                                    VALUES %s
                                    ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                                """.formatted(tableName, String.join(", ", statements));
                    case "SQLITE" ->
                        """
                                    INSERT INTO %s (player_id, stat_name, amount)
                                    VALUES %s
                                    ON CONFLICT(player_id, stat_name) DO UPDATE SET amount = excluded.amount
                                """.formatted(tableName, String.join(", ", statements));
                    case "POSTGRESQL" ->
                        """
                                    INSERT INTO %s (player_id, stat_name, amount)
                                    VALUES %s
                                    ON CONFLICT (player_id, stat_name) DO UPDATE SET amount = excluded.amount
                                """.formatted(tableName, String.join(", ", statements));
                    case null, default ->
                        throw new IllegalArgumentException("Unsupported database type: " + finalDbType);
                };

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Executing batch statement ...");
                    }
                    statement.executeUpdate();
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Batch statement executed!");
                    }
                } catch (Exception e) {
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Trace: ", e);
                    }
                    PlayerStatistics.LOGGER.error("Error executing batch statement: {}", e.getMessage());
                }
            });

            // Increment synced players
            StatSyncTask.progressFrom++;
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error synchronizing player stats: {}", e.getMessage());
        }
    }

    /**
     * Fetch the player nickname from the GeyserMC API.
     *
     * @param playerUUID The UUID of the player.
     * @return The player gamertag or null if not found (or an error occurred).
     */
    private static String fetchBedrockPlayerNickFromAPI(String playerUUID) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching Bedrock player nick from API ...");
        }

        // Remove leading zeros ('00000000-0000-0000-' - first 19 characters) from the
        // player UUID
        playerUUID = playerUUID.substring(19).replace("-", "");
        // Convert string from hexadecimal format to decimal format
        long playerLong = Long.parseLong(playerUUID, 16);

        String apiUrl = "https://api.geysermc.org/v2/xbox/gamertag/" + playerLong;
        try {
            // Create a connection to the API as a GET request
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");

            // Read the response from the API
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Reading response from API (Bedrock) ...");
                }
                String response = scanner.useDelimiter("\\A").next();
                JsonNode rootNode = StatSyncTask.MAPPER.readTree(response);
                JsonNode profileNameNode = rootNode.get("gamertag");
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Player nick fetched from API (Bedrock): {}",
                            profileNameNode.asText());
                }
                return profileNameNode != null ? profileNameNode.asText() : null;
            }
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error fetching player nick from API (Bedrock): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the player nickname from the Ely.by API.
     *
     * @param playerUUID The UUID of the player.
     * @return The player nickname or null if not found (or an error occurred).
     */
    private static String fetchJavaPlayerNickFromElybyAPI(String playerUUID) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching Java player nick from Ely.by API ...");
        }

        String apiUrl = "https://authserver.ely.by/api/user/profiles/" + playerUUID + "/names";
        try {
            // Create a connection to the API as a GET request
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");

            // Read the response from the API
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Reading response from API (Ely.by) ...");
                }
                String response = scanner.useDelimiter("\\A").next();
                JsonNode rootNode = StatSyncTask.MAPPER.readTree(response);
                PlayerStatistics.LOGGER.info("Player nick fetched from API (Java): {}", response);
                JsonNode nameNode = rootNode.iterator().next().path("name");
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Player nick fetched from API (Ely.by): {}",
                            nameNode.asText());
                }
                return nameNode != null ? nameNode.asText() : null;
            }
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error fetching player nick from API (Ely.by): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the player nickname from the Minetools API.
     *
     * @param playerUUID The UUID of the player.
     * @return The player nickname or null if not found (or an error occurred).
     */
    private static String fetchJavaPlayerNickFromAPI(String playerUUID) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Fetching Java player nick from API ...");
        }

        String apiUrl = "https://api.minetools.eu/profile/" + playerUUID;
        try {
            // Create a connection to the API as a GET request
            URI uri = new URI(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");

            // Read the response from the API
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Reading response from API (Java) ...");
                }
                String response = scanner.useDelimiter("\\A").next();
                JsonNode rootNode = StatSyncTask.MAPPER.readTree(response);
                JsonNode decodedNode = rootNode.get("decoded");
                JsonNode profileNameNode = decodedNode.get("profileName");
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Player nick fetched from API (Java): {}", profileNameNode.asText());
                }
                return profileNameNode != null ? profileNameNode.asText() : null;
            }
        } catch (Exception e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error fetching player nick from API (Java): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update the positions for all players in the database.
     * This method ranks players based on the amount of each stat and updates the
     * position column in the database.
     * Only the top 5 players for each stat are ranked.
     *
     * @param connection The connection to the database.
     * @throws UnsupportedOperationException If an SQL error occurs.
     */
    public static void updatePositionsForTable(Connection connection) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Updating positions in tables ...");
        }

        StatSyncTask.progressTo = TABLE_NAMES.size();

        int sync_thread_count = "SQLITE".equalsIgnoreCase(DB_TYPE) ? 1 : ConfigUtils.config.getInt("sync-thread-count");

        try (ExecutorService executor = Executors.newFixedThreadPool(sync_thread_count)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Executor created (positions)");
            }
            PlayerStatistics.executors.add(executor); // Add the executor to the list of executors for cleanup

            for (String tableName : TABLE_NAMES) {
                executor.submit(() -> {
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Executor running (positions)");
                    }
                    try {
                        // Reset the 'position' column in the table
                        String resetPositionsSQL = "UPDATE " + tableName + " SET position = NULL";
                        try (PreparedStatement resetStmt = connection.prepareStatement(resetPositionsSQL)) {
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Resetting positions in table: {}", tableName);
                            }
                            resetStmt.executeUpdate();
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Positions reset in table: {}", tableName);
                            }
                        }

                        if ("MYSQL".equalsIgnoreCase(DB_TYPE) || "MARIADB".equalsIgnoreCase(DB_TYPE)) {
                            updatePositionsMySQL(connection, tableName);
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Positions updated in table (MySQL/MARIADB): {}",
                                        tableName);
                            }
                        } else if ("SQLITE".equalsIgnoreCase(DB_TYPE)) {
                            updatePositionsSQLite(connection, tableName);
                            if (PlayerStatistics.DEBUG) {
                                PlayerStatistics.LOGGER.info("Positions updated in table (SQLite): {}", tableName);
                            }
                        } else {
                            throw new UnsupportedOperationException("Unsupported database type: " + DB_TYPE);
                        }
                    } catch (SQLException e) {
                        if (PlayerStatistics.DEBUG) {
                            PlayerStatistics.LOGGER.info("Trace: ", e);
                        }
                        PlayerStatistics.LOGGER.error("Error updating positions in table '{}': {}", tableName,
                                e.getMessage());
                    }
                });
            }

            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.MINUTES)) {
                executor.shutdown();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Executor interrupted (positions): {}", e.getMessage());
        }

    }

    /**
     * Update the positions for all players in the database (MySQL and MariaDB
     * version).
     * This method ranks players based on the amount of each stat and updates the
     * position column in the database.
     *
     * @param connection The connection to the database.
     * @param tableName  The name of the table.
     */
    private static void updatePositionsMySQL(Connection connection, String tableName) throws SQLException {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Updating positions in table (MySQL/MariaDB): {}", tableName);
        }

        String tempTableName = "ranked_data_" + Thread.currentThread().threadId();

        String createTempTableSQL = """
                    CREATE TEMPORARY TABLE %s AS
                    SELECT
                        player_id,
                        stat_name,
                        ROW_NUMBER() OVER (PARTITION BY stat_name ORDER BY amount DESC) AS row_num
                    FROM %s
                    WHERE amount > 0
                """.formatted(tempTableName, tableName);

        String updatePositionsSQL = """
                    UPDATE %s
                    JOIN %s
                    ON %s.player_id = %s.player_id
                    AND %s.stat_name = %s.stat_name
                    SET %s.position = %s.row_num
                    WHERE %s.row_num <= 5
                """.formatted(tableName, tempTableName, tableName, tempTableName, tableName, tempTableName, tableName,
                tempTableName, tempTableName);

        String dropTempTableSQL = "DROP TEMPORARY TABLE " + tempTableName;

        try {
            try (PreparedStatement createTempStmt = connection.prepareStatement(createTempTableSQL)) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Creating temporary table (MySQL/MariaDB)...");
                }
                createTempStmt.executeUpdate();
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Temporary table created (MySQL/MariaDB)!");
                }
            }

            try (PreparedStatement updateStmt = connection.prepareStatement(updatePositionsSQL)) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Updating positions in table (MySQL/MariaDB)...");
                }
                updateStmt.executeUpdate();
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Positions updated in table (MySQL/MariaDB)!");
                }
            }
        } finally {
            try (PreparedStatement dropTempStmt = connection.prepareStatement(dropTempTableSQL)) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Dropping temporary table (MySQL/MariaDB)...");
                }
                dropTempStmt.executeUpdate();
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Temporary table dropped (MySQL/MariaDB)!");
                }
            }
        }

        StatSyncTask.progressFrom++;
    }

    /**
     * Update the positions for all players in the database (SQLite version).
     * This method ranks players based on the amount of each stat and updates the
     * position column in the database.
     *
     * @param connection The connection to the database.
     * @param tableName  The name of the table.
     */
    private static void updatePositionsSQLite(Connection connection, String tableName) throws SQLException {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Updating positions in table (SQLite): {}", tableName);
        }

        String updatePositionsSQL = """
                    WITH ranked_data_cte AS (
                        SELECT
                            player_id,
                            stat_name,
                            ROW_NUMBER() OVER (PARTITION BY stat_name ORDER BY amount DESC) AS row_num
                        FROM %s
                        WHERE amount > 0
                    )
                    UPDATE %s
                    SET position = (
                        SELECT row_num
                        FROM ranked_data_cte
                        WHERE ranked_data_cte.player_id = %s.player_id
                          AND ranked_data_cte.stat_name = %s.stat_name
                          AND ranked_data_cte.row_num <= 5
                    )
                    WHERE EXISTS (
                        SELECT 1
                        FROM ranked_data_cte
                        WHERE ranked_data_cte.player_id = %s.player_id
                          AND ranked_data_cte.stat_name = %s.stat_name
                          AND ranked_data_cte.row_num <= 5
                    )
                """.formatted(tableName, tableName, tableName, tableName, tableName, tableName);

        try (PreparedStatement updateStmt = connection.prepareStatement(updatePositionsSQL)) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Updating positions in table (SQLite)...");
            }
            updateStmt.executeUpdate();
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Positions updated in table (SQLite)!");
            }
        }

        StatSyncTask.progressFrom++;
    }

    /**
     * Populate the 'hall_of_fame' table with the top players based on their score.
     *
     * @param connection The connection to the database.
     */
    public static void populateHallOfFame(Connection connection) {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Populating Hall of Fame ...");
        }

        try {
            // Reset the 'hall_of_fame' table
            String clearHallOfFameSQL;

            // SQLite
            if ("SQLITE".equalsIgnoreCase(DB_TYPE)) {
                clearHallOfFameSQL = "DELETE FROM hall_of_fame";
            } else {
                clearHallOfFameSQL = "TRUNCATE TABLE hall_of_fame";
            }
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("SQL: {}", clearHallOfFameSQL);
            }

            try (PreparedStatement clearStmt = connection.prepareStatement(clearHallOfFameSQL)) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Clearing Hall of Fame ...");
                }
                clearStmt.executeUpdate();
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Hall of Fame cleared!");
                }
            }

            // Calculate the score for each player
            String scoreCalculationSQL = generateScoreCalculationSQL();

            try (PreparedStatement scoreStmt = connection.prepareStatement(scoreCalculationSQL);
                    ResultSet rs = scoreStmt.executeQuery()) {
                if (PlayerStatistics.DEBUG) {
                    PlayerStatistics.LOGGER.info("Calculating player scores ...");
                }

                // Insert the top players into the 'hall_of_fame' table
                String insertHallOfFameSQL = """
                            INSERT INTO hall_of_fame (player_id, first_place, second_place, third_place, fourth_place, fifth_place, score)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement insertStmt = connection.prepareStatement(insertHallOfFameSQL)) {
                    while (rs.next()) {
                        if (PlayerStatistics.DEBUG) {
                            PlayerStatistics.LOGGER.info("Inserting player into Hall of Fame: {}",
                                    rs.getInt("player_id"));
                        }
                        insertStmt.setInt(1, rs.getInt("player_id"));
                        insertStmt.setInt(2, rs.getInt("first_place"));
                        insertStmt.setInt(3, rs.getInt("second_place"));
                        insertStmt.setInt(4, rs.getInt("third_place"));
                        insertStmt.setInt(5, rs.getInt("fourth_place"));
                        insertStmt.setInt(6, rs.getInt("fifth_place"));
                        insertStmt.setInt(7, rs.getInt("score"));
                        insertStmt.addBatch();
                    }
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Executing batch insert ...");
                    }
                    insertStmt.executeBatch();
                    if (PlayerStatistics.DEBUG) {
                        PlayerStatistics.LOGGER.info("Batch insert executed!");
                    }
                }
            }

        } catch (SQLException e) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Trace: ", e);
            }
            PlayerStatistics.LOGGER.error("Error populating Hall of Fame: {}", e.getMessage());
        }
    }

    /**
     * Generate the SQL query for calculating the score for each player.
     *
     * @return The SQL query for calculating the score.
     */
    private static String generateScoreCalculationSQL() {
        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Generating score calculation SQL ...");
        }

        StringBuilder unionQueries = new StringBuilder();

        // Create a UNION query for all tables
        for (String tableName : TABLE_NAMES) {
            if (PlayerStatistics.DEBUG) {
                PlayerStatistics.LOGGER.info("Generating score calculation SQL for table: {}", tableName);
            }

            if (!unionQueries.isEmpty()) {
                unionQueries.append(" UNION ALL ");
            }
            unionQueries.append("""
                        SELECT
                            player_id,
                            CASE WHEN position = 1 THEN 10 ELSE 0 END AS first_place,
                            CASE WHEN position = 2 THEN 5 ELSE 0 END AS second_place,
                            CASE WHEN position = 3 THEN 3 ELSE 0 END AS third_place,
                            CASE WHEN position = 4 THEN 2 ELSE 0 END AS fourth_place,
                            CASE WHEN position = 5 THEN 1 ELSE 0 END AS fifth_place
                        FROM %s
                        WHERE position BETWEEN 1 AND 5
                    """.formatted(tableName));
        }

        if (PlayerStatistics.DEBUG) {
            PlayerStatistics.LOGGER.info("Generated SQL: {}", unionQueries);
        }
        // Calculate the total score for each player
        return """
                    SELECT
                        player_id,
                        SUM(first_place) AS first_place,
                        SUM(second_place) AS second_place,
                        SUM(third_place) AS third_place,
                        SUM(fourth_place) AS fourth_place,
                        SUM(fifth_place) AS fifth_place,
                        SUM(first_place + second_place + third_place + fourth_place + fifth_place) AS score
                    FROM (
                        %s
                    ) AS all_stats
                    GROUP BY player_id
                    ORDER BY score DESC
                """.formatted(unionQueries.toString());
    }
}
