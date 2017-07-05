/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.sql;

import java.util.Properties;

/**
 * <P>A connection (session) with a specific
 * database. SQL statements are executed and results are returned
 * within the context of a connection.
 * <P>
 * A <code>Connection</code> object's database is able to provide information
 * describing its tables, its supported SQL grammar, its stored
 * procedures, the capabilities of this connection, and so on. This
 * information is obtained with the <code>getMetaData</code> method.
 *
 * <P><B>Note:</B> When configuring a <code>Connection</code>, JDBC applications
 *  should use the appropritate <code>Connection</code> method such as
 *  <code>setAutoCommit</code> or <code>setTransactionIsolation</code>.
 *  Applications should not invoke SQL commands directly to change the connection's
 *   configuration when there is a JDBC method available.  By default a <code>Connection</code> object is in
 * auto-commit mode, which means that it automatically commits changes
 * after executing each statement. If auto-commit mode has been
 * disabled, the method <code>commit</code> must be called explicitly in
 * order to commit changes; otherwise, database changes will not be saved.
 * <P>
 * A new <code>Connection</code> object created using the JDBC 2.1 core API
 * has an initially empty type map associated with it. A user may enter a
 * custom mapping for a UDT in this type map.
 * When a UDT is retrieved from a data source with the
 * method <code>ResultSet.getObject</code>, the <code>getObject</code> method
 * will check the connection's type map to see if there is an entry for that
 * UDT.  If so, the <code>getObject</code> method will map the UDT to the
 * class indicated.  If there is no entry, the UDT will be mapped using the
 * standard mapping.
 * <p>
 * A user may create a new type map, which is a <code>java.util.Map</code>
 * object, make an entry in it, and pass it to the <code>java.sql</code>
 * methods that can perform custom mapping.  In this case, the method
 * will use the given type map instead of the one associated with
 * the connection.
 * <p>
 * For example, the following code fragment specifies that the SQL
 * type <code>ATHLETES</code> will be mapped to the class
 * <code>Athletes</code> in the Java programming language.
 * The code fragment retrieves the type map for the <code>Connection
 * </code> object <code>con</code>, inserts the entry into it, and then sets
 * the type map with the new entry as the connection's type map.
 * <pre>
 *      java.util.Map map = con.getTypeMap();
 *      map.put("mySchemaName.ATHLETES", Class.forName("Athletes"));
 *      con.setTypeMap(map);
 * </pre>
 *
 * @see DriverManager#getConnection
 * @see Statement
 * @see ResultSet
 * @see DatabaseMetaData
 */
public interface Connection  extends Wrapper {

    /**
     * Creates a <code>Statement</code> object for sending
     * SQL statements to the database.
     * SQL statements without parameters are normally
     * executed using <code>Statement</code> objects. If the same SQL statement
     * is executed many times, it may be more efficient to use a
     * <code>PreparedStatement</code> object.
     * <P>
     * Result sets created using the returned <code>Statement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @return a new default <code>Statement</code> object
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    Statement createStatement() throws SQLException;

    /**
     * Creates a <code>PreparedStatement</code> object for sending
     * parameterized SQL statements to the database.
     * <P>
     * A SQL statement with or without IN parameters can be
     * pre-compiled and stored in a <code>PreparedStatement</code> object. This
     * object can then be used to efficiently execute this statement
     * multiple times.
     *
     * <P><B>Note:</B> This method is optimized for handling
     * parametric SQL statements that benefit from precompilation. If
     * the driver supports precompilation,
     * the method <code>prepareStatement</code> will send
     * the statement to the database for precompilation. Some drivers
     * may not support precompilation. In this case, the statement may
     * not be sent to the database until the <code>PreparedStatement</code>
     * object is executed.  This has no direct effect on users; however, it does
     * affect which methods throw certain <code>SQLException</code> objects.
     * <P>
     * Result sets created using the returned <code>PreparedStatement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql an SQL statement that may contain one or more '?' IN
     * parameter placeholders
     * @return a new default <code>PreparedStatement</code> object containing the
     * pre-compiled SQL statement
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    PreparedStatement prepareStatement(String sql)
        throws SQLException;

    /**
     * Creates a <code>CallableStatement</code> object for calling
     * database stored procedures.
     * The <code>CallableStatement</code> object provides
     * methods for setting up its IN and OUT parameters, and
     * methods for executing the call to a stored procedure.
     *
     * <P><B>Note:</B> This method is optimized for handling stored
     * procedure call statements. Some drivers may send the call
     * statement to the database when the method <code>prepareCall</code>
     * is done; others
     * may wait until the <code>CallableStatement</code> object
     * is executed. This has no
     * direct effect on users; however, it does affect which method
     * throws certain SQLExceptions.
     * <P>
     * Result sets created using the returned <code>CallableStatement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql an SQL statement that may contain one or more '?'
     * parameter placeholders. Typically this statement is specified using JDBC
     * call escape syntax.
     * @return a new default <code>CallableStatement</code> object containing the
     * pre-compiled SQL statement
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    CallableStatement prepareCall(String sql) throws SQLException;

    /**
     * Converts the given SQL statement into the system's native SQL grammar.
     * A driver may convert the JDBC SQL grammar into its system's
     * native SQL grammar prior to sending it. This method returns the
     * native form of the statement that the driver would have sent.
     *
     * @param sql an SQL statement that may contain one or more '?'
     * parameter placeholders
     * @return the native form of this statement
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    String nativeSQL(String sql) throws SQLException;

    /**
     * Sets this connection's auto-commit mode to the given state.
     * If a connection is in auto-commit mode, then all its SQL
     * statements will be executed and committed as individual
     * transactions.  Otherwise, its SQL statements are grouped into
     * transactions that are terminated by a call to either
     * the method <code>commit</code> or the method <code>rollback</code>.
     * By default, new connections are in auto-commit
     * mode.
     * <P>
     * The commit occurs when the statement completes. The time when the statement
     * completes depends on the type of SQL Statement:
     * <ul>
     * <li>For DML statements, such as Insert, Update or Delete, and DDL statements,
     * the statement is complete as soon as it has finished executing.
     * <li>For Select statements, the statement is complete when the associated result
     * set is closed.
     * <li>For <code>CallableStatement</code> objects or for statements that return
     * multiple results, the statement is complete
     * when all of the associated result sets have been closed, and all update
     * counts and output parameters have been retrieved.
     *</ul>
     * <P>
     * <B>NOTE:</B>  If this method is called during a transaction and the
     * auto-commit mode is changed, the transaction is committed.  If
     * <code>setAutoCommit</code> is called and the auto-commit mode is
     * not changed, the call is a no-op.
     *
     * @param autoCommit <code>true</code> to enable auto-commit mode;
     *         <code>false</code> to disable it
     * @exception SQLException if a database access error occurs,
     *  setAutoCommit(true) is called while participating in a distributed transaction,
     * or this method is called on a closed connection
     * @see #getAutoCommit
     */
    void setAutoCommit(boolean autoCommit) throws SQLException;

