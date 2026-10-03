package com.saivandan.crm;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** One-time copier for the preserved crm.db backup. */
public final class SqliteToMysqlMigration {
    private static final List<String> TABLES = List.of(
            "enquiries", "properties", "followups", "qualifications", "sitevisits", "negotiations",
            "bookings", "documentcollections", "loans", "agreements", "customerpayments", "possessions",
            "supporttickets", "employees", "attendance", "leaverequests", "salarypayments", "registeredusers",
            "vendors", "purchaseorders", "vendorbills", "vendorpayments", "pettycashentries", "pettycashapprovals"
    );

    private SqliteToMysqlMigration() {
    }

    public static void main(String[] args) throws Exception {
        String sqlitePath = System.getProperty("sqlite.db", "crm.db");
        String mysqlUrl = setting("CRM_DB_URL", "jdbc:mysql://localhost:3307/skyline_crm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = setting("CRM_DB_USER", "root");
        String password = setting("CRM_DB_PASSWORD", "");

        Class.forName("org.sqlite.JDBC");
        Class.forName("com.mysql.cj.jdbc.Driver");

        String sqliteUrl = "jdbc:sqlite:" + Path.of(sqlitePath).toAbsolutePath().normalize();
        System.out.println("Copying SQLite data from: " + Path.of(sqlitePath).toAbsolutePath().normalize());
        System.out.println("Copying SQLite data to: " + mysqlUrl);

        try (Connection sqlite = java.sql.DriverManager.getConnection(sqliteUrl);
             Connection mysql = java.sql.DriverManager.getConnection(mysqlUrl, user, password)) {
            mysql.setAutoCommit(false);
            for (String table : TABLES) {
                int processed = copyTable(sqlite, mysql, table);
                System.out.println(table + ": " + processed + " rows processed (existing duplicates kept once)");
            }
            mysql.commit();
        }
        System.out.println("Migration completed. crm.db was not modified.");
    }

    private static int copyTable(Connection sqlite, Connection mysql, String table) throws SQLException {
        List<String> columns = columns(sqlite, table);
        if (columns.isEmpty()) {
            return 0;
        }
        String quotedColumns = columns.stream().map(c -> "`" + c + "`").reduce((a, b) -> a + ", " + b).orElseThrow();
        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
        String select = "SELECT " + columns.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + ", " + b).orElseThrow()
                + " FROM \"" + table + "\"";
        // Keep the migration rerunnable. The CRM uses unique business keys for
        // some tables, so duplicate source/target rows must not abort the batch.
        String firstColumn = "`" + columns.get(0) + "`";
        String insert = "INSERT INTO `" + table + "` (" + quotedColumns + ") VALUES (" + placeholders + ")"
                + " ON DUPLICATE KEY UPDATE " + firstColumn + " = " + firstColumn;
        int count = 0;
        try (Statement source = sqlite.createStatement();
             ResultSet rows = source.executeQuery(select);
             PreparedStatement target = mysql.prepareStatement(insert)) {
            while (rows.next()) {
                for (int i = 0; i < columns.size(); i++) {
                    target.setObject(i + 1, rows.getObject(i + 1));
                }
                target.addBatch();
                count++;
                if (count % 500 == 0) {
                    target.executeBatch();
                }
            }
            target.executeBatch();
        }
        return count;
    }

    private static List<String> columns(Connection sqlite, String table) throws SQLException {
        List<String> result = new ArrayList<>();
        DatabaseMetaData meta = sqlite.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, null)) {
            while (rs.next()) {
                result.add(rs.getString("COLUMN_NAME"));
            }
        }
        return result;
    }

    private static String setting(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? fallback : environment;
    }
}
