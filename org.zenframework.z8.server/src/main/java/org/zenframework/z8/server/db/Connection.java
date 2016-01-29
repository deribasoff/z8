package org.zenframework.z8.server.db;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;

import org.zenframework.z8.server.engine.Database;
import org.zenframework.z8.server.logs.Trace;
import org.zenframework.z8.server.types.encoding;

public class Connection {
    static public int TransactionReadCommitted = java.sql.Connection.TRANSACTION_READ_COMMITTED;
    static public int TransactionReadUncommitted = java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
    static public int TransactionRepeatableRead = java.sql.Connection.TRANSACTION_REPEATABLE_READ;
    static public int TransactionSerializable = java.sql.Connection.TRANSACTION_SERIALIZABLE;

    private Database database = null;
    private java.sql.Connection connection = null;
    private Thread owner = null;

    private int transactionCount = 0;

    static private java.sql.Connection newConnection(Database database) {
        try {
            Class.forName(database.driver());
            
            java.sql.Connection connection = DriverManager.getConnection(database.connection(), database.user(),
                    database.password());

            if (database.vendor() == DatabaseVendor.SqlServer) {
                connection.setTransactionIsolation(TransactionReadUncommitted);

                //				PreparedStatement statement = connection.prepareStatement("set transaction isolation level snapshot");
                //				statement.execute();
                //				statement.close();
            }

            if (database.vendor() == DatabaseVendor.Postgres) {
/*                PreparedStatement statement = connection.prepareStatement("Alter user " + database.user()
                        + " set search_path to '" + database.schema() + "'");
                statement.execute();
                statement.close();*/
            }

            return connection;
        } catch (Throwable e) {
            Trace.logError(e);
            throw new RuntimeException(e);
        }
    }

    static public Connection connect(Database database) {
        return new Connection(database, newConnection(database));
    }

    private Connection(Database database, java.sql.Connection connection) {
        this.database = database;
        this.connection = connection;
    }

    private void safeClose() {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
        }
    }
    
    private void reconnect() {
        safeClose();
        connection = newConnection(database);

        initClientInfo();
    }

    public boolean isCurrent() {
        return owner.equals(Thread.currentThread());
    }

    public boolean isInUse() {
        return !isCurrent() ? owner.isAlive() : false;
    }

    public boolean isClosed() {
        return connection == null;
    }

    public void use() {
        if(isClosed())
            reconnect();
        
        owner = Thread.currentThread();
        initClientInfo();
    }

    private void initClientInfo() {
        try {
            connection.setClientInfo("ApplicationName", owner.getName());
        } catch(SQLClientInfoException e) {
        }
    }
    
    public java.sql.Connection getSqlConnection() {
        return connection;
    }

    public Database database() {
        return database;
    }

    public String schema() {
        return database.schema();
    }

    public encoding charset() {
        return database.charset();
    }

    public DatabaseVendor vendor() {
        return database.vendor();
    }

    private void checkAndReconnect(SQLException exception) throws SQLException {
        String message = exception.getMessage();
        String sqlState = exception.getSQLState();
        int errorCode = exception.getErrorCode();
        
        SqlExceptionConverter.rethrowIfKnown(database.vendor(), exception);
            
        System.out.println(message + "( error code: " + errorCode + "; sqlState: " + sqlState + ") - reconnecting...");

        /* 
            Postgres; Class 08 — Connection Exception
            SQLState    Description
            08000       connection exception
            08003       connection does not exist
            08006       connection failure
            08001       sqlclient unable to establish sqlconnection
            08004       sqlserver rejected establishment of sqlconnection
            08007       transaction resolution unknown
            08P01       protocol violation
        */
        
        if (transactionCount != 0)
            throw exception;

        reconnect();
    }

    private void setAutoCommit(boolean autoCommit) throws SQLException {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            checkAndReconnect(e);
            connection.setAutoCommit(autoCommit);
        }
    }

    public void beginTransaction() {
        try {
            if(transactionCount == 0)
                setAutoCommit(false);
    
            transactionCount++;
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void commit() {
        try {
            if(transactionCount != 0) {
                connection.commit();
                if(transactionCount - 1 == 0)
                    setAutoCommit(true);
                transactionCount--;
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rollback() {
        try {
            if(transactionCount != 0) {
                connection.rollback();
                if(transactionCount - 1 == 0)
                    setAutoCommit(true);
                transactionCount--;
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isOpen() {
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            Trace.logError(e);
        }
        return false;
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        try {
            return connection.prepareStatement(sql);
        } catch (SQLException e) {
            checkAndReconnect(e);
            return connection.prepareStatement(sql);
        }
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        try {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        } catch (SQLException e) {
            checkAndReconnect(e);
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
    }

    public ResultSet executeQuery(BasicStatement statement) throws SQLException {
        try {
            return statement.statement().executeQuery();
        } catch (SQLException e) {
            checkAndReconnect(e);
            statement.safeClose();
            statement.prepare();
            return statement.statement().executeQuery();
        }
    }

    public void executeUpdate(BasicStatement statement) throws SQLException {
        try {
            statement.statement().executeUpdate();
        } catch (SQLException e) {
            checkAndReconnect(e);
            statement.safeClose();
            statement.prepare();
            statement.statement().executeUpdate();
        }
    }
}