    /**
     * Retrieves the current auto-commit mode for this <code>Connection</code>
     * object.
     *
     * @return the current state of this <code>Connection</code> object's
     *         auto-commit mode
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @see #setAutoCommit
     */
    boolean getAutoCommit() throws SQLException;

    /**
     * Makes all changes made since the previous
     * commit/rollback permanent and releases any database locks
     * currently held by this <code>Connection</code> object.
     * This method should be
     * used only when auto-commit mode has been disabled.
     *
     * @exception SQLException if a database access error occurs,
     * this method is called while participating in a distributed transaction,
     * if this method is called on a closed conection or this
     *            <code>Connection</code> object is in auto-commit mode
     * @see #setAutoCommit
     */
    void commit() throws SQLException;

    /**
     * Undoes all changes made in the current transaction
     * and releases any database locks currently held
     * by this <code>Connection</code> object. This method should be
     * used only when auto-commit mode has been disabled.
     *
     * @exception SQLException if a database access error occurs,
     * this method is called while participating in a distributed transaction,
     * this method is called on a closed connection or this
     *            <code>Connection</code> object is in auto-commit mode
     * @see #setAutoCommit
     */
    void rollback() throws SQLException;

    /**
     * Releases this <code>Connection</code> object's database and JDBC resources
     * immediately instead of waiting for them to be automatically released.
     * <P>
     * Calling the method <code>close</code> on a <code>Connection</code>
     * object that is already closed is a no-op.
     * <P>
     * It is <b>strongly recommended</b> that an application explicitly
     * commits or rolls back an active transaction prior to calling the
     * <code>close</code> method.  If the <code>close</code> method is called
     * and there is an active transaction, the results are implementation-defined.
     * <P>
     *
     * @exception SQLException SQLException if a database access error occurs
     */
    void close() throws SQLException;

    /**
     * Retrieves whether this <code>Connection</code> object has been
     * closed.  A connection is closed if the method <code>close</code>
     * has been called on it or if certain fatal errors have occurred.
     * This method is guaranteed to return <code>true</code> only when
     * it is called after the method <code>Connection.close</code> has
     * been called.
     * <P>
     * This method generally cannot be called to determine whether a
     * connection to a database is valid or invalid.  A typical client
     * can determine that a connection is invalid by catching any
     * exceptions that might be thrown when an operation is attempted.
     *
     * @return <code>true</code> if this <code>Connection</code> object
     *         is closed; <code>false</code> if it is still open
     * @exception SQLException if a database access error occurs
     */
    boolean isClosed() throws SQLException;

    //======================================================================
    // Advanced features:

    /**
     * Retrieves a <code>DatabaseMetaData</code> object that contains
     * metadata about the database to which this
     * <code>Connection</code> object represents a connection.
     * The metadata includes information about the database's
     * tables, its supported SQL grammar, its stored
     * procedures, the capabilities of this connection, and so on.
     *
     * @return a <code>DatabaseMetaData</code> object for this
     *         <code>Connection</code> object
     * @exception  SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    DatabaseMetaData getMetaData() throws SQLException;

    /**
     * Puts this connection in read-only mode as a hint to the driver to enable
     * database optimizations.
     *
     * <P><B>Note:</B> This method cannot be called during a transaction.
     *
     * @param readOnly <code>true</code> enables read-only mode;
     *        <code>false</code> disables it
     * @exception SQLException if a database access error occurs, this
     *  method is called on a closed connection or this
     *            method is called during a transaction
     */
    void setReadOnly(boolean readOnly) throws SQLException;

