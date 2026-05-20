package fr.silenthill99.reddawnBan.mysql;

import fr.silenthill99.reddawnBan.Main;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection {
    private final Main main = Main.getInstance();
    private final DbCredentials dbCredentials;
    private Connection connection;

    public DbConnection(DbCredentials dbCredentials) {
        this.dbCredentials = dbCredentials;
        this.connect();
    }

    private void connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            this.connection = DriverManager.getConnection(dbCredentials.toURL(), dbCredentials.getUser(), dbCredentials.getPassword());
            main.getLogger().info("Connected to database.");
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
        }
    }

    public Connection getConnection() throws SQLException {
        if (this.connection != null) {
            if (!this.connection.isClosed()) {
                return this.connection;
            }
        }
        connect();
        return connection;
    }
}
