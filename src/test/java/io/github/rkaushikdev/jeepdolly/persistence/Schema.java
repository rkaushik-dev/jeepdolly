package io.github.rkaushikdev.jeepdolly.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * What a database's schema holds, read off its metadata, so two schemas can be compared table by
 * table: the columns with their types and nullability, the primary key in column order, the unique
 * keys, the foreign keys, the named indexes, and the name of every constraint.
 *
 * <p>Every name is lower case. Indexes are the ones named {@code ix_}; the indexes the database
 * builds behind a key or a constraint on its own are not part of what either side declares.
 * Uniqueness is read as one thing whichever way it was declared: a unique constraint and a unique
 * index refuse the same rows, so both are unique keys and neither is counted among the indexes.
 */
final class Schema {

    /** One column as the database reports it. */
    record Column(String name, String type, int size, int digits, boolean nullable, boolean identity) {}

    /** One foreign key: its columns and the columns it points at. */
    record ForeignKey(String name, List<String> columns, String referenced, List<String> referencedColumns) {}

    /** One named index that allows duplicates. */
    record Index(String name, List<String> columns) {}

    /** One unique key, whether declared as a constraint or as a unique index. */
    record UniqueKey(String name, List<String> columns) {}

    /** The name the database gives the index it builds behind a constraint of the same name. */
    private static final Pattern BACKING = Pattern.compile("_index_\\d+$");

    private final Map<String, Set<Column>> columns = new TreeMap<>();
    private final Map<String, List<String>> primaryKeys = new TreeMap<>();
    private final Map<String, Set<ForeignKey>> foreignKeys = new TreeMap<>();
    private final Map<String, Set<Index>> indexes = new TreeMap<>();
    private final Map<String, Set<UniqueKey>> uniqueKeys = new TreeMap<>();
    private final Set<String> constraintNames = new TreeSet<>();

    private Schema() {}