    /**
     * Retrieves whether this <code>Connection</code>
     * object is in read-only mode.
     *
     * @return <code>true</code> if this <code>Connection</code> object
     *         is read-only; <code>false</code> otherwise
     * @exception SQLException SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    boolean isReadOnly() throws SQLException;

    /**
     * Sets the given catalog name in order to select
     * a subspace of this <code>Connection</code> object's database
     * in which to work.
     * <P>
     * If the driver does not support catalogs, it will
     * silently ignore this request.
     *
     * @param catalog the name of a catalog (subspace in this
     *        <code>Connection</code> object's database) in which to work
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @see #getCatalog
     */
    void setCatalog(String catalog) throws SQLException;

    /**
     * Retrieves this <code>Connection</code> object's current catalog name.
     *
     * @return the current catalog name or <code>null</code> if there is none
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @see #setCatalog
     */
    String getCatalog() throws SQLException;

    /**
     * A constant indicating that transactions are not supported.
     */
    int TRANSACTION_NONE             = 0;

    /**
     * A constant indicating that
     * dirty reads, non-repeatable reads and phantom reads can occur.
     * This level allows a row changed by one transaction to be read
     * by another transaction before any changes in that row have been
     * committed (a "dirty read").  If any of the changes are rolled back,
     * the second transaction will have retrieved an invalid row.
     */
    int TRANSACTION_READ_UNCOMMITTED = 1;

    /**
     * A constant indicating that
     * dirty reads are prevented; non-repeatable reads and phantom
     * reads can occur.  This level only prohibits a transaction
     * from reading a row with uncommitted changes in it.
     */
    int TRANSACTION_READ_COMMITTED   = 2;

    /**
     * A constant indicating that
     * dirty reads and non-repeatable reads are prevented; phantom
     * reads can occur.  This level prohibits a transaction from
     * reading a row with uncommitted changes in it, and it also
     * prohibits the situation where one transaction reads a row,
     * a second transaction alters the row, and the first transaction
     * rereads the row, getting different values the second time
     * (a "non-repeatable read").
     */
    int TRANSACTION_REPEATABLE_READ  = 4;

    /**
     * A constant indicating that
     * dirty reads, non-repeatable reads and phantom reads are prevented.
     * This level includes the prohibitions in
     * <code>TRANSACTION_REPEATABLE_READ</code> and further prohibits the
     * situation where one transaction reads all rows that satisfy
     * a <code>WHERE</code> condition, a second transaction inserts a row that
     * satisfies that <code>WHERE</code> condition, and the first transaction
     * rereads for the same condition, retrieving the additional
     * "phantom" row in the second read.
     */
    int TRANSACTION_SERIALIZABLE     = 8;

    /**
     * Attempts to change the transaction isolation level for this
     * <code>Connection</code> object to the one given.
     * The constants defined in the interface <code>Connection</code>
     * are the possible transaction isolation levels.
     * <P>
     * <B>Note:</B> If this method is called during a transaction, the result
     * is implementation-defined.
     *
     * @param level one of the following <code>Connection</code> constants:
     *        <code>Connection.TRANSACTION_READ_UNCOMMITTED</code>,
     *        <code>Connection.TRANSACTION_READ_COMMITTED</code>,
     *        <code>Connection.TRANSACTION_REPEATABLE_READ</code>, or
     *        <code>Connection.TRANSACTION_SERIALIZABLE</code>.
     *        (Note that <code>Connection.TRANSACTION_NONE</code> cannot be used
     *        because it specifies that transactions are not supported.)
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *            or the given parameter is not one of the <code>Connection</code>
     *            constants
     * @see DatabaseMetaData#supportsTransactionIsolationLevel
     * @see #getTransactionIsolation
     */
    void setTransactionIsolation(int level) throws SQLException;

    /**
     * Retrieves this <code>Connection</code> object's current
     * transaction isolation level.
     *
     * @return the current transaction isolation level, which will be one
     *         of the following constants:
     *        <code>Connection.TRANSACTION_READ_UNCOMMITTED</code>,
     *        <code>Connection.TRANSACTION_READ_COMMITTED</code>,
     *        <code>Connection.TRANSACTION_REPEATABLE_READ</code>,
     *        <code>Connection.TRANSACTION_SERIALIZABLE</code>, or
     *        <code>Connection.TRANSACTION_NONE</code>.
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @see #setTransactionIsolation
     */
    int getTransactionIsolation() throws SQLException;

    /**
     * Retrieves the first warning reported by calls on this
     * <code>Connection</code> object.  If there is more than one
     * warning, subsequent warnings will be chained to the first one
     * and can be retrieved by calling the method
     * <code>SQLWarning.getNextWarning</code> on the warning
     * that was retrieved previously.
     * <P>
     * This method may not be
     * called on a closed connection; doing so will cause an
     * <code>SQLException</code> to be thrown.
     *
     * <P><B>Note:</B> Subsequent warnings will be chained to this
     * SQLWarning.
     *
     * @return the first <code>SQLWarning</code> object or <code>null</code>
     *         if there are none
     * @exception SQLException if a database access error occurs or
     *            this method is called on a closed connection
     * @see SQLWarning
     */
    SQLWarning getWarnings() throws SQLException;

