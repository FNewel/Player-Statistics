package github.fnewell.playerstatistics.db;

import java.util.List;
import java.util.Map;

/**
 * Represents the schema of a database table
 */
public class DatabaseSchema {
    public static final List<TableSchema> TABLES = List.of(
            new TableSchema("sync_metadata",
                    List.of(
                            new ColumnSchema("id", "BIT", true, false, null),
                            new ColumnSchema("last_update", "DATETIME", false, false, null),
                            new ColumnSchema("server_name", "VARCHAR(256)", false, false,
                                    null),
                            new ColumnSchema("server_desc", "VARCHAR(256)", false, false,
                                    null),
                            new ColumnSchema("server_url", "VARCHAR(256)", false, false,
                                    null),
                            new ColumnSchema("server_icon", "BLOB", false, false, null)),
                    List.of("id"),
                    Map.of(), false),

            new TableSchema("uuid_map",
                    List.of(
                            new ColumnSchema("id", "SMALLINT UNSIGNED", true, true, null),
                            new ColumnSchema("player_uuid", "VARCHAR(36)", true, false,
                                    null),
                            new ColumnSchema("player_nick", "VARCHAR(16)", false, false,
                                    null),
                            new ColumnSchema("player_last_online", "DATETIME", false, false,
                                    null)),
                    List.of("id"),
                    Map.of(), false),

            new TableSchema("hall_of_fame",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("score", "MEDIUMINT UNSIGNED", true, false,
                                    null)),
                    List.of("player_id"),
                    Map.of("player_id", "uuid_map(id)"), false),

            new TableSchema("stats_map",
                    List.of(
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, true,
                                    null),
                            new ColumnSchema("stat_name", "VARCHAR(128)", true, false,
                                    null)),
                    List.of("stat_id"),
                    Map.of(), false),

            new TableSchema("stat_custom",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_broken",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_crafted",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_dropped",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_picked_up",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_used",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_mined",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_killed",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true),

            new TableSchema("stat_killed_by",
                    List.of(
                            new ColumnSchema("player_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("stat_id", "SMALLINT UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("amount", "INTEGER UNSIGNED", true, false,
                                    null),
                            new ColumnSchema("position", "TINYINT UNSIGNED", false, false,
                                    null)),
                    List.of("player_id", "stat_id"),
                    Map.of(
                            "player_id", "uuid_map(id)",
                            "stat_id", "stats_map(stat_id)"),
                    true));
}