    /** Read every table of the public schema, the migrations' own history aside. */
    static Schema of(DataSource source) {
        Schema schema = new Schema();
        try (Connection connection = source.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String table : tables(metadata)) {
                schema.columns.put(lower(table), columns(metadata, table));
                schema.primaryKeys.put(lower(table), primaryKey(metadata, table));
                schema.foreignKeys.put(lower(table), foreignKeys(metadata, table));
                Map<String, List<String>> unique = new TreeMap<>();
                schema.indexes.put(lower(table), indexes(metadata, table, unique));
                schema.uniqueKeys.put(lower(table), uniqueKeys(connection, table, unique));
            }
            schema.constraintNames.addAll(constraintNames(connection));
        } catch (SQLException trouble) {
            throw new IllegalStateException("cannot read the schema", trouble);
        }
        return schema;
    }

    Set<String> tables() {
        return columns.keySet();
    }

    Set<Column> columns(String table) {
        return columns.getOrDefault(table, Set.of());
    }

    List<String> primaryKey(String table) {
        return primaryKeys.getOrDefault(table, List.of());
    }

    Set<ForeignKey> foreignKeys(String table) {
        return foreignKeys.getOrDefault(table, Set.of());
    }

    Set<Index> indexes(String table) {
        return indexes.getOrDefault(table, Set.of());
    }

    Set<UniqueKey> uniqueKeys(String table) {
        return uniqueKeys.getOrDefault(table, Set.of());
    }

    /** The name of every constraint in the schema: keys, foreign keys and checks. */
    Set<String> constraintNames() {
        return constraintNames;
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<String> tables(DatabaseMetaData metadata) throws SQLException {
        List<String> found = new ArrayList<>();
        try (ResultSet tables = metadata.getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (!name.equalsIgnoreCase("flyway_schema_history")) {
                    found.add(name);
                }
            }
        }
        return found;
    }

    private static Set<Column> columns(DatabaseMetaData metadata, String table) throws SQLException {
        Set<Column> found = new TreeSet<>(Comparator.comparing(Column::name));
        try (ResultSet rows = metadata.getColumns(null, "PUBLIC", table, "%")) {
            while (rows.next()) {
                found.add(new Column(
                        lower(rows.getString("COLUMN_NAME")),
                        rows.getString("TYPE_NAME"),
                        rows.getInt("COLUMN_SIZE"),
                        rows.getInt("DECIMAL_DIGITS"),
                        "YES".equals(rows.getString("IS_NULLABLE")),
                        "YES".equals(rows.getString("IS_AUTOINCREMENT"))));
            }
        }
        return Set.copyOf(found);
    }

    private static List<String> primaryKey(DatabaseMetaData metadata, String table) throws SQLException {
        Map<Integer, String> bySequence = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, "PUBLIC", table)) {
            while (rows.next()) {
                bySequence.put(rows.getInt("KEY_SEQ"), lower(rows.getString("COLUMN_NAME")));
            }
        }
        return List.copyOf(bySequence.values());
    }

    private static Set<ForeignKey> foreignKeys(DatabaseMetaData metadata, String table) throws SQLException {
        Map<String, List<String[]>> byName = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getImportedKeys(null, "PUBLIC", table)) {
            while (rows.next()) {
                byName.computeIfAbsent(lower(rows.getString("FK_NAME")), name -> new ArrayList<>())
                        .add(new String[] {
                            String.format(Locale.ROOT, "%04d", rows.getInt("KEY_SEQ")),
                            lower(rows.getString("FKCOLUMN_NAME")),
                            lower(rows.getString("PKTABLE_NAME")),
                            lower(rows.getString("PKCOLUMN_NAME"))
                        });
            }
        }
        Set<ForeignKey> found = new HashSet<>();
        byName.forEach((name, parts) -> {
            parts.sort(Comparator.comparing(part -> part[0]));
            found.add(new ForeignKey(
                    name,
                    parts.stream().map(part -> part[1]).toList(),
                    parts.get(0)[2],
                    parts.stream().map(part -> part[3]).toList()));
        });
        return Set.copyOf(found);
    }

    /** The named indexes that allow duplicates; the unique ones go into {@code unique}. */
    private static Set<Index> indexes(DatabaseMetaData metadata, String table, Map<String, List<String>> unique)
            throws SQLException {
        Map<String, Map<Integer, String>> columnsByName = new TreeMap<>();
        Map<String, Boolean> uniqueByName = new TreeMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, "PUBLIC", table, false, false)) {
            while (rows.next()) {
                String name = lower(rows.getString("INDEX_NAME"));
                if (!name.startsWith("ix_") || BACKING.matcher(name).find()) {
                    continue;
                }
                columnsByName
                        .computeIfAbsent(name, key -> new TreeMap<>())
                        .put(rows.getInt("ORDINAL_POSITION"), lower(rows.getString("COLUMN_NAME")));
                uniqueByName.put(name, !rows.getBoolean("NON_UNIQUE"));
            }
        }
        Set<Index> found = new HashSet<>();
        columnsByName.forEach((name, byPosition) -> {
            List<String> columns = List.copyOf(byPosition.values());
            if (uniqueByName.get(name)) {
                unique.put(name, columns);
            } else {
                found.add(new Index(name, columns));
            }
        });
        return Set.copyOf(found);
    }

    private static Set<UniqueKey> uniqueKeys(Connection connection, String table, Map<String, List<String>> indexes)
            throws SQLException {
        Map<String, Map<Integer, String>> byName = new TreeMap<>();
        try (PreparedStatement query = connection.prepareStatement("select c.constraint_name, k.column_name,"
                + " k.ordinal_position from information_schema.table_constraints c"
                + " join information_schema.key_column_usage k on k.constraint_name = c.constraint_name"
                + " and k.table_name = c.table_name where c.table_schema = 'PUBLIC'"
                + " and c.constraint_type = 'UNIQUE' and c.table_name = ?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    byName.computeIfAbsent(lower(rows.getString(1)), name -> new TreeMap<>())
                            .put(rows.getInt(3), lower(rows.getString(2)));
                }
            }
        }
        Set<UniqueKey> found = new HashSet<>();
        byName.forEach((name, byPosition) -> found.add(new UniqueKey(name, List.copyOf(byPosition.values()))));
        indexes.forEach((name, columns) -> found.add(new UniqueKey(name, columns)));
        return Set.copyOf(found);
    }

    private static Set<String> constraintNames(Connection connection) throws SQLException {
        Set<String> found = new TreeSet<>();
        try (PreparedStatement query =
                        connection.prepareStatement("select constraint_name from information_schema.table_constraints"
                                + " where table_schema = 'PUBLIC' and lower(table_name) <> 'flyway_schema_history'");
                ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                found.add(lower(rows.getString(1)));
            }
        }
        return found;
    }
}