    /**
     * Clears all warnings reported for this <code>Connection</code> object.
     * After a call to this method, the method <code>getWarnings</code>
     * returns <code>null</code> until a new warning is
     * reported for this <code>Connection</code> object.
     *
     * @exception SQLException SQLException if a database access error occurs
     * or this method is called on a closed connection
     */
    void clearWarnings() throws SQLException;


    //--------------------------JDBC 2.0-----------------------------

    /**
     * Creates a <code>Statement</code> object that will generate
     * <code>ResultSet</code> objects with the given type and concurrency.
     * This method is the same as the <code>createStatement</code> method
     * above, but it allows the default result set
     * type and concurrency to be overridden.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param resultSetType a result set type; one of
     *        <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *        <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *        <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency a concurrency type; one of
     *        <code>ResultSet.CONCUR_READ_ONLY</code> or
     *        <code>ResultSet.CONCUR_UPDATABLE</code>
     * @return a new <code>Statement</code> object that will generate
     *         <code>ResultSet</code> objects with the given type and
     *         concurrency
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *         or the given parameters are not <code>ResultSet</code>
     *         constants indicating type and concurrency
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type and result set concurrency.
     * @since 1.2
     */
    Statement createStatement(int resultSetType, int resultSetConcurrency)
        throws SQLException;

    /**
     *
     * Creates a <code>PreparedStatement</code> object that will generate
     * <code>ResultSet</code> objects with the given type and concurrency.
     * This method is the same as the <code>prepareStatement</code> method
     * above, but it allows the default result set
     * type and concurrency to be overridden.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql a <code>String</code> object that is the SQL statement to
     *            be sent to the database; may contain one or more '?' IN
     *            parameters
     * @param resultSetType a result set type; one of
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency a concurrency type; one of
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     * @return a new PreparedStatement object containing the
     * pre-compiled SQL statement that will produce <code>ResultSet</code>
     * objects with the given type and concurrency
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *         or the given parameters are not <code>ResultSet</code>
     *         constants indicating type and concurrency
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type and result set concurrency.
     * @since 1.2
     */
    PreparedStatement prepareStatement(String sql, int resultSetType,
                                       int resultSetConcurrency)
        throws SQLException;

    /**
     * Creates a <code>CallableStatement</code> object that will generate
     * <code>ResultSet</code> objects with the given type and concurrency.
     * This method is the same as the <code>prepareCall</code> method
     * above, but it allows the default result set
     * type and concurrency to be overridden.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql a <code>String</code> object that is the SQL statement to
     *            be sent to the database; may contain on or more '?' parameters
     * @param resultSetType a result set type; one of
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency a concurrency type; one of
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     * @return a new <code>CallableStatement</code> object containing the
     * pre-compiled SQL statement that will produce <code>ResultSet</code>
     * objects with the given type and concurrency
     * @exception SQLException if a database access error occurs, this method
     * is called on a closed connection
     *         or the given parameters are not <code>ResultSet</code>
     *         constants indicating type and concurrency
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type and result set concurrency.
     * @since 1.2
     */
    CallableStatement prepareCall(String sql, int resultSetType,
                                  int resultSetConcurrency) throws SQLException;

    /**
     * Retrieves the <code>Map</code> object associated with this
     * <code>Connection</code> object.
     * Unless the application has added an entry, the type map returned
     * will be empty.
     *
     * @return the <code>java.util.Map</code> object associated
     *         with this <code>Connection</code> object
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.2
     * @see #setTypeMap
     */
    java.util.Map<String,Class<?>> getTypeMap() throws SQLException;

    /**
     * Installs the given <code>TypeMap</code> object as the type map for
     * this <code>Connection</code> object.  The type map will be used for the
     * custom mapping of SQL structured types and distinct types.
     *
     * @param map the <code>java.util.Map</code> object to install
     *        as the replacement for this <code>Connection</code>
     *        object's default type map
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection or
     *        the given parameter is not a <code>java.util.Map</code>
     *        object
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.2
     * @see #getTypeMap
     */
    void setTypeMap(java.util.Map<String,Class<?>> map) throws SQLException;

    //--------------------------JDBC 3.0-----------------------------


    /**
     * Changes the default holdability of <code>ResultSet</code> objects
     * created using this <code>Connection</code> object to the given
     * holdability.  The default holdability of <code>ResultSet</code> objects
     * can be be determined by invoking
     * {@link DatabaseMetaData#getResultSetHoldability}.
     *
     * @param holdability a <code>ResultSet</code> holdability constant; one of
     *        <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
     *        <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @throws SQLException if a database access occurs, this method is called
     * on a closed connection, or the given parameter
     *         is not a <code>ResultSet</code> constant indicating holdability
     * @exception SQLFeatureNotSupportedException if the given holdability is not supported
     * @see #getHoldability
     * @see DatabaseMetaData#getResultSetHoldability
     * @see ResultSet
     * @since 1.4
     */
    void setHoldability(int holdability) throws SQLException;

    /**
     * Retrieves the current holdability of <code>ResultSet</code> objects
     * created using this <code>Connection</code> object.
     *
     * @return the holdability, one of
     *        <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
     *        <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @throws SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @see #setHoldability
     * @see DatabaseMetaData#getResultSetHoldability
     * @see ResultSet
     * @since 1.4
     */
    int getHoldability() throws SQLException;

