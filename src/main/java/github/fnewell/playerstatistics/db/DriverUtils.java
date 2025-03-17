package github.fnewell.playerstatistics.db;

import github.fnewell.playerstatistics.utils.ConfigUtils;
import github.fnewell.playerstatistics.utils.PSLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Utility class for handling driver-related operations.
 */
public class DriverUtils {

    public static DriverShim customDriverShim;
    private static final String DB_TYPE = ConfigUtils.config.getConfig("database").getString("type");

    // Driver map: DB type → [cesta k JAR, JDBC classpath]
    private static final Map<String, String[]> DRIVERS = Map.of(
            "SQLITE",
            new String[] { "mods/player-statistics/libs/sqlite-jdbc-3.47.1.0.jar", "org.sqlite.JDBC" },
            "MYSQL",
            new String[] { "mods/player-statistics/libs/mysql-connector-j-9.2.0.jar", "com.mysql.cj.jdbc.Driver" },
            "MARIADB",
            new String[] { "mods/player-statistics/libs/mariadb-java-client-3.5.1.jar", "org.mariadb.jdbc.Driver" },
            "POSTGRESQL",
            new String[] { "mods/player-statistics/libs/postgresql-42.7.4.jar", "org.postgresql.Driver" });

    /**
     * Function to register a custom SQLite JDBC driver via DriverShim
     * 
     * @throws RuntimeException If an error occurs during the registration process
     */
    public static void registerDriver() throws RuntimeException {
        try {
            PSLogger.debug("Registering custom {} driver ...", DB_TYPE);

            // Path to the custom SQLite JDBC driver
            Path driverPath = FabricLoader.getInstance().getGameDir().resolve(DRIVERS.get(DB_TYPE)[0]);
            URL driverUrl = driverPath.toUri().toURL();

            PSLogger.debug("Driver URL: {}", driverUrl);

            // Load driver via custom ClassLoader
            URLClassLoader loader = new URLClassLoader(new URL[] { driverUrl }, ClassLoader.getPlatformClassLoader());
            Class<?> driverClass = Class.forName(DRIVERS.get(DB_TYPE)[1], true, loader);
            Driver customDriver = (Driver) driverClass.getDeclaredConstructor().newInstance();

            PSLogger.debug("Driver Class: {}; Loaded from: {}", customDriver.getClass().getName(),
                    customDriver.getClass().getProtectionDomain().getCodeSource().getLocation());

            // Register DriverShim instead of the original driver
            customDriverShim = new DriverShim(customDriver);
            DriverManager.registerDriver(customDriverShim);
            PSLogger.debug("Custom {} driver registered!", DB_TYPE);
        } catch (Exception e) {
            PSLogger.debug("Trace: ", e);
            throw new RuntimeException(
                    "Failed to register custom " + DB_TYPE + " driver via DriverShim: " + e.getMessage());
        }
    }

    /**
     * Entry point to check for required drivers and download them if missing.
     * 
     * @throws RuntimeException If an error occurs during the check or download process
     */
    public static void checkDrivers() throws RuntimeException {
        try {
            PSLogger.debug("Checking for required drivers ...");

            // Check and download missing drivers
            Map<String, String> drivers = Map.of(
                    "sqlite-jdbc-3.47.1.0.jar",
                    "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.47.1.0/sqlite-jdbc-3.47.1.0.jar",
                    "mariadb-java-client-3.5.1.jar",
                    "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.5.1/mariadb-java-client-3.5.1.jar",
                    "postgresql-42.7.4.jar",
                    "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar",
                    "mysql-connector-j-9.2.0.jar",
                    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar");

            Path driverFolder = FabricLoader.getInstance().getGameDir().resolve("mods/player-statistics/libs");

            // Ensure the folder exists
            if (!Files.exists(driverFolder)) {
                Files.createDirectories(driverFolder);
            }

            // Check and download missing drivers
            for (Map.Entry<String, String> entry : drivers.entrySet()) {
                String fileName = entry.getKey();
                String downloadUrl = entry.getValue();

                Path filePath = driverFolder.resolve(fileName);

                if (!Files.exists(filePath)) {
                    downloadFile(downloadUrl, filePath);
                } else {
                    PSLogger.debug("Driver {} already exists.", fileName);
                }
            }

            PSLogger.debug("Required drivers checked!");
        } catch (Exception e) {
            PSLogger.debug("Trace: ", e);
            throw new RuntimeException("Failed to check required drivers: " + e.getMessage());
        }
    }

    /**
     * Download a file from a URL to a specific path.
     *
     * @param fileUrl The URL of the file to download.
     * @param destination The path where the file should be saved.
     * @throws IOException If an error occurs during the download.
     * @throws InterruptedException If the download is interrupted.
     */
    private static void downloadFile(String fileUrl, Path destination) throws IOException, InterruptedException {
        PSLogger.info("Downloading {} to {}", fileUrl, destination);

        HttpResponse<Path> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file: " + fileUrl + " (HTTP " + response.statusCode() + ")");
        }

        PSLogger.info("... successfully downloaded");
    }
}
