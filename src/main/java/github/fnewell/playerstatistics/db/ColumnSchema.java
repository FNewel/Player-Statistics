package github.fnewell.playerstatistics.db;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import github.fnewell.playerstatistics.utils.PSLogger;

/**
 * Represents a column in a database table
 * 
 * @param name           Column name
 * @param type           Column type
 * @param notNull        Whether the column is not null
 * @param autoIncrement  Whether the column is an auto-incrementing primary key
 * @param defaultValue   Default value for the column
 */
public record ColumnSchema(String name, String type, boolean notNull, boolean autoIncrement, String defaultValue) {

    // SQLite date format
    private static final DateTimeFormatter SQLITE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Returns the column type for the given database type
     *
     * @param dbType Database type (SQLITE, MYSQL, MARIADB, POSTGRESQL)
     * @return Column type for the given database type
     */
    public String getColumnType(String dbType) {
        PSLogger.debug("Getting column type {} for {} database...", this.type, dbType);

        return switch (this.type) {
            case "BIT" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "SMALLINT"
                    : dbType.equalsIgnoreCase("SQLITE") ? "INTEGER" : "BIT";
            case "TINYINT UNSIGNED" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "SMALLINT"
                    : dbType.equalsIgnoreCase("SQLITE") ? "INTEGER" : "TINYINT UNSIGNED";
            case "SMALLINT UNSIGNED" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "SMALLINT"
                    : dbType.equalsIgnoreCase("SQLITE") ? "INTEGER" : "SMALLINT UNSIGNED";
            case "MEDIUMINT UNSIGNED" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "INTEGER"
                    : dbType.equalsIgnoreCase("SQLITE") ? "INTEGER" : "MEDIUMINT UNSIGNED";
            case "INTEGER UNSIGNED" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "INTEGER"
                    : dbType.equalsIgnoreCase("SQLITE") ? "INTEGER" : "INTEGER UNSIGNED";
            case "DATETIME" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "TIMESTAMP"
                    : dbType.equalsIgnoreCase("SQLITE") ? "TEXT" : "DATETIME";
            case "BLOB" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "BYTEA" : "BLOB";
            case "VARCHAR(128)" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "TEXT" : "VARCHAR(128)";
            case "VARCHAR(256)" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "TEXT" : "VARCHAR(256)";
            case "VARCHAR(16)" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "TEXT" : "VARCHAR(16)";
            case "VARCHAR(36)" -> dbType.equalsIgnoreCase("POSTGRESQL") ? "TEXT" : "VARCHAR(36)";
            default -> this.type; // Other types are not changed
        };
    }

    /**
     * Format LocalDateTime to the correct format (TEXT for SQLite, Timestamp for MySQL/PostgreSQL)
     *
     * @param dateTime LocalDateTime value, which will be formatted
     * @param dbType   Database type (SQLITE, MYSQL, POSTGRESQL, MARIADB)
     * @return Formatted value
     */
    public static Object formatDatetime(LocalDateTime dateTime, String dbType) {
        PSLogger.debug("Formatting datetime {} for {} database...", dateTime, dbType);

        if (dateTime == null)
            return null;

        return dbType.equalsIgnoreCase("SQLITE")
                ? dateTime.format(SQLITE_FORMATTER) // TEXT for SQLite
                : Timestamp.valueOf(dateTime); // Timestamp for MySQL, MariaDB, PostgreSQL
    }

    /**
     * Format database format (TEXT/Timestamp) back to LocalDateTime
     *
     * @param value  Value loaded from the database
     * @param dbType Database type (SQLITE, MYSQL, POSTGRESQL, MARIADB)
     * @return LocalDateTime object or null if value is null
     */
    public static LocalDateTime parseDatetime(Object value, String dbType) {
        PSLogger.debug("Parsing datetime {} for {} database...", value, dbType);

        if (value == null)
            return null;

        if (dbType.equalsIgnoreCase("SQLITE")) {
            // SQlite saves dates as TEXT → parse string
            return LocalDateTime.parse(value.toString(), SQLITE_FORMATTER);
        } else {
            // MySQL, PostgreSQL, MariaDB saves dates as Timestamp → conversion
            return ((Timestamp) value).toLocalDateTime();
        }
    }
}