    /**
     * Creates an unnamed savepoint in the current transaction and
     * returns the new <code>Savepoint</code> object that represents it.
     *
     *<p> if setSavepoint is invoked outside of an active transaction, a transaction will be started at this newly created
     *savepoint.
     *
     * @return the new <code>Savepoint</code> object
     * @exception SQLException if a database access error occurs,
     * this method is called while participating in a distributed transaction,
     * this method is called on a closed connection
     *            or this <code>Connection</code> object is currently in
     *            auto-commit mode
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @see Savepoint
     * @since 1.4
     */
    Savepoint setSavepoint() throws SQLException;

    /**
     * Creates a savepoint with the given name in the current transaction
     * and returns the new <code>Savepoint</code> object that represents it.
     *
     * <p> if setSavepoint is invoked outside of an active transaction, a transaction will be started at this newly created
     *savepoint.
     *
     * @param name a <code>String</code> containing the name of the savepoint
     * @return the new <code>Savepoint</code> object
     * @exception SQLException if a database access error occurs,
          * this method is called while participating in a distributed transaction,
     * this method is called on a closed connection
     *            or this <code>Connection</code> object is currently in
     *            auto-commit mode
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @see Savepoint
     * @since 1.4
     */
    Savepoint setSavepoint(String name) throws SQLException;

    /**
     * Undoes all changes made after the given <code>Savepoint</code> object
     * was set.
     * <P>
     * This method should be used only when auto-commit has been disabled.
     *
     * @param savepoint the <code>Savepoint</code> object to roll back to
     * @exception SQLException if a database access error occurs,
     * this method is called while participating in a distributed transaction,
     * this method is called on a closed connection,
     *            the <code>Savepoint</code> object is no longer valid,
     *            or this <code>Connection</code> object is currently in
     *            auto-commit mode
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @see Savepoint
     * @see #rollback
     * @since 1.4
     */
    void rollback(Savepoint savepoint) throws SQLException;

    /**
     * Removes the specified <code>Savepoint</code>  and subsequent <code>Savepoint</code> objects from the current
     * transaction. Any reference to the savepoint after it have been removed
     * will cause an <code>SQLException</code> to be thrown.
     *
     * @param savepoint the <code>Savepoint</code> object to be removed
     * @exception SQLException if a database access error occurs, this
     *  method is called on a closed connection or
     *            the given <code>Savepoint</code> object is not a valid
     *            savepoint in the current transaction
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.4
     */
    void releaseSavepoint(Savepoint savepoint) throws SQLException;

    /**
     * Creates a <code>Statement</code> object that will generate
     * <code>ResultSet</code> objects with the given type, concurrency,
     * and holdability.
     * This method is the same as the <code>createStatement</code> method
     * above, but it allows the default result set
     * type, concurrency, and holdability to be overridden.
     *
     * @param resultSetType one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     * @param resultSetHoldability one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
     *         <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @return a new <code>Statement</code> object that will generate
     *         <code>ResultSet</code> objects with the given type,
     *         concurrency, and holdability
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *            or the given parameters are not <code>ResultSet</code>
     *            constants indicating type, concurrency, and holdability
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type, result set holdability and result set concurrency.
     * @see ResultSet
     * @since 1.4
     */
    Statement createStatement(int resultSetType, int resultSetConcurrency,
                              int resultSetHoldability) throws SQLException;

    /**
     * Creates a <code>PreparedStatement</code> object that will generate
     * <code>ResultSet</code> objects with the given type, concurrency,
     * and holdability.
     * <P>
     * This method is the same as the <code>prepareStatement</code> method
     * above, but it allows the default result set
     * type, concurrency, and holdability to be overridden.
     *
     * @param sql a <code>String</code> object that is the SQL statement to
     *            be sent to the database; may contain one or more '?' IN
     *            parameters
     * @param resultSetType one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     * @param resultSetHoldability one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
     *         <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @return a new <code>PreparedStatement</code> object, containing the
     *         pre-compiled SQL statement, that will generate
     *         <code>ResultSet</code> objects with the given type,
     *         concurrency, and holdability
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *            or the given parameters are not <code>ResultSet</code>
     *            constants indicating type, concurrency, and holdability
      * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type, result set holdability and result set concurrency.
     * @see ResultSet
     * @since 1.4
     */
    PreparedStatement prepareStatement(String sql, int resultSetType,
                                       int resultSetConcurrency, int resultSetHoldability)
        throws SQLException;

    /**
     * Creates a <code>CallableStatement</code> object that will generate
     * <code>ResultSet</code> objects with the given type and concurrency.
     * This method is the same as the <code>prepareCall</code> method
     * above, but it allows the default result set
     * type, result set concurrency type and holdability to be overridden.
     *
     * @param sql a <code>String</code> object that is the SQL statement to
     *            be sent to the database; may contain on or more '?' parameters
     * @param resultSetType one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     * @param resultSetHoldability one of the following <code>ResultSet</code>
     *        constants:
     *         <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
     *         <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @return a new <code>CallableStatement</code> object, containing the
     *         pre-compiled SQL statement, that will generate
     *         <code>ResultSet</code> objects with the given type,
     *         concurrency, and holdability
     * @exception SQLException if a database access error occurs, this
     * method is called on a closed connection
     *            or the given parameters are not <code>ResultSet</code>
     *            constants indicating type, concurrency, and holdability
      * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method or this method is not supported for the specified result
     * set type, result set holdability and result set concurrency.
     * @see ResultSet
     * @since 1.4
     */
    CallableStatement prepareCall(String sql, int resultSetType,
                                  int resultSetConcurrency,
                                  int resultSetHoldability) throws SQLException;


