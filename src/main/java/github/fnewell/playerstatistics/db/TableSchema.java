package github.fnewell.playerstatistics.db;

import java.util.List;
import java.util.Map;

/**
 * Represents a table in a database
 * 
 * @param name          Table name
 * @param columns       List of columns in the table
 * @param primaryKeys   List of primary keys in the table
 * @param foreignKeys   key: column, value: referenced_table(column)
 * @param withoutRowId  Whether the table has a without row id constraint
 */
public record TableSchema(String name, List<ColumnSchema> columns, List<String> primaryKeys,
        Map<String, String> foreignKeys, boolean withoutRowId) {
}
