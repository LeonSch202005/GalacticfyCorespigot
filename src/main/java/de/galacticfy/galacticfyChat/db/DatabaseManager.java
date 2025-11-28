package de.galacticfy.galacticfyChat.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public class DatabaseManager {

    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void init() {
        HikariConfig cfg = new HikariConfig();

        cfg.setJdbcUrl("jdbc:mariadb://localhost:3306/galacticfy_core");
        cfg.setUsername("galacticfy");
        cfg.setPassword("bKdHRouvvx0Gds7nEz4XVAh3zp1C2ldH");

        cfg.setDriverClassName("org.mariadb.jdbc.Driver");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setPoolName("GalacticfyChatPool");

        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(cfg);
        logger.info("[GalacticfyChat] Database-Pool initialisiert.");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager.init() wurde nicht aufgerufen.");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            logger.info("[GalacticfyChat] Database-Pool geschlossen.");
        }
    }
}