    /**
     * Creates a default <code>PreparedStatement</code> object that has
     * the capability to retrieve auto-generated keys. The given constant
     * tells the driver whether it should make auto-generated keys
     * available for retrieval.  This parameter is ignored if the SQL statement
     * is not an <code>INSERT</code> statement, or an SQL statement able to return
     * auto-generated keys (the list of such statements is vendor-specific).
     * <P>
     * <B>Note:</B> This method is optimized for handling
     * parametric SQL statements that benefit from precompilation. If
     * the driver supports precompilation,
     * the method <code>prepareStatement</code> will send
     * the statement to the database for precompilation. Some drivers
     * may not support precompilation. In this case, the statement may
     * not be sent to the database until the <code>PreparedStatement</code>
     * object is executed.  This has no direct effect on users; however, it does
     * affect which methods throw certain SQLExceptions.
     * <P>
     * Result sets created using the returned <code>PreparedStatement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql an SQL statement that may contain one or more '?' IN
     *        parameter placeholders
     * @param autoGeneratedKeys a flag indicating whether auto-generated keys
     *        should be returned; one of
     *        <code>Statement.RETURN_GENERATED_KEYS</code> or
     *        <code>Statement.NO_GENERATED_KEYS</code>
     * @return a new <code>PreparedStatement</code> object, containing the
     *         pre-compiled SQL statement, that will have the capability of
     *         returning auto-generated keys
     * @exception SQLException if a database access error occurs, this
     *  method is called on a closed connection
     *         or the given parameter is not a <code>Statement</code>
     *         constant indicating whether auto-generated keys should be
     *         returned
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method with a constant of Statement.RETURN_GENERATED_KEYS
     * @since 1.4
     */
    PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
        throws SQLException;

    /**
     * Creates a default <code>PreparedStatement</code> object capable
     * of returning the auto-generated keys designated by the given array.
     * This array contains the indexes of the columns in the target
     * table that contain the auto-generated keys that should be made
     * available.  The driver will ignore the array if the SQL statement
     * is not an <code>INSERT</code> statement, or an SQL statement able to return
     * auto-generated keys (the list of such statements is vendor-specific).
     *<p>
     * An SQL statement with or without IN parameters can be
     * pre-compiled and stored in a <code>PreparedStatement</code> object. This
     * object can then be used to efficiently execute this statement
     * multiple times.
     * <P>
     * <B>Note:</B> This method is optimized for handling
     * parametric SQL statements that benefit from precompilation. If
     * the driver supports precompilation,
     * the method <code>prepareStatement</code> will send
     * the statement to the database for precompilation. Some drivers
     * may not support precompilation. In this case, the statement may
     * not be sent to the database until the <code>PreparedStatement</code>
     * object is executed.  This has no direct effect on users; however, it does
     * affect which methods throw certain SQLExceptions.
     * <P>
     * Result sets created using the returned <code>PreparedStatement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql an SQL statement that may contain one or more '?' IN
     *        parameter placeholders
     * @param columnIndexes an array of column indexes indicating the columns
     *        that should be returned from the inserted row or rows
     * @return a new <code>PreparedStatement</code> object, containing the
     *         pre-compiled statement, that is capable of returning the
     *         auto-generated keys designated by the given array of column
     *         indexes
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     *
     * @since 1.4
     */
    PreparedStatement prepareStatement(String sql, int columnIndexes[])
        throws SQLException;

    /**
     * Creates a default <code>PreparedStatement</code> object capable
     * of returning the auto-generated keys designated by the given array.
     * This array contains the names of the columns in the target
     * table that contain the auto-generated keys that should be returned.
     * The driver will ignore the array if the SQL statement
     * is not an <code>INSERT</code> statement, or an SQL statement able to return
     * auto-generated keys (the list of such statements is vendor-specific).
     * <P>
     * An SQL statement with or without IN parameters can be
     * pre-compiled and stored in a <code>PreparedStatement</code> object. This
     * object can then be used to efficiently execute this statement
     * multiple times.
     * <P>
     * <B>Note:</B> This method is optimized for handling
     * parametric SQL statements that benefit from precompilation. If
     * the driver supports precompilation,
     * the method <code>prepareStatement</code> will send
     * the statement to the database for precompilation. Some drivers
     * may not support precompilation. In this case, the statement may
     * not be sent to the database until the <code>PreparedStatement</code>
     * object is executed.  This has no direct effect on users; however, it does
     * affect which methods throw certain SQLExceptions.
     * <P>
     * Result sets created using the returned <code>PreparedStatement</code>
     * object will by default be type <code>TYPE_FORWARD_ONLY</code>
     * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
     * The holdability of the created result sets can be determined by
     * calling {@link #getHoldability}.
     *
     * @param sql an SQL statement that may contain one or more '?' IN
     *        parameter placeholders
     * @param columnNames an array of column names indicating the columns
     *        that should be returned from the inserted row or rows
     * @return a new <code>PreparedStatement</code> object, containing the
     *         pre-compiled statement, that is capable of returning the
     *         auto-generated keys designated by the given array of column
     *         names
     * @exception SQLException if a database access error occurs
     * or this method is called on a closed connection
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     *
     * @since 1.4
     */
    PreparedStatement prepareStatement(String sql, String columnNames[])
        throws SQLException;

