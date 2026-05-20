package fr.silenthill99.reddawnBan.mysql;

public class DbCredentials {
    private final String host;
    private final String dbName;
    private final int port;
    private final String user;
    private final String password;


    public DbCredentials(String host, String dbName, int port, String user, String password) {
        this.host = host;
        this.dbName = dbName;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public String toURL() {
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