    /**
     * Constructs an object that implements the <code>Clob</code> interface. The object
     * returned initially contains no data.  The <code>setAsciiStream</code>,
     * <code>setCharacterStream</code> and <code>setString</code> methods of
     * the <code>Clob</code> interface may be used to add data to the <code>Clob</code>.
     * @return An object that implements the <code>Clob</code> interface
     * @throws SQLException if an object that implements the
     * <code>Clob</code> interface can not be constructed, this method is
     * called on a closed connection or a database access error occurs.
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this data type
     *
     * @since 1.6
     */
    Clob createClob() throws SQLException;

    /**
     * Constructs an object that implements the <code>Blob</code> interface. The object
     * returned initially contains no data.  The <code>setBinaryStream</code> and
     * <code>setBytes</code> methods of the <code>Blob</code> interface may be used to add data to
     * the <code>Blob</code>.
     * @return  An object that implements the <code>Blob</code> interface
     * @throws SQLException if an object that implements the
     * <code>Blob</code> interface can not be constructed, this method is
     * called on a closed connection or a database access error occurs.
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this data type
     *
     * @since 1.6
     */
    Blob createBlob() throws SQLException;

    /**
     * Constructs an object that implements the <code>NClob</code> interface. The object
     * returned initially contains no data.  The <code>setAsciiStream</code>,
     * <code>setCharacterStream</code> and <code>setString</code> methods of the <code>NClob</code> interface may
     * be used to add data to the <code>NClob</code>.
     * @return An object that implements the <code>NClob</code> interface
     * @throws SQLException if an object that implements the
     * <code>NClob</code> interface can not be constructed, this method is
     * called on a closed connection or a database access error occurs.
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this data type
     *
     * @since 1.6
     */
    NClob createNClob() throws SQLException;

    /**
     * Constructs an object that implements the <code>SQLXML</code> interface. The object
     * returned initially contains no data. The <code>createXmlStreamWriter</code> object and
     * <code>setString</code> method of the <code>SQLXML</code> interface may be used to add data to the <code>SQLXML</code>
     * object.
     * @return An object that implements the <code>SQLXML</code> interface
     * @throws SQLException if an object that implements the <code>SQLXML</code> interface can not
     * be constructed, this method is
     * called on a closed connection or a database access error occurs.
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this data type
     * @since 1.6
     */
    SQLXML createSQLXML() throws SQLException;

        /**
         * Returns true if the connection has not been closed and is still valid.
         * The driver shall submit a query on the connection or use some other
         * mechanism that positively verifies the connection is still valid when
         * this method is called.
         * <p>
         * The query submitted by the driver to validate the connection shall be
         * executed in the context of the current transaction.
         *
         * @param timeout -             The time in seconds to wait for the database operation
         *                                              used to validate the connection to complete.  If
         *                                              the timeout period expires before the operation
         *                                              completes, this method returns false.  A value of
         *                                              0 indicates a timeout is not applied to the
         *                                              database operation.
         * <p>
         * @return true if the connection is valid, false otherwise
         * @exception SQLException if the value supplied for <code>timeout</code>
         * is less then 0
         * @since 1.6
         * <p>
         * @see java.sql.DatabaseMetaData#getClientInfoProperties
         */
         boolean isValid(int timeout) throws SQLException;

        /**
         * Sets the value of the client info property specified by name to the
         * value specified by value.
         * <p>
         * Applications may use the <code>DatabaseMetaData.getClientInfoProperties</code>
         * method to determine the client info properties supported by the driver
         * and the maximum length that may be specified for each property.
         * <p>
         * The driver stores the value specified in a suitable location in the
         * database.  For example in a special register, session parameter, or
         * system table column.  For efficiency the driver may defer setting the
         * value in the database until the next time a statement is executed or
         * prepared.  Other than storing the client information in the appropriate
         * place in the database, these methods shall not alter the behavior of
         * the connection in anyway.  The values supplied to these methods are
         * used for accounting, diagnostics and debugging purposes only.
         * <p>
         * The driver shall generate a warning if the client info name specified
         * is not recognized by the driver.
         * <p>
         * If the value specified to this method is greater than the maximum
         * length for the property the driver may either truncate the value and
         * generate a warning or generate a <code>SQLClientInfoException</code>.  If the driver
         * generates a <code>SQLClientInfoException</code>, the value specified was not set on the
         * connection.
         * <p>
         * The following are standard client info properties.  Drivers are not
         * required to support these properties however if the driver supports a
         * client info property that can be described by one of the standard
         * properties, the standard property name should be used.
         * <p>
         * <ul>
         * <li>ApplicationName  -       The name of the application currently utilizing
         *                                                      the connection</li>
         * <li>ClientUser               -       The name of the user that the application using
         *                                                      the connection is performing work for.  This may
         *                                                      not be the same as the user name that was used
         *                                                      in establishing the connection.</li>
         * <li>ClientHostname   -       The hostname of the computer the application
         *                                                      using the connection is running on.</li>
         * </ul>
         * <p>
         * @param name          The name of the client info property to set
         * @param value         The value to set the client info property to.  If the
         *                                      value is null, the current value of the specified
         *                                      property is cleared.
         * <p>
         * @throws      SQLClientInfoException if the database server returns an error while
         *                      setting the client info value on the database server or this method
         * is called on a closed connection
         * <p>
         * @since 1.6
         */
         void setClientInfo(String name, String value)
                throws SQLClientInfoException;

        /**
     * Sets the value of the connection's client info properties.  The
     * <code>Properties</code> object contains the names and values of the client info
     * properties to be set.  The set of client info properties contained in
     * the properties list replaces the current set of client info properties
     * on the connection.  If a property that is currently set on the
     * connection is not present in the properties list, that property is
     * cleared.  Specifying an empty properties list will clear all of the
     * properties on the connection.  See <code>setClientInfo (String, String)</code> for
     * more information.
     * <p>
     * If an error occurs in setting any of the client info properties, a
     * <code>SQLClientInfoException</code> is thrown. The <code>SQLClientInfoException</code>
     * contains information indicating which client info properties were not set.
     * The state of the client information is unknown because
     * some databases do not allow multiple client info properties to be set
     * atomically.  For those databases, one or more properties may have been
     * set before the error occurred.
     * <p>
     *
     * @param properties                the list of client info properties to set
     * <p>
     * @see java.sql.Connection#setClientInfo(String, String) setClientInfo(String, String)
     * @since 1.6
     * <p>
     * @throws SQLClientInfoException if the database server returns an error while
     *                  setting the clientInfo values on the database server or this method
     * is called on a closed connection
     * <p>
     */
         void setClientInfo(Properties properties)
                throws SQLClientInfoException;

        /**
         * Returns the value of the client info property specified by name.  This
         * method may return null if the specified client info property has not
         * been set and does not have a default value.  This method will also
         * return null if the specified client info property name is not supported
         * by the driver.
         * <p>
         * Applications may use the <code>DatabaseMetaData.getClientInfoProperties</code>
         * method to determine the client info properties supported by the driver.
         * <p>
         * @param name          The name of the client info property to retrieve
         * <p>
         * @return                      The value of the client info property specified
         * <p>
         * @throws SQLException         if the database server returns an error when
         *                                                      fetching the client info value from the database
         *or this method is called on a closed connection
         * <p>
         * @since 1.6
         * <p>
         * @see java.sql.DatabaseMetaData#getClientInfoProperties
         */
         String getClientInfo(String name)
                throws SQLException;

        /**
         * Returns a list containing the name and current value of each client info
         * property supported by the driver.  The value of a client info property
         * may be null if the property has not been set and does not have a
         * default value.
         * <p>
         * @return      A <code>Properties</code> object that contains the name and current value of
         *                      each of the client info properties supported by the driver.
         * <p>
         * @throws      SQLException if the database server returns an error when
         *                      fetching the client info values from the database
         * or this method is called on a closed connection
         * <p>
         * @since 1.6
         */
         Properties getClientInfo()
                throws SQLException;

/**
  * Factory method for creating Array objects.
  *<p>
  * <b>Note: </b>When <code>createArrayOf</code> is used to create an array object
  * that maps to a primitive data type, then it is implementation-defined
  * whether the <code>Array</code> object is an array of that primitive
  * data type or an array of <code>Object</code>.
  * <p>
  * <b>Note: </b>The JDBC driver is responsible for mapping the elements
  * <code>Object</code> array to the default JDBC SQL type defined in
  * java.sql.Types for the given class of <code>Object</code>. The default
  * mapping is specified in Appendix B of the JDBC specification.  If the
  * resulting JDBC type is not the appropriate type for the given typeName then
  * it is implementation defined whether an <code>SQLException</code> is
  * thrown or the driver supports the resulting conversion.
  *
  * @param typeName the SQL name of the type the elements of the array map to. The typeName is a
  * database-specific name which may be the name of a built-in type, a user-defined type or a standard  SQL type supported by this database. This
  *  is the value returned by <code>Array.getBaseTypeName</code>
  * @param elements the elements that populate the returned object
  * @return an Array object whose elements map to the specified SQL type
  * @throws SQLException if a database error occurs, the JDBC type is not
  *  appropriate for the typeName and the conversion is not supported, the typeName is null or this method is called on a closed connection
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this data type
  * @since 1.6
  */
 Array createArrayOf(String typeName, Object[] elements) throws
SQLException;

/**
  * Factory method for creating Struct objects.
  *
  * @param typeName the SQL type name of the SQL structured type that this <code>Struct</code>
  * object maps to. The typeName is the name of  a user-defined type that
  * has been defined for this database. It is the value returned by
  * <code>Struct.getSQLTypeName</code>.

  * @param attributes the attributes that populate the returned object
  *  @return a Struct object that maps to the given SQL type and is populated with the given attributes
  * @throws SQLException if a database error occurs, the typeName is null or this method is called on a closed connection
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this data type
  * @since 1.6
  */
 Struct createStruct(String typeName, Object[] attributes)
throws SQLException;
}
