/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.rowset;

import java.sql.*;
import javax.sql.*;
import javax.naming.*;
import java.io.*;
import java.math.*;
import java.util.*;
import java.beans.*;

import javax.sql.rowset.*;

/**
 * The standard implementation of the <code>JdbcRowSet</code> interface. See the interface
 * defintion for full behavior and implementation requirements.
 *
 * @author Jonathan Bruce, Amit Handa
 */

public class JdbcRowSetImpl extends BaseRowSet implements JdbcRowSet, Joinable {

    /**
     * The <code>Connection</code> object that is this rowset's
     * current connection to the database.  This field is set
     * internally when the connection is established.
     */
    private Connection conn;

    /**
     * The <code>PreparedStatement</code> object that is this rowset's
     * current command.  This field is set internally when the method
     * <code>execute</code> creates the <code>PreparedStatement</code>
     * object.
     */
    private PreparedStatement ps;

    /**
     * The <code>ResultSet</code> object that is this rowset's
     * current result set.  This field is set internally when the method
     * <code>execute</code> executes the rowset's command and thereby
     * creates the rowset's <code>ResultSet</code> object.
     */
    private ResultSet rs;

    /**
     * The <code>RowSetMetaDataImpl</code> object that is contructed when
     * a <code>ResultSet</code> object is passed to the <code>JdbcRowSet</code>
     * constructor. This helps in constructing all metadata associated
     * with the <code>ResultSet</code> object using the setter methods of
     * <code>RowSetMetaDataImpl</code>.
     */
    private RowSetMetaDataImpl rowsMD;

    /**
     * The <code>ResultSetMetaData</code> object from which this
     * <code>RowSetMetaDataImpl</code> is formed and which  helps in getting
     * the metadata information.
     */
    private ResultSetMetaData resMD;

    /**
     * The property that helps to fire the property changed event when certain
     * properties are changed in the <code>JdbcRowSet</code> object. This property
     * is being added to satisfy Rave requirements.
     */
    private PropertyChangeSupport propertyChangeSupport;

    /**
     * The Vector holding the Match Columns
     */
    private Vector iMatchColumns;

    /**
     * The Vector that will hold the Match Column names.
     */
    private Vector strMatchColumns;


    protected transient JdbcRowSetResourceBundle resBundle;

    /**
     * Constructs a default <code>JdbcRowSet</code> object.
     * The new instance of <code>JdbcRowSet</code> will serve as a proxy
     * for the <code>ResultSet</code> object it creates, and by so doing,
     * it will make it possible to use the result set as a JavaBeans
     * component.
     * <P>
     * The following is true of a default <code>JdbcRowSet</code> instance:
     * <UL>
     *   <LI>Does not show deleted rows
     *   <LI>Has no time limit for how long a driver may take to
     *       execute the rowset's command
     *   <LI>Has no limit for the number of rows it may contain
     *   <LI>Has no limit for the number of bytes a column may contain
     *   <LI>Has a scrollable cursor and does not show changes
     *       made by others
     *   <LI>Will not see uncommitted data (make "dirty" reads)
     *   <LI>Has escape processing turned on
     *   <LI>Has its connection's type map set to <code>null</code>
     *   <LI>Has an empty <code>Hashtable</code> object for storing any
     *       parameters that are set
     * </UL>
     * A newly created <code>JdbcRowSet</code> object must have its
     * <code>execute</code> method invoked before other public methods
     * are called on it; otherwise, such method calls will cause an
     * exception to be thrown.
     *
     * @throws SQLException [1] if any of its public methods are called prior
     * to calling the <code>execute</code> method; [2] if invalid JDBC driver
     * properties are set or [3] if no connection to a data source exists.
     */
    public JdbcRowSetImpl() {
        conn = null;
        ps   = null;
        rs   = null;

        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }

        propertyChangeSupport = new PropertyChangeSupport(this);

        initParams();

        // set the defaults

        try {
            setShowDeleted(false);
        } catch(SQLException sqle) {
             System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setshowdeleted").toString() +
                                sqle.getLocalizedMessage());
        }

        try {
            setQueryTimeout(0);
        } catch(SQLException sqle) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setquerytimeout").toString() +
                                sqle.getLocalizedMessage());
        }

        try {
            setMaxRows(0);
        } catch(SQLException sqle) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setmaxrows").toString() +
                                sqle.getLocalizedMessage());
        }

        try {
            setMaxFieldSize(0);
        } catch(SQLException sqle) {
             System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setmaxfieldsize").toString() +
                                sqle.getLocalizedMessage());
        }

        try {
            setEscapeProcessing(true);
        } catch(SQLException sqle) {
             System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setescapeprocessing").toString() +
                                sqle.getLocalizedMessage());
        }

        try {
            setConcurrency(ResultSet.CONCUR_UPDATABLE);
        } catch (SQLException sqle) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setconcurrency").toString() +
                                sqle.getLocalizedMessage());
        }

        setTypeMap(null);

        try {
            setType(ResultSet.TYPE_SCROLL_INSENSITIVE);
        } catch(SQLException sqle){
          System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.settype").toString() +
                                sqle.getLocalizedMessage());
        }

        setReadOnly(true);

        try {
            setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        } catch(SQLException sqle){
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.settransactionisolation").toString() +
                                sqle.getLocalizedMessage());
        }

        //Instantiating the vector for MatchColumns

        iMatchColumns = new Vector(10);
        for(int i = 0; i < 10 ; i++) {
           iMatchColumns.add(i,new Integer(-1));
        }

        strMatchColumns = new Vector(10);
        for(int j = 0; j < 10; j++) {
           strMatchColumns.add(j,null);
        }
    }

    /**
     * Constructs a default <code>JdbcRowSet</code> object given a
     * valid <code>Connection</code> object. The new
     * instance of <code>JdbcRowSet</code> will serve as a proxy for
     * the <code>ResultSet</code> object it creates, and by so doing,
     * it will make it possible to use the result set as a JavaBeans
     * component.
     * <P>
     * The following is true of a default <code>JdbcRowSet</code> instance:
     * <UL>
     *   <LI>Does not show deleted rows
     *   <LI>Has no time limit for how long a driver may take to
     *       execute the rowset's command
     *   <LI>Has no limit for the number of rows it may contain
     *   <LI>Has no limit for the number of bytes a column may contain
     *   <LI>Has a scrollable cursor and does not show changes
     *       made by others
     *   <LI>Will not see uncommitted data (make "dirty" reads)
     *   <LI>Has escape processing turned on
     *   <LI>Has its connection's type map set to <code>null</code>
     *   <LI>Has an empty <code>Hashtable</code> object for storing any
     *       parameters that are set
     * </UL>
     * A newly created <code>JdbcRowSet</code> object must have its
     * <code>execute</code> method invoked before other public methods
     * are called on it; otherwise, such method calls will cause an
     * exception to be thrown.
     *
     * @throws SQLException [1] if any of its public methods are called prior
     * to calling the <code>execute</code> method, [2] if invalid JDBC driver
     * properties are set, or [3] if no connection to a data source exists.
     */
    public JdbcRowSetImpl(Connection con) throws SQLException {

        conn = con;
        ps = null;
        rs = null;

        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }

        propertyChangeSupport = new PropertyChangeSupport(this);

        initParams();
        // set the defaults
        setShowDeleted(false);
        setQueryTimeout(0);
        setMaxRows(0);
        setMaxFieldSize(0);

        setParams();

        setReadOnly(true);
        setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        setEscapeProcessing(true);
        setTypeMap(null);

        //Instantiating the vector for MatchColumns

        iMatchColumns = new Vector(10);
        for(int i = 0; i < 10 ; i++) {
           iMatchColumns.add(i,new Integer(-1));
        }

        strMatchColumns = new Vector(10);
        for(int j = 0; j < 10; j++) {
           strMatchColumns.add(j,null);
        }
    }

    /**
     * Constructs a default <code>JdbcRowSet</code> object using the
     * URL, username, and password arguments supplied. The new
     * instance of <code>JdbcRowSet</code> will serve as a proxy for
     * the <code>ResultSet</code> object it creates, and by so doing,
     * it will make it possible to use the result set as a JavaBeans
     * component.
     *
     * <P>
     * The following is true of a default <code>JdbcRowSet</code> instance:
     * <UL>
     *   <LI>Does not show deleted rows
     *   <LI>Has no time limit for how long a driver may take to
     *       execute the rowset's command
     *   <LI>Has no limit for the number of rows it may contain
     *   <LI>Has no limit for the number of bytes a column may contain
     *   <LI>Has a scrollable cursor and does not show changes
     *       made by others
     *   <LI>Will not see uncommitted data (make "dirty" reads)
     *   <LI>Has escape processing turned on
     *   <LI>Has its connection's type map set to <code>null</code>
     *   <LI>Has an empty <code>Hashtable</code> object for storing any
     *       parameters that are set
     * </UL>
     *
     * @param url - a JDBC URL for the database to which this <code>JdbcRowSet</code>
     *        object will be connected. The form for a JDBC URL is
     *        <code>jdbc:subprotocol:subname</code>.
     * @param user - the database user on whose behalf the connection
     *        is being made
     * @param password - the user's password
     *
     * @throws SQLException if a database access error occurs
     *
     */
    public JdbcRowSetImpl(String url, String user, String password) throws SQLException {
        conn = null;
        ps = null;
        rs = null;

        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }

        propertyChangeSupport = new PropertyChangeSupport(this);

        initParams();

        // Pass the arguments to BaseRowSet
        // setter methods now.

        setUsername(user);
        setPassword(password);
        setUrl(url);

        // set the defaults
        setShowDeleted(false);
        setQueryTimeout(0);
        setMaxRows(0);
        setMaxFieldSize(0);

        // to ensure connection to a db call connect now
        // and associate a conn with "this" object
        // in this case.
        conn = connect();
        setParams();

        setReadOnly(true);
        setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        setEscapeProcessing(true);
        setTypeMap(null);

        //Instantiating the vector for MatchColumns

        iMatchColumns = new Vector(10);
        for(int i = 0; i < 10 ; i++) {
           iMatchColumns.add(i,new Integer(-1));
        }

        strMatchColumns = new Vector(10);
        for(int j = 0; j < 10; j++) {
           strMatchColumns.add(j,null);
        }
    }


    /**
     * Constructs a <code>JdbcRowSet</code> object using the given valid
     * <code>ResultSet</code> object. The new
     * instance of <code>JdbcRowSet</code> will serve as a proxy for
     * the <code>ResultSet</code> object, and by so doing,
     * it will make it possible to use the result set as a JavaBeans
     * component.
     *
     * <P>
     * The following is true of a default <code>JdbcRowSet</code> instance:
     * <UL>
     *   <LI>Does not show deleted rows
     *   <LI>Has no time limit for how long a driver may take to
     *       execute the rowset's command
     *   <LI>Has no limit for the number of rows it may contain
     *   <LI>Has no limit for the number of bytes a column may contain
     *   <LI>Has a scrollable cursor and does not show changes
     *       made by others
     *   <LI>Will not see uncommitted data (make "dirty" reads)
     *   <LI>Has escape processing turned on
     *   <LI>Has its connection's type map set to <code>null</code>
     *   <LI>Has an empty <code>Hashtable</code> object for storing any
     *       parameters that are set
     * </UL>
     *
     * @param res a valid <code>ResultSet</code> object
     *
     * @throws SQLException if a database access occurs due to a non
     * valid ResultSet handle.
     */
    public JdbcRowSetImpl(ResultSet res) throws SQLException {

        // A ResultSet handle encapsulates a connection handle.
        // But there is no way we can retrieve a Connection handle
        // from a ResultSet object.
        // So to avoid any anomalies we keep the conn = null
        // The passed rs handle will be a wrapper around for
        // "this" object's all operations.
        conn = null;

        ps = null;

        rs = res;

        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }

        propertyChangeSupport = new PropertyChangeSupport(this);

        initParams();

        // get the values from the resultset handle.
        setShowDeleted(false);
        setQueryTimeout(0);
        setMaxRows(0);
        setMaxFieldSize(0);

        setParams();

        setReadOnly(true);
        setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        setEscapeProcessing(true);
        setTypeMap(null);

        // Get a handle to ResultSetMetaData
        // Construct RowSetMetaData out of it.

        resMD = rs.getMetaData();

        rowsMD = new RowSetMetaDataImpl();

        initMetaData(rowsMD, resMD);

        //Instantiating the vector for MatchColumns

        iMatchColumns = new Vector(10);
        for(int i = 0; i < 10 ; i++) {
           iMatchColumns.add(i,new Integer(-1));
        }

        strMatchColumns = new Vector(10);
        for(int j = 0; j < 10; j++) {
           strMatchColumns.add(j,null);
        }
    }

    /**
     * Initializes the given <code>RowSetMetaData</code> object with the values
     * in the given <code>ResultSetMetaData</code> object.
     *
     * @param md the <code>RowSetMetaData</code> object for this
     *           <code>JdbcRowSetImpl</code> object, which will be set with
     *           values from rsmd
     * @param rsmd the <code>ResultSetMetaData</code> object from which new
     *             values for md will be read
     * @throws SQLException if an error occurs
     */
    protected void initMetaData(RowSetMetaData md, ResultSetMetaData rsmd) throws SQLException {
        int numCols = rsmd.getColumnCount();

        md.setColumnCount(numCols);
        for (int col=1; col <= numCols; col++) {
            md.setAutoIncrement(col, rsmd.isAutoIncrement(col));
            md.setCaseSensitive(col, rsmd.isCaseSensitive(col));
            md.setCurrency(col, rsmd.isCurrency(col));
            md.setNullable(col, rsmd.isNullable(col));
            md.setSigned(col, rsmd.isSigned(col));
            md.setSearchable(col, rsmd.isSearchable(col));
            md.setColumnDisplaySize(col, rsmd.getColumnDisplaySize(col));
            md.setColumnLabel(col, rsmd.getColumnLabel(col));
            md.setColumnName(col, rsmd.getColumnName(col));
            md.setSchemaName(col, rsmd.getSchemaName(col));
            md.setPrecision(col, rsmd.getPrecision(col));
            md.setScale(col, rsmd.getScale(col));
            md.setTableName(col, rsmd.getTableName(col));
            md.setCatalogName(col, rsmd.getCatalogName(col));
            md.setColumnType(col, rsmd.getColumnType(col));
            md.setColumnTypeName(col, rsmd.getColumnTypeName(col));
        }
    }


    protected void checkState() throws SQLException {

        // If all the three i.e.  conn, ps & rs are
        // simultaneously null implies we are not connected
        // to the db, implies undesirable state so throw exception

        if (conn == null && ps == null && rs == null ) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.invalstate").toString());
        }
    }

    //---------------------------------------------------------------------
    // Reading and writing data
    //---------------------------------------------------------------------

    /**
     * Creates the internal <code>ResultSet</code> object for which this
     * <code>JdbcRowSet</code> object is a wrapper, effectively
     * making the result set a JavaBeans component.
     * <P>
     * Certain properties must have been set before this method is called
     * so that it can establish a connection to a database and execute the
     * query that will create the result set.  If a <code>DataSource</code>
     * object will be used to create the connection, properties for the
     * data source name, user name, and password must be set.  If the
     * <code>DriverManager</code> will be used, the properties for the
     * URL, user name, and password must be set.  In either case, the
     * property for the command must be set.  If the command has placeholder
     * parameters, those must also be set. This method throws
     * an exception if the required properties are not set.
     * <P>
     * Other properties have default values that may optionally be set
     * to new values. The <code>execute</code> method will use the value
     * for the command property to create a <code>PreparedStatement</code>
     * object and set its properties (escape processing, maximum field
     * size, maximum number of rows, and query timeout limit) to be those
     * of this rowset.
     *
     * @throws SQLException if (1) a database access error occurs,
     * (2) any required JDBC properties are not set, or (3) if an
     * invalid connection exists.
     */
    public void execute() throws SQLException {
        /*
         * To execute based on the properties:
         * i) determine how to get a connection
         * ii) prepare the statement
         * iii) set the properties of the statement
         * iv) parse the params. and set them
         * v) execute the statement
         *
         * During all of this try to tolerate as many errors
         * as possible, many drivers will not support all of
         * the properties and will/should throw SQLException
         * at us...
         *
         */

        prepare();

        // set the properties of our shiny new statement
        setProperties(ps);


        // set the parameters
        decodeParams(getParams(), ps);


        // execute the statement
        rs = ps.executeQuery();


        // notify listeners
        notifyRowSetChanged();


    }

    protected void setProperties(PreparedStatement ps) throws SQLException {

        try {
            ps.setEscapeProcessing(getEscapeProcessing());
        } catch (SQLException ex) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setescapeprocessing").toString() +
                                ex.getLocalizedMessage());
        }

        try {
            ps.setMaxFieldSize(getMaxFieldSize());
        } catch (SQLException ex) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setmaxfieldsize").toString() +
                                ex.getLocalizedMessage());
        }

        try {
            ps.setMaxRows(getMaxRows());
        } catch (SQLException ex) {
           System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setmaxrows").toString() +
                                ex.getLocalizedMessage());
        }

        try {
            ps.setQueryTimeout(getQueryTimeout());
        } catch (SQLException ex) {
           System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.setquerytimeout").toString() +
                                ex.getLocalizedMessage());
        }

    }

    // An alternate solution is required instead of having the
    // connect method as protected.
    // This is a work around to assist Rave Team
    // :ah

    protected Connection connect() throws SQLException {

        // Get a JDBC connection.

        // First check for Connection handle object as such if
        // "this" initialized  using conn.

        if(conn != null) {
            return conn;

        } else if (getDataSourceName() != null) {

            // Connect using JNDI.
            try {
                Context ctx = new InitialContext();
                DataSource ds = (DataSource)ctx.lookup
                    (getDataSourceName());
                //return ds.getConnection(getUsername(),getPassword());

                if(getUsername() != null && !getUsername().equals("")) {
                     return ds.getConnection(getUsername(),getPassword());
                } else {
                     return ds.getConnection();
                }
            }
            catch (javax.naming.NamingException ex) {
                throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.connect").toString());
            }

        } else if (getUrl() != null) {
            // Check only for getUrl() != null because
            // user, passwd can be null
            // Connect using the driver manager.

            return DriverManager.getConnection
                    (getUrl(), getUsername(), getPassword());
        }
        else {
            return null;
        }

    }


    protected PreparedStatement prepare() throws SQLException {
        // get a connection
        conn = connect();

        try {

            Map aMap = getTypeMap();
            if( aMap != null) {
                conn.setTypeMap(aMap);
            }
            ps = conn.prepareStatement(getCommand(),ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
        } catch (SQLException ex) {
            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.prepare").toString() +
                                ex.getLocalizedMessage());

            if (ps != null)
                ps.close();
            if (conn != null)
                conn.close();

            throw new SQLException(ex.getMessage());
        }

        return ps;
    }

    private void decodeParams(Object[] params, PreparedStatement ps)
    throws SQLException {

    // There is a corresponding decodeParams in JdbcRowSetImpl
    // which does the same as this method. This is a design flaw.
    // Update the CachedRowsetReader.decodeParams when you update
    // this method.

    // Adding the same comments to CachedRowsetReader.decodeParams.

        int arraySize;
        Object[] param = null;

        for (int i=0; i < params.length; i++) {
            if (params[i] instanceof Object[]) {
                param = (Object[])params[i];

                if (param.length == 2) {
                    if (param[0] == null) {
                        ps.setNull(i + 1, ((Integer)param[1]).intValue());
                        continue;
                    }

                    if (param[0] instanceof java.sql.Date ||
                        param[0] instanceof java.sql.Time ||
                        param[0] instanceof java.sql.Timestamp) {
                        System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.detecteddate"));
                        if (param[1] instanceof java.util.Calendar) {
                            System.err.println(resBundle.handleGetObject("jdbcrowsetimpl.detectedcalendar"));
                            ps.setDate(i + 1, (java.sql.Date)param[0],
                                       (java.util.Calendar)param[1]);
                            continue;
                        }
                        else {
                            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.paramtype").toString());
                        }
                    }

                    if (param[0] instanceof Reader) {
                        ps.setCharacterStream(i + 1, (Reader)param[0],
                                              ((Integer)param[1]).intValue());
                        continue;
                    }

                    /*
                     * What's left should be setObject(int, Object, scale)
                     */
                    if (param[1] instanceof Integer) {
                        ps.setObject(i + 1, param[0], ((Integer)param[1]).intValue());
                        continue;
                    }

                } else if (param.length == 3) {

                    if (param[0] == null) {
                        ps.setNull(i + 1, ((Integer)param[1]).intValue(),
                                   (String)param[2]);
                        continue;
                    }

                    if (param[0] instanceof java.io.InputStream) {
                        switch (((Integer)param[2]).intValue()) {
                        case JdbcRowSetImpl.UNICODE_STREAM_PARAM:
                            ps.setUnicodeStream(i + 1,
                                                (java.io.InputStream)param[0],
                                                ((Integer)param[1]).intValue());
                        case JdbcRowSetImpl.BINARY_STREAM_PARAM:
                            ps.setBinaryStream(i + 1,
                                               (java.io.InputStream)param[0],
                                               ((Integer)param[1]).intValue());
                        case JdbcRowSetImpl.ASCII_STREAM_PARAM:
                            ps.setAsciiStream(i + 1,
                                              (java.io.InputStream)param[0],
                                              ((Integer)param[1]).intValue());
                        default:
                            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.paramtype").toString());
                        }
                    }

                    /*
                     * no point at looking at the first element now;
                     * what's left must be the setObject() cases.
                     */
                    if (param[1] instanceof Integer && param[2] instanceof Integer) {
                        ps.setObject(i + 1, param[0], ((Integer)param[1]).intValue(),
                                     ((Integer)param[2]).intValue());
                        continue;
                    }

                    throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.paramtype").toString());

                } else {
                    // common case - this catches all SQL92 types
                    ps.setObject(i + 1, params[i]);
                    continue;
                }
            }  else {
               // Try to get all the params to be set here
               ps.setObject(i + 1, params[i]);

            }
        }
    }

    /**
     * Moves the cursor for this rowset's <code>ResultSet</code>
     * object down one row from its current position.
     * A <code>ResultSet</code> cursor is initially positioned
     * before the first row; the first call to the method
     * <code>next</code> makes the first row the current row; the
     * second call makes the second row the current row, and so on.
     *
     * <P>If an input stream is open for the current row, a call
     * to the method <code>next</code> will
     * implicitly close it. A <code>ResultSet</code> object's
     * warning chain is cleared when a new row is read.
     *
     * @return <code>true</code> if the new current row is valid;
     *         <code>false</code> if there are no more rows
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public boolean next() throws SQLException {
        checkState();

        boolean b = rs.next();
        notifyCursorMoved();
        return b;
    }

    /**
     * Releases this rowset's <code>ResultSet</code> object's database and
     * JDBC resources immediately instead of waiting for
     * this to happen when it is automatically closed.
     *
     * <P><B>Note:</B> A <code>ResultSet</code> object
     * is automatically closed by the
     * <code>Statement</code> object that generated it when
     * that <code>Statement</code> object is closed,
     * re-executed, or is used to retrieve the next result from a
     * sequence of multiple results. A <code>ResultSet</code> object
     * is also automatically closed when it is garbage collected.
     *
     * @throws SQLException if a database access error occurs
     */
    public void close() throws SQLException {
        if (rs != null)
            rs.close();
        if (ps != null)
            ps.close();
        if (conn != null)
            conn.close();
    }

    /**
     * Reports whether the last column read from this rowset's
     * <code>ResultSet</code> object had a value of SQL <code>NULL</code>.
     * Note that you must first call one of the <code>getXXX</code> methods
     * on a column to try to read its value and then call
     * the method <code>wasNull</code> to see if the value read was
     * SQL <code>NULL</code>.
     *
     * @return <code>true</code> if the last column value read was SQL
     *         <code>NULL</code> and <code>false</code> otherwise
     * @throws SQLException if a database access error occurs
     *            or this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public boolean wasNull() throws SQLException {
        checkState();

        return rs.wasNull();
    }

    //======================================================================
    // Methods for accessing results by column index
    //======================================================================

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>String</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public String getString(int columnIndex) throws SQLException {
        checkState();

        return rs.getString(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>boolean</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>false</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkState();

        return rs.getBoolean(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>byte</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public byte getByte(int columnIndex) throws SQLException {
        checkState();

        return rs.getByte(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>short</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public short getShort(int columnIndex) throws SQLException {
        checkState();

        return rs.getShort(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * an <code>int</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public int getInt(int columnIndex) throws SQLException {
        checkState();

        return rs.getInt(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>long</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public long getLong(int columnIndex) throws SQLException {
        checkState();

        return rs.getLong(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>float</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public float getFloat(int columnIndex) throws SQLException {
        checkState();

        return rs.getFloat(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>double</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public double getDouble(int columnIndex) throws SQLException {
        checkState();

        return rs.getDouble(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.BigDecimal</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param scale the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     * @deprecated
     */
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        checkState();

        return rs.getBigDecimal(columnIndex, scale);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>byte</code> array in the Java programming language.
     * The bytes represent the raw values returned by the driver.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public byte[] getBytes(int columnIndex) throws SQLException {
        checkState();

        return rs.getBytes(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Date</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Date getDate(int columnIndex) throws SQLException {
        checkState();

        return rs.getDate(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Time</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Time getTime(int columnIndex) throws SQLException {
        checkState();

        return rs.getTime(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Timestamp</code> object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkState();

        return rs.getTimestamp(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a stream of ASCII characters. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will
     * do any necessary conversion from the database format into ASCII.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream.  Also, a
     * stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return a Java input stream that delivers the database column value
     * as a stream of one-byte ASCII characters;
     * if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) database access error occurs
     *            (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.io.InputStream getAsciiStream(int columnIndex) throws SQLException {
        checkState();

        return rs.getAsciiStream(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * as a stream of Unicode characters.
     * The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large<code>LONGVARCHAR</code>values.  The JDBC driver will
     * do any necessary conversion from the database format into Unicode.
     * The byte format of the Unicode stream must be Java UTF-8,
     * as specified in the Java virtual machine specification.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream.  Also, a
     * stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return a Java input stream that delivers the database column value
     * as a stream in Java UTF-8 byte format;
     * if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     * @deprecated use <code>getCharacterStream</code> in place of
     *              <code>getUnicodeStream</code>
     */
    public java.io.InputStream getUnicodeStream(int columnIndex) throws SQLException {
        checkState();

        return rs.getUnicodeStream(columnIndex);
    }

    /**
     * Gets the value of a column in the current row as a stream of
     * the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a binary stream of
     * uninterpreted bytes. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARBINARY</code> values.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream.  Also, a
     * stream may return <code>0</code> when the method
     * <code>InputStream.available</code>
     * is called whether there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return a Java input stream that delivers the database column value
     * as a stream of uninterpreted bytes;
     * if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException {
        checkState();

        return rs.getBinaryStream(columnIndex);
    }


    //======================================================================
    // Methods for accessing results by column name
    //======================================================================

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>String</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public String getString(String columnName) throws SQLException {
        return getString(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>boolean</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>false</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public boolean getBoolean(String columnName) throws SQLException {
        return getBoolean(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>byte</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public byte getByte(String columnName) throws SQLException {
        return getByte(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>short</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public short getShort(String columnName) throws SQLException {
        return getShort(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * an <code>int</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public int getInt(String columnName) throws SQLException {
        return getInt(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>long</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if a database access error occurs
     *            or this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public long getLong(String columnName) throws SQLException {
        return getLong(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>float</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public float getFloat(String columnName) throws SQLException {
        return getFloat(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>double</code>.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>0</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public double getDouble(String columnName) throws SQLException {
        return getDouble(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.math.BigDecimal</code>.
     *
     * @param columnName the SQL name of the column
     * @param scale the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) adatabase access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     * @deprecated
     */
    public BigDecimal getBigDecimal(String columnName, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnName), scale);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>byte</code> array in the Java programming language.
     * The bytes represent the raw values returned by the driver.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public byte[] getBytes(String columnName) throws SQLException {
        return getBytes(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Date</code> object in the Java programming language.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Date getDate(String columnName) throws SQLException {
        return getDate(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Time</code> object in the Java programming language.
     *
     * @param columnName the SQL name of the column
     * @return the column value;
     * if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Time getTime(String columnName) throws SQLException {
        return getTime(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * a <code>java.sql.Timestamp</code> object.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Timestamp getTimestamp(String columnName) throws SQLException {
        return getTimestamp(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a stream of
     * ASCII characters. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will
     * do any necessary conversion from the database format into ASCII.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code>
     * is called whether there is data available or not.
     *
     * @param columnName the SQL name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of one-byte ASCII characters.
     * If the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>.
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.io.InputStream getAsciiStream(String columnName) throws SQLException {
        return getAsciiStream(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a stream of
     * Unicode characters. The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will
     * do any necessary conversion from the database format into Unicode.
     * The byte format of the Unicode stream must be Java UTF-8,
     * as defined in the Java virtual machine specification.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code>
     * is called whether there is data available or not.
     *
     * @param columnName the SQL name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of two-byte Unicode characters.
     * If the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>.
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     * @deprecated
     */
    public java.io.InputStream getUnicodeStream(String columnName) throws SQLException {
        return getUnicodeStream(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a stream of uninterpreted
     * <code>byte</code>s.
     * The value can then be read in chunks from the
     * stream. This method is particularly
     * suitable for retrieving large <code>LONGVARBINARY</code>
     * values.
     *
     * <P><B>Note:</B> All the data in the returned stream must be
     * read prior to getting the value of any other column. The next
     * call to a <code>getXXX</code> method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code>
     * is called whether there is data available or not.
     *
     * @param columnName the SQL name of the column
     * @return a Java input stream that delivers the database column value
     * as a stream of uninterpreted bytes;
     * if the value is SQL <code>NULL</code>, the result is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public java.io.InputStream getBinaryStream(String columnName) throws SQLException {
        return getBinaryStream(findColumn(columnName));
    }


    //=====================================================================
    // Advanced features:
    //=====================================================================

    /**
     * Returns the first warning reported by calls on this rowset's
     * <code>ResultSet</code> object.
     * Subsequent warnings on this rowset's <code>ResultSet</code> object
     * will be chained to the <code>SQLWarning</code> object that
     * this method returns.
     *
     * <P>The warning chain is automatically cleared each time a new
     * row is read.
     *
     * <P><B>Note:</B> This warning chain only covers warnings caused
     * by <code>ResultSet</code> methods.  Any warning caused by
     * <code>Statement</code> methods
     * (such as reading OUT parameters) will be chained on the
     * <code>Statement</code> object.
     *
     * @return the first <code>SQLWarning</code> object reported or <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public SQLWarning getWarnings() throws SQLException {
        checkState();

        return rs.getWarnings();
    }

    /**
     * Clears all warnings reported on this rowset's <code>ResultSet</code> object.
     * After this method is called, the method <code>getWarnings</code>
     * returns <code>null</code> until a new warning is
     * reported for this rowset's <code>ResultSet</code> object.
     *
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public void clearWarnings() throws SQLException {
        checkState();

        rs.clearWarnings();
    }

    /**
     * Gets the name of the SQL cursor used by this rowset's <code>ResultSet</code>
     * object.
     *
     * <P>In SQL, a result table is retrieved through a cursor that is
     * named. The current row of a result set can be updated or deleted
     * using a positioned update/delete statement that references the
     * cursor name. To insure that the cursor has the proper isolation
     * level to support update, the cursor's <code>select</code> statement should be
     * of the form 'select for update'. If the 'for update' clause is
     * omitted, the positioned updates may fail.
     *
     * <P>The JDBC API supports this SQL feature by providing the name of the
     * SQL cursor used by a <code>ResultSet</code> object.
     * The current row of a <code>ResultSet</code> object
     * is also the current row of this SQL cursor.
     *
     * <P><B>Note:</B> If positioned update is not supported, a
     * <code>SQLException</code> is thrown.
     *
     * @return the SQL name for this rowset's <code>ResultSet</code> object's cursor
     * @throws SQLException if (1) a database access error occurs
     *            or (2) xthis rowset does not have a currently valid connection,
     *            prepared statement, and result set
     */
    public String getCursorName() throws SQLException {
        checkState();

        return rs.getCursorName();
    }

    /**
     * Retrieves the  number, types and properties of
     * this rowset's <code>ResultSet</code> object's columns.
     *
     * @return the description of this rowset's <code>ResultSet</code>
     *     object's columns
     * @throws SQLException if (1) a database access error occurs
     *     or (2) this rowset does not have a currently valid connection,
     *     prepared statement, and result set
     */
    public ResultSetMetaData getMetaData() throws SQLException {

        checkState();

        // It may be the case that JdbcRowSet might not have been
        // initialized with ResultSet handle and may be by PreparedStatement
        // internally when we set JdbcRowSet.setCommand().
        // We may require all the basic properties of setEscapeProcessing
        // setMaxFieldSize etc. which an application can use before we call
        // execute.
        try {
             checkState();
        } catch(SQLException sqle) {
             prepare();
             // will return ResultSetMetaData
             return ps.getMetaData();
        }
        return rs.getMetaData();
    }

    /**
     * <p>Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * an <code>Object</code>.
     *
     * <p>This method will return the value of the given column as a
     * Java object.  The type of the Java object will be the default
     * Java object type corresponding to the column's SQL type,
     * following the mapping for built-in types specified in the JDBC
     * specification.
     *
     * <p>This method may also be used to read datatabase-specific
     * abstract data types.
     *
     * In the JDBC 3.0 API, the behavior of method
     * <code>getObject</code> is extended to materialize
     * data of SQL user-defined types.  When a column contains
     * a structured or distinct value, the behavior of this method is as
     * if it were a call to: <code>getObject(columnIndex,
     * this.getStatement().getConnection().getTypeMap())</code>.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Object getObject(int columnIndex) throws SQLException {
        checkState();

        return rs.getObject(columnIndex);
    }

    /**
     * <p>Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as
     * an <code>Object</code>.
     *
     * <p>This method will return the value of the given column as a
     * Java object.  The type of the Java object will be the default
     * Java object type corresponding to the column's SQL type,
     * following the mapping for built-in types specified in the JDBC
     * specification.
     *
     * <p>This method may also be used to read datatabase-specific
     * abstract data types.
     *
     * In the JDBC 3.0 API, the behavior of the method
     * <code>getObject</code> is extended to materialize
     * data of SQL user-defined types.  When a column contains
     * a structured or distinct value, the behavior of this method is as
     * if it were a call to: <code>getObject(columnIndex,
     * this.getStatement().getConnection().getTypeMap())</code>.
     *
     * @param columnName the SQL name of the column
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Object getObject(String columnName) throws SQLException {
        return getObject(findColumn(columnName));
    }

    //----------------------------------------------------------------

    /**
     * Maps the given <code>JdbcRowSetImpl</code> column name to its
     * <code>JdbcRowSetImpl</code> column index and reflects this on
     * the internal <code>ResultSet</code> object.
     *
     * @param columnName the name of the column
     * @return the column index of the given column name
     * @throws SQLException if (1) a database access error occurs
     * (2) this rowset does not have a currently valid connection,
     * prepared statement, and result set
     */
    public int findColumn(String columnName) throws SQLException {
        checkState();

        return rs.findColumn(columnName);
    }


    //--------------------------JDBC 2.0-----------------------------------

    //---------------------------------------------------------------------
    // Getters and Setters
    //---------------------------------------------------------------------

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>.
     * @param columnIndex the first column is 1, the second is 2, and so on
     *
     */
    public java.io.Reader getCharacterStream(int columnIndex) throws SQLException {
        checkState();

        return rs.getCharacterStream(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     *
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>.
     * @param columnName the name of the column
     * @return the value in the specified column as a <code>java.io.Reader</code>
     *
     */
    public java.io.Reader getCharacterStream(String columnName) throws SQLException {
        return getCharacterStream(findColumn(columnName));
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.math.BigDecimal</code> with full precision.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @return the column value (full precision);
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>.
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkState();

        return rs.getBigDecimal(columnIndex);
    }

    /**
     * Gets the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.math.BigDecimal</code> with full precision.
     *
     * @param columnName the column name
     * @return the column value (full precision);
     * if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code>.
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public BigDecimal getBigDecimal(String columnName) throws SQLException {
        return getBigDecimal(findColumn(columnName));
    }

    //---------------------------------------------------------------------
    // Traversal/Positioning
    //---------------------------------------------------------------------

    /**
     * Indicates whether the cursor is before the first row in
     * this rowset's <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is before the first row;
     * <code>false</code> if the cursor is at any other position or the
     * result set contains no rows
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean isBeforeFirst() throws SQLException {
        checkState();

        return rs.isBeforeFirst();
    }

    /**
     * Indicates whether the cursor is after the last row in
     * this rowset's <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is after the last row;
     * <code>false</code> if the cursor is at any other position or the
     * result set contains no rows
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean isAfterLast() throws SQLException {
        checkState();

        return rs.isAfterLast();
    }

    /**
     * Indicates whether the cursor is on the first row of
     * this rowset's <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is on the first row;
     * <code>false</code> otherwise
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean isFirst() throws SQLException {
        checkState();

        return rs.isFirst();
    }

    /**
     * Indicates whether the cursor is on the last row of
     * this rowset's <code>ResultSet</code> object.
     * Note: Calling the method <code>isLast</code> may be expensive
     * because the JDBC driver
     * might need to fetch ahead one row in order to determine
     * whether the current row is the last row in the result set.
     *
     * @return <code>true</code> if the cursor is on the last row;
     * <code>false</code> otherwise
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     *
     */
    public boolean isLast() throws SQLException {
        checkState();

        return rs.isLast();
    }

    /**
     * Moves the cursor to the front of
     * this rowset's <code>ResultSet</code> object, just before the
     * first row. This method has no effect if the result set contains no rows.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public void beforeFirst() throws SQLException {
        checkState();

        rs.beforeFirst();
        notifyCursorMoved();
    }

    /**
     * Moves the cursor to the end of
     * this rowset's <code>ResultSet</code> object, just after the
     * last row. This method has no effect if the result set contains no rows.
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public void afterLast() throws SQLException {
        checkState();

        rs.afterLast();
        notifyCursorMoved();
    }

    /**
     * Moves the cursor to the first row in
     * this rowset's <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is on a valid row;
     * <code>false</code> if there are no rows in the result set
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean first() throws SQLException {
        checkState();

        boolean b = rs.first();
        notifyCursorMoved();
        return b;

    }

    /**
     * Moves the cursor to the last row in
     * this rowset's <code>ResultSet</code> object.
     *
     * @return <code>true</code> if the cursor is on a valid row;
     * <code>false</code> if there are no rows in the result set
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean last() throws SQLException {
        checkState();

        boolean b = rs.last();
        notifyCursorMoved();
        return b;
    }

    /**
     * Retrieves the current row number.  The first row is number 1, the
     * second is number 2, and so on.
     *
     * @return the current row number; <code>0</code> if there is no current row
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public int getRow() throws SQLException {
        checkState();

        return rs.getRow();
    }

    /**
     * Moves the cursor to the given row number in
     * this rowset's internal <code>ResultSet</code> object.
     *
     * <p>If the row number is positive, the cursor moves to
     * the given row number with respect to the
     * beginning of the result set.  The first row is row 1, the second
     * is row 2, and so on.
     *
     * <p>If the given row number is negative, the cursor moves to
     * an absolute row position with respect to
     * the end of the result set.  For example, calling the method
     * <code>absolute(-1)</code> positions the
     * cursor on the last row, calling the method <code>absolute(-2)</code>
     * moves the cursor to the next-to-last row, and so on.
     *
     * <p>An attempt to position the cursor beyond the first/last row in
     * the result set leaves the cursor before the first row or after
     * the last row.
     *
     * <p><B>Note:</B> Calling <code>absolute(1)</code> is the same
     * as calling <code>first()</code>. Calling <code>absolute(-1)</code>
     * is the same as calling <code>last()</code>.
     *
     * @return <code>true</code> if the cursor is on the result set;
     * <code>false</code> otherwise
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the row is <code>0</code>, (3) the result set
     *            type is <code>TYPE_FORWARD_ONLY</code>, or (4) this
     *            rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public boolean absolute(int row) throws SQLException {
        checkState();

        boolean b = rs.absolute(row);
        notifyCursorMoved();
        return b;
    }

    /**
     * Moves the cursor a relative number of rows, either positive or negative.
     * Attempting to move beyond the first/last row in the
     * result set positions the cursor before/after the
     * the first/last row. Calling <code>relative(0)</code> is valid, but does
     * not change the cursor position.
     *
     * <p>Note: Calling the method <code>relative(1)</code>
     * is different from calling the method <code>next()</code>
     * because is makes sense to call <code>next()</code> when there
     * is no current row,
     * for example, when the cursor is positioned before the first row
     * or after the last row of the result set.
     *
     * @return <code>true</code> if the cursor is on a row;
     * <code>false</code> otherwise
     * @throws SQLException if (1) a database access error occurs,
     *            (2) there is no current row, (3) the result set
     *            type is <code>TYPE_FORWARD_ONLY</code>, or (4) this
     *            rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public boolean relative(int rows) throws SQLException {
        checkState();

        boolean b = rs.relative(rows);
        notifyCursorMoved();
        return b;
    }

    /**
     * Moves the cursor to the previous row in this
     * <code>ResultSet</code> object.
     *
     * <p><B>Note:</B> Calling the method <code>previous()</code> is not the same as
     * calling the method <code>relative(-1)</code> because it
     * makes sense to call <code>previous()</code> when there is no current row.
     *
     * @return <code>true</code> if the cursor is on a valid row;
     * <code>false</code> if it is off the result set
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     */
    public boolean previous() throws SQLException {
        checkState();

        boolean b = rs.previous();
        notifyCursorMoved();
        return b;
    }

    /**
     * Gives a hint as to the direction in which the rows in this
     * <code>ResultSet</code> object will be processed.
     * The initial value is determined by the
     * <code>Statement</code> object
     * that produced this rowset's <code>ResultSet</code> object.
     * The fetch direction may be changed at any time.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) the result set type is <code>TYPE_FORWARD_ONLY</code>
     *            and the fetch direction is not <code>FETCH_FORWARD</code>,
     *            or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     * @see java.sql.Statement#setFetchDirection
     */
    public void setFetchDirection(int direction) throws SQLException {
        checkState();

        rs.setFetchDirection(direction);
    }

    /**
     * Returns the fetch direction for this
     * <code>ResultSet</code> object.
     *
     * @return the current fetch direction for this rowset's
     *         <code>ResultSet</code> object
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public int getFetchDirection() throws SQLException {
        try {
             checkState();
        } catch(SQLException sqle) {
             super.getFetchDirection();
        }
        return rs.getFetchDirection();
    }

    /**
     * Gives the JDBC driver a hint as to the number of rows that should
     * be fetched from the database when more rows are needed for this
     * <code>ResultSet</code> object.
     * If the fetch size specified is zero, the JDBC driver
     * ignores the value and is free to make its own best guess as to what
     * the fetch size should be.  The default value is set by the
     * <code>Statement</code> object
     * that created the result set.  The fetch size may be changed at any time.
     *
     * @param rows the number of rows to fetch
     * @throws SQLException if (1) a database access error occurs, (2) the
     *            condition <code>0 <= rows <= this.getMaxRows()</code> is not
     *            satisfied, or (3) this rowset does not currently have a valid
     *            connection, prepared statement, and result set
     *
     */
    public void setFetchSize(int rows) throws SQLException {
        checkState();

        rs.setFetchSize(rows);
    }

    /**
     *
     * Returns the fetch size for this
     * <code>ResultSet</code> object.
     *
     * @return the current fetch size for this rowset's <code>ResultSet</code> object
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public int getType() throws SQLException {
        try {
             checkState();
        } catch(SQLException sqle) {
            return super.getType();
        }

        // If the ResultSet has not been created, then return the default type
        // otherwise return the type from the ResultSet.
        if(rs == null) {
            return super.getType();
        } else {
           int rstype = rs.getType();
            return rstype;
        }


    }

    /**
     * Returns the concurrency mode of this rowset's <code>ResultSet</code> object.
     * The concurrency used is determined by the
     * <code>Statement</code> object that created the result set.
     *
     * @return the concurrency type, either <code>CONCUR_READ_ONLY</code>
     * or <code>CONCUR_UPDATABLE</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public int getConcurrency() throws SQLException {
        try {
             checkState();
        } catch(SQLException sqle) {
             super.getConcurrency();
        }
        return rs.getConcurrency();
    }

    //---------------------------------------------------------------------
    // Updates
    //---------------------------------------------------------------------

    /**
     * Indicates whether the current row has been updated.  The value returned
     * depends on whether or not the result set can detect updates.
     *
     * @return <code>true</code> if the row has been visibly updated
     * by the owner or another, and updates are detected
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     * @see java.sql.DatabaseMetaData#updatesAreDetected
     */
    public boolean rowUpdated() throws SQLException {
        checkState();

        return rs.rowUpdated();
    }

    /**
     * Indicates whether the current row has had an insertion.
     * The value returned depends on whether or not this
     * <code>ResultSet</code> object can detect visible inserts.
     *
     * @return <code>true</code> if a row has had an insertion
     * and insertions are detected; <code>false</code> otherwise
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     * @see java.sql.DatabaseMetaData#insertsAreDetected
     *
     */
    public boolean rowInserted() throws SQLException {
        checkState();

        return rs.rowInserted();
    }

    /**
     * Indicates whether a row has been deleted.  A deleted row may leave
     * a visible "hole" in a result set.  This method can be used to
     * detect holes in a result set.  The value returned depends on whether
     * or not this rowset's <code>ResultSet</code> object can detect deletions.
     *
     * @return <code>true</code> if a row was deleted and deletions are detected;
     * <code>false</code> otherwise
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     * @see java.sql.DatabaseMetaData#deletesAreDetected
     */
    public boolean rowDeleted() throws SQLException {
        checkState();

        return rs.rowDeleted();
    }

    /**
     * Gives a nullable column a null value.
     *
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code>
     * or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public void updateNull(int columnIndex) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateNull(columnIndex);
    }

    /**
     * Updates the designated column with a <code>boolean</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateBoolean(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>byte</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateByte(int columnIndex, byte x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateByte(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>short</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateShort(int columnIndex, short x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateShort(columnIndex, x);
    }

    /**
     * Updates the designated column with an <code>int</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public void updateInt(int columnIndex, int x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateInt(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>long</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateLong(int columnIndex, long x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateLong(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>float</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateFloat(int columnIndex, float x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateFloat(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>double</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateDouble(int columnIndex, double x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateDouble(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>java.math.BigDecimal</code>
     * value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateBigDecimal(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateString(int columnIndex, String x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateString(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>byte</code> array value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateBytes(int columnIndex, byte x[]) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateBytes(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateDate(int columnIndex, java.sql.Date x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateDate(columnIndex, x);
    }


    /**
     * Updates the designated column with a <code>java.sql.Time</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateTime(int columnIndex, java.sql.Time x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateTime(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code>
     * value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateTimestamp(int columnIndex, java.sql.Timestamp x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateTimestamp(columnIndex, x);
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @param length the length of the stream
     * @throws SQLException if (1) a database access error occurs
     *            (2) or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateAsciiStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateAsciiStream(columnIndex, x, length);
    }

    /**
     * Updates the designated column with a binary stream value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @param length the length of the stream
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateBinaryStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateBinaryStream(columnIndex, x, length);
    }

    /**
     * Updates the designated column with a character stream value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @param length the length of the stream
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateCharacterStream(int columnIndex, java.io.Reader x, int length) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateCharacterStream(columnIndex, x, length);
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @param scale for <code>java.sql.Types.DECIMAl</code>
     *  or <code>java.sql.Types.NUMERIC</code> types,
     *  this is the number of digits after the decimal point.  For all other
     *  types this value will be ignored.
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateObject(int columnIndex, Object x, int scale) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateObject(columnIndex, x, scale);
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateObject(int columnIndex, Object x) throws SQLException {
        checkState();

        // To check the type and concurrency of the ResultSet
        // to verify whether updates are possible or not
        checkTypeConcurrency();

        rs.updateObject(columnIndex, x);
    }

    /**
     * Updates the designated column with a <code>null</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public void updateNull(String columnName) throws SQLException {
        updateNull(findColumn(columnName));
    }

    /**
     * Updates the designated column with a <code>boolean</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateBoolean(String columnName, boolean x) throws SQLException {
        updateBoolean(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>byte</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateByte(String columnName, byte x) throws SQLException {
        updateByte(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>short</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateShort(String columnName, short x) throws SQLException {
        updateShort(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with an <code>int</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateInt(String columnName, int x) throws SQLException {
        updateInt(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>long</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateLong(String columnName, long x) throws SQLException {
        updateLong(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>float </code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateFloat(String columnName, float x) throws SQLException {
        updateFloat(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>double</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateDouble(String columnName, double x) throws SQLException {
        updateDouble(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>java.sql.BigDecimal</code>
     * value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateBigDecimal(String columnName, BigDecimal x) throws SQLException {
        updateBigDecimal(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>String</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateString(String columnName, String x) throws SQLException {
        updateString(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>boolean</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * JDBC 2.0
     *
     * Updates a column with a byte array value.
     *
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row, or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateBytes(String columnName, byte x[]) throws SQLException {
        updateBytes(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateDate(String columnName, java.sql.Date x) throws SQLException {
        updateDate(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>java.sql.Time</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateTime(String columnName, java.sql.Time x) throws SQLException {
        updateTime(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code>
     * value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateTimestamp(String columnName, java.sql.Timestamp x) throws SQLException {
        updateTimestamp(findColumn(columnName), x);
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @param length the length of the stream
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateAsciiStream(String columnName, java.io.InputStream x, int length) throws SQLException {
        updateAsciiStream(findColumn(columnName), x, length);
    }

    /**
     * Updates the designated column with a binary stream value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @param length the length of the stream
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateBinaryStream(String columnName, java.io.InputStream x, int length) throws SQLException {
        updateBinaryStream(findColumn(columnName), x, length);
    }

    /**
     * Updates the designated column with a character stream value.
     * The <code>updateXXX</code> methods are used to update column values
     * in the current row or the insert row.  The <code>updateXXX</code>
     * methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnName the name of the column
     * @param reader the new column <code>Reader</code> stream value
     * @param length the length of the stream
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateCharacterStream(String columnName, java.io.Reader reader, int length) throws SQLException {
        updateCharacterStream(findColumn(columnName), reader, length);
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @param scale for <code>java.sql.Types.DECIMAL</code>
     *  or <code>java.sql.Types.NUMERIC</code> types,
     *  this is the number of digits after the decimal point.  For all other
     *  types this value will be ignored.
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateObject(String columnName, Object x, int scale) throws SQLException {
        updateObject(findColumn(columnName), x, scale);
    }

    /**
     * Updates the designated column with an <code>Object</code> value.
     * The <code>updateXXX</code> methods are used to update column values in the
     * current row or the insert row.  The <code>updateXXX</code> methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnName the name of the column
     * @param x the new column value
     * @throws SQLException if a database access error occurs
     *
     */
    public void updateObject(String columnName, Object x) throws SQLException {
        updateObject(findColumn(columnName), x);
    }

    /**
     * Inserts the contents of the insert row into this
     * <code>ResultSet</code> object and into the database
     * and also notifies listeners that a row has changed.
     * The cursor must be on the insert row when this method is called.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this method is called when the cursor is not
     *             on the insert row, (3) not all non-nullable columns in
     *             the insert row have been given a value, or (4) this
     *             rowset does not currently have a valid connection,
     *             prepared statement, and result set
     */
    public void insertRow() throws SQLException {
        checkState();

        rs.insertRow();
        notifyRowChanged();
    }

    /**
     * Updates the underlying database with the new contents of the
     * current row of this rowset's <code>ResultSet</code> object
     * and notifies listeners that a row has changed.
     * This method cannot be called when the cursor is on the insert row.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this method is called when the cursor is
     *             on the insert row, (3) the concurrency of the result
     *             set is <code>ResultSet.CONCUR_READ_ONLY</code>, or
     *             (4) this rowset does not currently have a valid connection,
     *             prepared statement, and result set
     */
    public void updateRow() throws SQLException {
        checkState();

        rs.updateRow();
        notifyRowChanged();
    }

    /**
     * Deletes the current row from this rowset's <code>ResultSet</code> object
     * and from the underlying database and also notifies listeners that a row
     * has changed.  This method cannot be called when the cursor is on the insert
     * row.
     *
     * @throws SQLException if a database access error occurs
     * or if this method is called when the cursor is on the insert row
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this method is called when the cursor is before the
     *            first row, after the last row, or on the insert row,
     *            (3) the concurrency of this rowset's result
     *            set is <code>ResultSet.CONCUR_READ_ONLY</code>, or
     *            (4) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public void deleteRow() throws SQLException {
        checkState();

        rs.deleteRow();
        notifyRowChanged();
    }

    /**
     * Refreshes the current row of this rowset's <code>ResultSet</code>
     * object with its most recent value in the database.  This method
     * cannot be called when the cursor is on the insert row.
     *
     * <P>The <code>refreshRow</code> method provides a way for an
     * application to explicitly tell the JDBC driver to refetch
     * a row(s) from the database.  An application may want to call
     * <code>refreshRow</code> when caching or prefetching is being
     * done by the JDBC driver to fetch the latest value of a row
     * from the database.  The JDBC driver may actually refresh multiple
     * rows at once if the fetch size is greater than one.
     *
     * <P> All values are refetched subject to the transaction isolation
     * level and cursor sensitivity.  If <code>refreshRow</code> is called after
     * calling an <code>updateXXX</code> method, but before calling
     * the method <code>updateRow</code>, then the
     * updates made to the row are lost.  Calling the method
     * <code>refreshRow</code> frequently will likely slow performance.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this method is called when the cursor is
     *             on the insert row, or (3) this rowset does not
     *             currently have a valid connection, prepared statement,
     *             and result set
     *
     */
    public void refreshRow() throws SQLException {
        checkState();

        rs.refreshRow();
    }

    /**
     * Cancels the updates made to the current row in this
     * <code>ResultSet</code> object and notifies listeners that a row
     * has changed. This method may be called after calling an
     * <code>updateXXX</code> method(s) and before calling
     * the method <code>updateRow</code> to roll back
     * the updates made to a row.  If no updates have been made or
     * <code>updateRow</code> has already been called, this method has no
     * effect.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this method is called when the cursor is
     *             on the insert row, or (3) this rowset does not
     *             currently have a valid connection, prepared statement,
     *             and result set
     */
    public void cancelRowUpdates() throws SQLException {
        checkState();

        rs.cancelRowUpdates();

        notifyRowChanged();
    }

    /**
     * Moves the cursor to the insert row.  The current cursor position is
     * remembered while the cursor is positioned on the insert row.
     *
     * The insert row is a special row associated with an updatable
     * result set.  It is essentially a buffer where a new row may
     * be constructed by calling the <code>updateXXX</code> methods prior to
     * inserting the row into the result set.
     *
     * Only the <code>updateXXX</code>, <code>getXXX</code>,
     * and <code>insertRow</code> methods may be
     * called when the cursor is on the insert row.  All of the columns in
     * a result set must be given a value each time this method is
     * called before calling <code>insertRow</code>.
     * An <code>updateXXX</code> method must be called before a
     * <code>getXXX</code> method can be called on a column value.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this rowset's <code>ResultSet</code> object is
     *             not updatable, or (3) this rowset does not
     *             currently have a valid connection, prepared statement,
     *             and result set
     *
     */
    public void moveToInsertRow() throws SQLException {
        checkState();

        rs.moveToInsertRow();
    }

    /**
     * Moves the cursor to the remembered cursor position, usually the
     * current row.  This method has no effect if the cursor is not on
     * the insert row.
     *
     * @throws SQLException if (1) a database access error occurs,
     *            (2) this rowset's <code>ResultSet</code> object is
     *             not updatable, or (3) this rowset does not
     *             currently have a valid connection, prepared statement,
     *             and result set
     */
    public void moveToCurrentRow() throws SQLException {
        checkState();

        rs.moveToCurrentRow();
    }

    /**
     * Returns the <code>Statement</code> object that produced this
     * <code>ResultSet</code> object.
     * If the result set was generated some other way, such as by a
     * <code>DatabaseMetaData</code> method, this method returns
     * <code>null</code>.
     *
     * @return the <code>Statment</code> object that produced
     * this rowset's <code>ResultSet</code> object or <code>null</code>
     * if the result set was produced some other way
     * @throws SQLException if a database access error occurs
     */
    public java.sql.Statement getStatement() throws SQLException {

        if(rs != null)
        {
           return rs.getStatement();
        } else {
           return null;
        }
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as an <code>Object</code>.
     * This method uses the given <code>Map</code> object
     * for the custom mapping of the
     * SQL structured or distinct type that is being retrieved.
     *
     * @param i the first column is 1, the second is 2, and so on
     * @param map a <code>java.util.Map</code> object that contains the mapping
     * from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> in the Java programming language
     * representing the SQL value
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Object getObject(int i, java.util.Map<String,Class<?>> map)
        throws SQLException
    {
        checkState();

        return rs.getObject(i, map);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Ref</code> object.
     *
     * @param i the first column is 1, the second is 2, and so on
     * @return a <code>Ref</code> object representing an SQL <code>REF</code> value
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Ref getRef(int i) throws SQLException {
        checkState();

        return rs.getRef(i);
    }


    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Blob</code> object.
     *
     * @param i the first column is 1, the second is 2, and so on
     * @return a <code>Blob</code> object representing the SQL <code>BLOB</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Blob getBlob(int i) throws SQLException {
        checkState();

        return rs.getBlob(i);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Clob</code> object.
     *
     * @param i the first column is 1, the second is 2, and so on
     * @return a <code>Clob</code> object representing the SQL <code>CLOB</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Clob getClob(int i) throws SQLException {
        checkState();

        return rs.getClob(i);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as an <code>Array</code> object.
     *
     * @param i the first column is 1, the second is 2, and so on.
     * @return an <code>Array</code> object representing the SQL <code>ARRAY</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Array getArray(int i) throws SQLException {
        checkState();

        return rs.getArray(i);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as an <code>Object</code>.
     * This method uses the specified <code>Map</code> object for
     * custom mapping if appropriate.
     *
     * @param colName the name of the column from which to retrieve the value
     * @param map a <code>java.util.Map</code> object that contains the mapping
     * from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> representing the SQL
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Object getObject(String colName, java.util.Map<String,Class<?>> map)
        throws SQLException
    {
        return getObject(findColumn(colName), map);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Ref</code> object.
     *
     * @param colName the column name
     * @return a <code>Ref</code> object representing the SQL <code>REF</code> value in
     *         the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Ref getRef(String colName) throws SQLException {
        return getRef(findColumn(colName));
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Blob</code> object.
     *
     * @param colName the name of the column from which to retrieve the value
     * @return a <code>Blob</code> object representing the SQL <code>BLOB</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Blob getBlob(String colName) throws SQLException {
        return getBlob(findColumn(colName));
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>Clob</code> object.
     *
     * @param colName the name of the column from which to retrieve the value
     * @return a <code>Clob</code> object representing the SQL <code>CLOB</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Clob getClob(String colName) throws SQLException {
        return getClob(findColumn(colName));
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as an <code>Array</code> object.
     *
     * @param colName the name of the column from which to retrieve the value
     * @return an <code>Array</code> object representing the SQL <code>ARRAY</code>
     *         value in the specified column
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public Array getArray(String colName) throws SQLException {
        return getArray(findColumn(colName));
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>java.sql.Date</code>
     * object. This method uses the given calendar to construct an appropriate
     * millisecond value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code>
     * @throws SQLException if (1) a database access error occurs
     *            or (2) this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Date getDate(int columnIndex, Calendar cal) throws SQLException {
        checkState();

        return rs.getDate(columnIndex, cal);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>java.sql.Date</code>
     * object. This method uses the given calendar to construct an appropriate
     * millisecond value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnName the SQL name of the column from which to retrieve the value
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code>
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     *
     */
    public java.sql.Date getDate(String columnName, Calendar cal) throws SQLException {
        return getDate(findColumn(columnName), cal);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>java.sql.Time</code>
     * object. This method uses the given calendar to construct an appropriate
     * millisecond value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code> in the Java programming language
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkState();

        return rs.getTime(columnIndex, cal);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a <code>java.sql.Time</code>
     * object. This method uses the given calendar to construct an appropriate
     * millisecond value for the date if the underlying database does not store
     * timezone information.
     *
     * @param columnName the SQL name of the column
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code> in the Java programming language
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Time getTime(String columnName, Calendar cal) throws SQLException {
        return getTime(findColumn(columnName), cal);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.sql.Timestamp</code> object.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the timestamp if the underlying database does not store
     * timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, and so on
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the timestamp
     * @return the column value as a <code>java.sql.Timestamp</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code>
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        checkState();

        return rs.getTimestamp(columnIndex, cal);
    }

    /**
     * Returns the value of the designated column in the current row
     * of this rowset's <code>ResultSet</code> object as a
     * <code>java.sql.Timestamp</code> object.
     * This method uses the given calendar to construct an appropriate millisecond
     * value for the timestamp if the underlying database does not store
     * timezone information.
     *
     * @param columnName the SQL name of the column
     * @param cal the <code>java.util.Calendar</code> object
     *        to use in constructing the timestamp
     * @return the column value as a <code>java.sql.Timestamp</code> object;
     *         if the value is SQL <code>NULL</code>,
     *         the value returned is <code>null</code>
     * @throws SQLException if a database access error occurs
     *            or this rowset does not currently have a valid connection,
     *            prepared statement, and result set
     */
    public java.sql.Timestamp getTimestamp(String columnName, Calendar cal) throws SQLException {
        return getTimestamp(findColumn(columnName), cal);
    }


    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>double</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnIndex the first column is <code>1</code>, the second
     *        is <code>2</code>, and so on; must be <code>1</code> or larger
     *        and equal to or less than the number of columns in this rowset
     * @param ref the new <code>Ref</code> column value
     * @throws SQLException if (1) the given column index is out of bounds,
     *            (2) the cursor is not on one of this rowset's rows or its
     *            insert row, or (3) this rowset is
     *            <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateRef(int columnIndex, java.sql.Ref ref)
        throws SQLException {
        checkState();
        rs.updateRef(columnIndex, ref);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>double</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnName a <code>String</code> object that must match the
     *        SQL name of a column in this rowset, ignoring case
     * @param ref the new column value
     * @throws SQLException if (1) the given column name does not match the
     *            name of a column in this rowset, (2) the cursor is not on
     *            one of this rowset's rows or its insert row, or (3) this
     *            rowset is <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateRef(String columnName, java.sql.Ref ref)
        throws SQLException {
        updateRef(findColumn(columnName), ref);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>double</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnIndex the first column is <code>1</code>, the second
     *        is <code>2</code>, and so on; must be <code>1</code> or larger
     *        and equal to or less than the number of columns in this rowset
     * @param c the new column <code>Clob</code> value
     * @throws SQLException if (1) the given column index is out of bounds,
     *            (2) the cursor is not on one of this rowset's rows or its
     *            insert row, or (3) this rowset is
     *            <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateClob(int columnIndex, Clob c) throws SQLException {
        checkState();
        rs.updateClob(columnIndex, c);
    }


    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>double</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnName a <code>String</code> object that must match the
     *        SQL name of a column in this rowset, ignoring case
     * @param c the new column <code>Clob</code> value
     * @throws SQLException if (1) the given column name does not match the
     *            name of a column in this rowset, (2) the cursor is not on
     *            one of this rowset's rows or its insert row, or (3) this
     *            rowset is <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateClob(String columnName, Clob c) throws SQLException {
        updateClob(findColumn(columnName), c);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>java.sql.Blob</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnIndex the first column is <code>1</code>, the second
     *        is <code>2</code>, and so on; must be <code>1</code> or larger
     *        and equal to or less than the number of columns in this rowset
     * @param b the new column <code>Blob</code> value
     * @throws SQLException if (1) the given column index is out of bounds,
     *            (2) the cursor is not on one of this rowset's rows or its
     *            insert row, or (3) this rowset is
     *            <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateBlob(int columnIndex, Blob b) throws SQLException {
        checkState();
        rs.updateBlob(columnIndex, b);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>java.sql.Blob </code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnName a <code>String</code> object that must match the
     *        SQL name of a column in this rowset, ignoring case
     * @param b the new column <code>Blob</code> value
     * @throws SQLException if (1) the given column name does not match the
     *            name of a column in this rowset, (2) the cursor is not on
     *            one of this rowset's rows or its insert row, or (3) this
     *            rowset is <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateBlob(String columnName, Blob b) throws SQLException {
        updateBlob(findColumn(columnName), b);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>java.sql.Array</code> values.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnIndex the first column is <code>1</code>, the second
     *        is <code>2</code>, and so on; must be <code>1</code> or larger
     *        and equal to or less than the number of columns in this rowset
     * @param a the new column <code>Array</code> value
     * @throws SQLException if (1) the given column index is out of bounds,
     *            (2) the cursor is not on one of this rowset's rows or its
     *            insert row, or (3) this rowset is
     *            <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateArray(int columnIndex, Array a) throws SQLException {
        checkState();
        rs.updateArray(columnIndex, a);
    }

    /**
     * Sets the designated column in either the current row or the insert
     * row of this <code>JdbcRowSetImpl</code> object with the given
     * <code>java.sql.Array</code> value.
     *
     * This method updates a column value in either the current row or
     * the insert row of this rowset, but it does not update the
     * database.  If the cursor is on a row in the rowset, the
     * method {@link #updateRow} must be called to update the database.
     * If the cursor is on the insert row, the method {@link #insertRow}
     * must be called, which will insert the new row into both this rowset
     * and the database. Both of these methods must be called before the
     * cursor moves to another row.
     *
     * @param columnName a <code>String</code> object that must match the
     *        SQL name of a column in this rowset, ignoring case
     * @param a the new column <code>Array</code> value
     * @throws SQLException if (1) the given column name does not match the
     *            name of a column in this rowset, (2) the cursor is not on
     *            one of this rowset's rows or its insert row, or (3) this
     *            rowset is <code>ResultSet.CONCUR_READ_ONLY</code>
     */
    public void updateArray(String columnName, Array a) throws SQLException {
        updateArray(findColumn(columnName), a);
    }

    /**
     * Provide interface coverage for getURL(int) in ResultSet->RowSet
     */
    public java.net.URL getURL(int columnIndex) throws SQLException {
        checkState();
        return rs.getURL(columnIndex);
    }

    /**
     * Provide interface coverage for getURL(String) in ResultSet->RowSet
     */
    public java.net.URL getURL(String columnName) throws SQLException {
        return getURL(findColumn(columnName));
    }

    /**
     * Return the RowSetWarning object for the current row of a
     * <code>JdbcRowSetImpl</code>
     */
    public RowSetWarning getRowSetWarnings() throws SQLException {
       return null;
    }
    /**
     * Unsets the designated parameter to the given int array.
     * This was set using <code>setMatchColumn</code>
     * as the column which will form the basis of the join.
     * <P>
     * The parameter value unset by this method should be same
     * as was set.
     *
     * @param columnIdxes the index into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds or if the columnIdx is
     *  not the same as set using <code>setMatchColumn(int [])</code>
     */
    public void unsetMatchColumn(int[] columnIdxes) throws SQLException {

         int i_val;
         for( int j= 0 ;j < columnIdxes.length; j++) {
            i_val = (Integer.parseInt(iMatchColumns.get(j).toString()));
            if(columnIdxes[j] != i_val) {
               throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols").toString());
            }
         }

         for( int i = 0;i < columnIdxes.length ;i++) {
            iMatchColumns.set(i,new Integer(-1));
         }
    }

   /**
     * Unsets the designated parameter to the given String array.
     * This was set using <code>setMatchColumn</code>
     * as the column which will form the basis of the join.
     * <P>
     * The parameter value unset by this method should be same
     * as was set.
     *
     * @param columnIdxes the index into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds or if the columnName is
     *  not the same as set using <code>setMatchColumn(String [])</code>
     */
    public void unsetMatchColumn(String[] columnIdxes) throws SQLException {

        for(int j = 0 ;j < columnIdxes.length; j++) {
           if( !columnIdxes[j].equals(strMatchColumns.get(j)) ){
              throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols").toString());
           }
        }

        for(int i = 0 ; i < columnIdxes.length; i++) {
           strMatchColumns.set(i,null);
        }
    }

    /**
     * Retrieves the column name as <code>String</code> array
     * that was set using <code>setMatchColumn(String [])</code>
     * for this rowset.
     *
     * @return a <code>String</code> array object that contains the column names
     *         for the rowset which has this the match columns
     *
     * @throws SQLException if an error occurs or column name is not set
     */
    public String[] getMatchColumnNames() throws SQLException {

        String []str_temp = new String[strMatchColumns.size()];

        if( strMatchColumns.get(0) == null) {
           throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.setmatchcols").toString());
        }

        strMatchColumns.copyInto(str_temp);
        return str_temp;
    }

    /**
     * Retrieves the column id as <code>int</code> array that was set using
     * <code>setMatchColumn(int [])</code> for this rowset.
     *
     * @return a <code>int</code> array object that contains the column ids
     *         for the rowset which has this as the match columns.
     *
     * @throws SQLException if an error occurs or column index is not set
     */
    public int[] getMatchColumnIndexes() throws SQLException {

        Integer []int_temp = new Integer[iMatchColumns.size()];
        int [] i_temp = new int[iMatchColumns.size()];
        int i_val;

        i_val = ((Integer)iMatchColumns.get(0)).intValue();

        if( i_val == -1 ) {
           throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.setmatchcols").toString());
        }


        iMatchColumns.copyInto(int_temp);

        for(int i = 0; i < int_temp.length; i++) {
           i_temp[i] = (int_temp[i]).intValue();
        }

        return i_temp;
    }

    /**
     * Sets the designated parameter to the given int array.
     * This forms the basis of the join for the
     * <code>JoinRowSet</code> as the column which will form the basis of the
     * join.
     * <P>
     * The parameter value set by this method is stored internally and
     * will be supplied as the appropriate parameter in this rowset's
     * command when the method <code>getMatchColumnIndexes</code> is called.
     *
     * @param columnIdxes the indexes into this rowset
     *        object's internal representation of parameter values; the
     *        first parameter is 0, the second is 1, and so on; must be
     *        <code>0</code> or greater
     * @throws SQLException if an error occurs or the
     *                         parameter index is out of bounds
     */
    public void setMatchColumn(int[] columnIdxes) throws SQLException {

        for(int j = 0 ; j < columnIdxes.length; j++) {
           if( columnIdxes[j] < 0 ) {
              throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols1").toString());
           }
        }
        for(int i = 0 ;i < columnIdxes.length; i++) {
           iMatchColumns.add(i,new Integer(columnIdxes[i]));
        }
    }

    /**
     * Sets the designated parameter to the given String array.
     *  This forms the basis of the join for the
     * <code>JoinRowSet</code> as the column which will form the basis of the
     * join.
     * <P>
     * The parameter value set by this method is stored internally and
     * will be supplied as the appropriate parameter in this rowset's
     * command when the method <code>getMatchColumn</code> is called.
     *
     * @param columnNames the name of the column into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds
     */
    public void setMatchColumn(String[] columnNames) throws SQLException {

        for(int j = 0; j < columnNames.length; j++) {
           if( columnNames[j] == null || columnNames[j].equals("")) {
              throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols2").toString());
           }
        }
        for( int i = 0; i < columnNames.length; i++) {
           strMatchColumns.add(i,columnNames[i]);
        }
    }


        /**
     * Sets the designated parameter to the given <code>int</code>
     * object.  This forms the basis of the join for the
     * <code>JoinRowSet</code> as the column which will form the basis of the
     * join.
     * <P>
     * The parameter value set by this method is stored internally and
     * will be supplied as the appropriate parameter in this rowset's
     * command when the method <code>getMatchColumn</code> is called.
     *
     * @param columnIdx the index into this rowset
     *        object's internal representation of parameter values; the
     *        first parameter is 0, the second is 1, and so on; must be
     *        <code>0</code> or greater
     * @throws SQLException if an error occurs or the
     *                         parameter index is out of bounds
     */
    public void setMatchColumn(int columnIdx) throws SQLException {
        // validate, if col is ok to be set
        if(columnIdx < 0) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols1").toString());
        } else {
            // set iMatchColumn
            iMatchColumns.set(0, new Integer(columnIdx));
            //strMatchColumn = null;
        }
    }

    /**
     * Sets the designated parameter to the given <code>String</code>
     * object.  This forms the basis of the join for the
     * <code>JoinRowSet</code> as the column which will form the basis of the
     * join.
     * <P>
     * The parameter value set by this method is stored internally and
     * will be supplied as the appropriate parameter in this rowset's
     * command when the method <code>getMatchColumn</code> is called.
     *
     * @param columnName the name of the column into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds
     */
    public void setMatchColumn(String columnName) throws SQLException {
        // validate, if col is ok to be set
        if(columnName.equals(null) || ((columnName = columnName.trim()) == "" )) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.matchcols2").toString());
        } else {
            // set strMatchColumn
            strMatchColumns.set(0, columnName);
            //iMatchColumn = -1;
        }
    }

    /**
     * Unsets the designated parameter to the given <code>int</code>
     * object.  This was set using <code>setMatchColumn</code>
     * as the column which will form the basis of the join.
     * <P>
     * The parameter value unset by this method should be same
     * as was set.
     *
     * @param columnIdx the index into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds or if the columnIdx is
     *  not the same as set using <code>setMatchColumn(int)</code>
     */
    public void unsetMatchColumn(int columnIdx) throws SQLException {
        // check if we are unsetting the SAME column
        if(! iMatchColumns.get(0).equals(new Integer(columnIdx) )  ) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.unsetmatch").toString());
        } else if(strMatchColumns.get(0) != null) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.usecolname").toString());
        } else {
                // that is, we are unsetting it.
               iMatchColumns.set(0, new Integer(-1));
        }
    }

    /**
     * Unsets the designated parameter to the given <code>String</code>
     * object.  This was set using <code>setMatchColumn</code>
     * as the column which will form the basis of the join.
     * <P>
     * The parameter value unset by this method should be same
     * as was set.
     *
     * @param columnName the index into this rowset
     *        object's internal representation of parameter values
     * @throws SQLException if an error occurs or the
     *  parameter index is out of bounds or if the columnName is
     *  not the same as set using <code>setMatchColumn(String)</code>
     *
     */
    public void unsetMatchColumn(String columnName) throws SQLException {
        // check if we are unsetting the same column
        columnName = columnName.trim();

        if(!((strMatchColumns.get(0)).equals(columnName))) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.unsetmatch").toString());
        } else if( ((Integer)(iMatchColumns.get(0))).intValue() > 0) {
            throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.usecolid").toString());
        } else {
            strMatchColumns.set(0, null);   // that is, we are unsetting it.
        }
    }

    /**
     * Retrieves the <code>DatabaseMetaData</code> associated with
     * the connection handle associated this this
     * <code>JdbcRowSet</code> object.
     *
     * @return the <code>DatabaseMetadata</code> associated
     *  with the rowset's connection.
     * @throws SQLException if a database access error occurs
     */
    public DatabaseMetaData getDatabaseMetaData() throws SQLException {
        Connection con = connect();
        return con.getMetaData();
    }

    /**
     * Retrieves the <code>ParameterMetaData</code> associated with
     * the connection handle associated this this
     * <code>JdbcRowSet</code> object.
     *
     * @return the <code>ParameterMetadata</code> associated
     *  with the rowset's connection.
     * @throws SQLException if a database access error occurs
     */
    public ParameterMetaData getParameterMetaData() throws SQLException {
        prepare();
        return (ps.getParameterMetaData());
    }

    /**
     * Commits all updates in this <code>JdbcRowSet</code> object by
     * wrapping the internal <code>Connection</code> object and calling
     * its <code>commit</code> method.
     * This method sets this <code>JdbcRowSet</code> object's private field
     * <code>rs</code> to <code>null</code> after saving its value to another
     * object, but only if the <code>ResultSet</code>
     * constant <code>HOLD_CURSORS_OVER_COMMIT</code> has not been set.
     * (The field <code>rs</code> is this <code>JdbcRowSet</code> object's
     * <code>ResultSet</code> object.)
     *
     * @throws SQLException if autoCommit is set to true or if a database
     * access error occurs
     */
    public void commit() throws SQLException {
      conn.commit();

      // Checking the holadbility value and making the result set handle null
      // Added as per Rave requirements

      if( conn.getHoldability() != HOLD_CURSORS_OVER_COMMIT) {
         ResultSet oldVal = rs;
         rs = null;
         // propertyChangeSupport.firePropertyChange("ResultSet",oldVal,rs);
      }
    }

    /**
     * Sets auto-commit on the internal <code>Connection</code> object with this
     * <code>JdbcRowSet</code>
     *
     * @throws SQLException if a database access error occurs
     */
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        // The connection object should be there
        // in order to commit the connection handle on or off.

        if(conn != null) {
           conn.setAutoCommit(autoCommit);
        } else {
           // Coming here means the connection object is null.
           // So generate a connection handle internally, since
           // a JdbcRowSet is always connected to a db, it is fine
           // to get a handle to the connection.

           // Get hold of a connection handle
           // and change the autcommit as passesd.
           conn = connect();

           // After setting the below the conn.getAutoCommit()
           // should return the same value.
           conn.setAutoCommit(autoCommit);

        }
    }

    /**
     * Returns the auto-commit status with this <code>JdbcRowSet</code>.
     *
     * @return true if auto commit is true; false otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean getAutoCommit() throws SQLException {
        return conn.getAutoCommit();
    }

    /**
     * Rolls back all the updates in this <code>JdbcRowSet</code> object by
     * wrapping the internal <code>Connection</code> object and calling its
     * <code>rollback</code> method.
     * This method sets this <code>JdbcRowSet</code> object's private field
     * <code>rs</code> to <code>null</code> after saving its value to another object.
     * (The field <code>rs</code> is this <code>JdbcRowSet</code> object's
     * internal <code>ResultSet</code> object.)
     *
     * @throws SQLException if autoCommit is set to true or a database
     * access error occurs
     */
    public void rollback() throws SQLException {
        conn.rollback();

        // Makes the result ste handle null after rollback
        // Added as per Rave requirements

        ResultSet oldVal = rs;
        rs = null;
        // propertyChangeSupport.firePropertyChange("ResultSet", oldVal,rs);
    }


    /**
     * Rollbacks all the updates in the <code>JdbcRowSet</code> back to the
     * last <code>Savepoint</code> transaction marker. Wraps the internal
     * <code>Connection</code> object and call it's rollback method
     *
     * @param s the <code>Savepoint</code> transaction marker to roll the
     * transaction to.
     * @throws SQLException if autoCommit is set to true; or ia a database
     * access error occurs
     */
    public void rollback(Savepoint s) throws SQLException {
        conn.rollback(s);
    }

    // Setting the ResultSet Type and Concurrency
    protected void setParams() throws SQLException {
        if(rs == null) {
           setType(ResultSet.TYPE_SCROLL_INSENSITIVE);
           setConcurrency(ResultSet.CONCUR_UPDATABLE);
        }
        else {
            setType(rs.getType());
            setConcurrency(rs.getConcurrency());
        }
    }


    // Checking ResultSet Type and Concurrency
    private void checkTypeConcurrency() throws SQLException {
        if(rs.getType() == TYPE_FORWARD_ONLY ||
           rs.getConcurrency() == CONCUR_READ_ONLY) {
              throw new SQLException(resBundle.handleGetObject("jdbcrowsetimpl.resnotupd").toString());
         }
    }

     // Returns a Connection Handle
    //  Added as per Rave requirements

    /**
     * Gets this <code>JdbcRowSet</code> object's Connection property
     *
     *
     * @return the <code>Connection</code> object associated with this rowset;
     */

    protected Connection getConnection() {
       return conn;
    }

    // Sets the connection handle with the parameter
    // Added as per rave requirements

    /**
     * Sets this <code>JdbcRowSet</code> object's connection property
     * to the given <code>Connection</code> object.
     *
     * @param connection the <code>Connection</code> object.
     */

    protected void setConnection(Connection connection) {
       conn = connection;
    }

    // Returns a PreparedStatement Handle
    // Added as per Rave requirements

    /**
     * Gets this <code>JdbcRowSet</code> object's PreparedStatement property
     *
     *
     * @return the <code>PreparedStatement</code> object associated with this rowset;
     */

    protected PreparedStatement getPreparedStatement() {
       return ps;
    }

    //Sets the prepared statement handle to the parameter
    // Added as per Rave requirements

    /**
     * Sets this <code>JdbcRowSet</code> object's preparedtsatement property
     * to the given <code>PreparedStatemennt</code> object.
     *
     * @param preparedStatement the <code>PreparedStatement</code> object
     *
     */
    protected void setPreparedStatement(PreparedStatement preparedStatement) {
       ps = preparedStatement;
    }

    // Returns a ResultSet handle
    // Added as per Rave requirements

    /**
     * Gets this <code>JdbcRowSet</code> object's ResultSet property
     *
     *
     * @return the <code>ResultSet</code> object associated with this rowset;
     */

    protected ResultSet getResultSet() throws SQLException {

       checkState();

       return rs;
    }

    // Sets the result set handle to the parameter
    // Added as per Rave requirements

    /**
     * Sets this <code>JdbcRowSet</code> object's resultset property
     * to the given <code>ResultSet</code> object.
     *
     * @param resultSet the <code>ResultSet</code> object
     *
     */
    protected void setResultSet(ResultSet resultSet) {
       rs = resultSet;
    }


    // Over riding the setCommand from BaseRowSet for
    // firing the propertyChangeSupport Event for
    // Rave requirements when this property's value
    // changes.

    /**
     * Sets this <code>JdbcRowSet</code> object's <code>command</code> property to
     * the given <code>String</code> object and clears the parameters, if any,
     * that were set for the previous command. In addition,
     * if the <code>command</code> property has previously been set to a
     * non-null value and it is
     * different from the <code>String</code> object supplied,
     * this method sets this <code>JdbcRowSet</code> object's private fields
     * <code>ps</code> and <code>rs</code> to <code>null</code>.
     * (The field <code>ps</code> is its <code>PreparedStatement</code> object, and
     * the field <code>rs</code> is its <code>ResultSet</code> object.)
     * <P>
     * The <code>command</code> property may not be needed if the <code>RowSet</code>
     * object gets its data from a source that does not support commands,
     * such as a spreadsheet or other tabular file.
     * Thus, this property is optional and may be <code>null</code>.
     *
     * @param command a <code>String</code> object containing an SQL query
     *            that will be set as this <code>RowSet</code> object's command
     *            property; may be <code>null</code> but may not be an empty string
     * @throws SQLException if an empty string is provided as the command value
     * @see #getCommand
     */
    public void setCommand(String command) throws SQLException {
       String oldVal;

       if (getCommand() != null) {
          if(!getCommand().equals(command)) {
             oldVal = getCommand();
             super.setCommand(command);
             ps = null;
             rs = null;
             propertyChangeSupport.firePropertyChange("command", oldVal,command);
          }
       }
       else {
          super.setCommand(command);
          propertyChangeSupport.firePropertyChange("command", null,command);
       }
    }

    // Over riding the setDataSourceName from BaseRowSet for
    // firing the propertyChangeSupport Event for
    // Rave requirements when this property's values
    // changes.

    /**
     * Sets the <code>dataSourceName</code> property for this <code>JdbcRowSet</code>
     * object to the given logical name and sets this <code>JdbcRowSet</code> object's
     * Url property to <code>null</code>. In addition, if the <code>dataSourceName</code>
     * property has previously been set and is different from the one supplied,
     * this method sets this <code>JdbcRowSet</code> object's private fields
     * <code>ps</code>, <code>rs</code>, and <code>conn</code> to <code>null</code>.
     * (The field <code>ps</code> is its <code>PreparedStatement</code> object,
     * the field <code>rs</code> is its <code>ResultSet</code> object, and
     * the field <code>conn</code> is its <code>Connection</code> object.)
     * <P>
     * The name supplied to this method must have been bound to a
     * <code>DataSource</code> object in a JNDI naming service so that an
     * application can do a lookup using that name to retrieve the
     * <code>DataSource</code> object bound to it. The <code>DataSource</code>
     * object can then be used to establish a connection to the data source it
     * represents.
     * <P>
     * Users should set either the Url property or the dataSourceName property.
     * If both properties are set, the driver will use the property set most recently.
     *
     * @param dsName a <code>String</code> object with the name that can be supplied
     *        to a naming service based on JNDI technology to retrieve the
     *        <code>DataSource</code> object that can be used to get a connection;
     *        may be <code>null</code>
     * @throws SQLException if there is a problem setting the
     *          <code>dataSourceName</code> property
     * @see #getDataSourceName
     */
    public void setDataSourceName(String dsName) throws SQLException{
       String oldVal;

       if(getDataSourceName() != null) {
          if(!getDataSourceName().equals(dsName)) {
             oldVal = getDataSourceName();
             super.setDataSourceName(dsName);
             conn = null;
             ps = null;
             rs = null;
             propertyChangeSupport.firePropertyChange("dataSourceName",oldVal,dsName);
          }
       }
       else {
          super.setDataSourceName(dsName);
          propertyChangeSupport.firePropertyChange("dataSourceName",null,dsName);
       }
    }

    // Over riding the setUrl from BaseRowSet for
    // firing the propertyChangeSupport Event for
    // Rave requirements when this property's values
    // changes.

    /**
     * Sets the Url property for this <code>JdbcRowSet</code> object
     * to the given <code>String</code> object and sets the dataSource name
     * property to <code>null</code>. In addition, if the Url property has
     * previously been set to a non <code>null</code> value and its value
     * is different from the value to be set,
     * this method sets this <code>JdbcRowSet</code> object's private fields
     * <code>ps</code>, <code>rs</code>, and <code>conn</code> to <code>null</code>.
     * (The field <code>ps</code> is its <code>PreparedStatement</code> object,
     * the field <code>rs</code> is its <code>ResultSet</code> object, and
     * the field <code>conn</code> is its <code>Connection</code> object.)
     * <P>
     * The Url property is a JDBC URL that is used when
     * the connection is created using a JDBC technology-enabled driver
     * ("JDBC driver") and the <code>DriverManager</code>.
     * The correct JDBC URL for the specific driver to be used can be found
     * in the driver documentation.  Although there are guidelines for for how
     * a JDBC URL is formed,
     * a driver vendor can specify any <code>String</code> object except
     * one with a length of <code>0</code> (an empty string).
     * <P>
     * Setting the Url property is optional if connections are established using
     * a <code>DataSource</code> object instead of the <code>DriverManager</code>.
     * The driver will use either the URL property or the
     * dataSourceName property to create a connection, whichever was
     * specified most recently. If an application uses a JDBC URL, it
     * must load a JDBC driver that accepts the JDBC URL before it uses the
     * <code>RowSet</code> object to connect to a database.  The <code>RowSet</code>
     * object will use the URL internally to create a database connection in order
     * to read or write data.
     *
     * @param url a <code>String</code> object that contains the JDBC URL
     *            that will be used to establish the connection to a database for this
     *            <code>RowSet</code> object; may be <code>null</code> but must not
     *            be an empty string
     * @throws SQLException if an error occurs setting the Url property or the
     *         parameter supplied is a string with a length of <code>0</code> (an
     *         empty string)
     * @see #getUrl
     */

    public void setUrl(String url) throws SQLException {
       String oldVal;

       if(getUrl() != null) {
          if(!getUrl().equals(url)) {
             oldVal = getUrl();
             super.setUrl(url);
             conn = null;
             ps = null;
             rs = null;
             propertyChangeSupport.firePropertyChange("url", oldVal, url);
          }
       }
       else {
          super.setUrl(url);
          propertyChangeSupport.firePropertyChange("url", null, url);
       }
    }

    // Over riding the setUsername from BaseRowSet for
    // firing the propertyChangeSupport Event for
    // Rave requirements when this property's values
    // changes.

     /**
     * Sets the username property for this <code>JdbcRowSet</code> object
     * to the given user name. Because it
     * is not serialized, the username property is set at run time before
     * calling the method <code>execute</code>. In addition,
     * if the <code>username</code> property is already set with a
     * non-null value and that value is different from the <code>String</code>
     * object to be set,
     * this method sets this <code>JdbcRowSet</code> object's private fields
     * <code>ps</code>, <code>rs</code>, and <code>conn</code> to <code>null</code>.
     * (The field <code>ps</code> is its <code>PreparedStatement</code> object,
     * <code>rs</code> is its <code>ResultSet</code> object, and
     * <code>conn</code> is its <code>Connection</code> object.)
     * Setting these fields to <code>null</code> ensures that only current
     * values will be used.
     *
     * @param uname the <code>String</code> object containing the user name that
     *     is supplied to the data source to create a connection. It may be null.
     * @see #getUsername
     */
    public void setUsername(String uname) {
       String oldVal;

       if( getUsername() != null) {
          if(!getUsername().equals(uname)) {
             oldVal = getUsername();
             super.setUsername(uname);
             conn = null;
             ps = null;
             rs = null;
             propertyChangeSupport.firePropertyChange("username",oldVal,uname);
          }
       }
       else{
          super.setUsername(uname);
          propertyChangeSupport.firePropertyChange("username",null,uname);
       }
    }

    // Over riding the setPassword from BaseRowSet for
    // firing the propertyChangeSupport Event for
    // Rave requirements when this property's values
    // changes.

     /**
     * Sets the password property for this <code>JdbcRowSet</code> object
     * to the given <code>String</code> object. Because it
     * is not serialized, the password property is set at run time before
     * calling the method <code>execute</code>. Its default valus is
     * <code>null</code>. In addition,
     * if the <code>password</code> property is already set with a
     * non-null value and that value is different from the one being set,
     * this method sets this <code>JdbcRowSet</code> object's private fields
     * <code>ps</code>, <code>rs</code>, and <code>conn</code> to <code>null</code>.
     * (The field <code>ps</code> is its <code>PreparedStatement</code> object,
     * <code>rs</code> is its <code>ResultSet</code> object, and
     * <code>conn</code> is its <code>Connection</code> object.)
     * Setting these fields to <code>null</code> ensures that only current
     * values will be used.
     *
     * @param password the <code>String</code> object that represents the password
     *     that must be supplied to the database to create a connection
     */
    public void setPassword(String password) {
       String oldVal;

       if ( getPassword() != null) {
          if(!getPassword().equals(password)) {
             oldVal = getPassword();
             super.setPassword(password);
             conn = null;
             ps = null;
             rs = null;
             propertyChangeSupport.firePropertyChange("password",oldVal,password);
          }
       }
       else{
          super.setPassword(password);
          propertyChangeSupport.firePropertyChange("password",null,password);
       }
    }

    /**
     * Sets the type for this <code>RowSet</code> object to the specified type.
     * The default type is <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>.
     *
     * @param type one of the following constants:
     *             <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *             <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *             <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @throws SQLException if the parameter supplied is not one of the
     *         following constants:
     *          <code>ResultSet.TYPE_FORWARD_ONLY</code> or
     *          <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>
     *          <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @see #getConcurrency
     * @see #getType
     */

    public void setType(int type) throws SQLException {

       int oldVal;

       try {
          oldVal = getType();
        }catch(SQLException ex) {
           oldVal = 0;
        }

       if(oldVal != type) {
           super.setType(type);
           propertyChangeSupport.firePropertyChange("type",oldVal,type);
       }

    }

    /**
     * Sets the concurrency for this <code>RowSet</code> object to
     * the specified concurrency. The default concurrency for any <code>RowSet</code>
     * object (connected or disconnected) is <code>ResultSet.CONCUR_UPDATABLE</code>,
     * but this method may be called at any time to change the concurrency.
     *
     * @param concur one of the following constants:
     *                    <code>ResultSet.CONCUR_READ_ONLY</code> or
     *                    <code>ResultSet.CONCUR_UPDATABLE</code>
     * @throws SQLException if the parameter supplied is not one of the
     *         following constants:
     *          <code>ResultSet.CONCUR_UPDATABLE</code> or
     *          <code>ResultSet.CONCUR_READ_ONLY</code>
     * @see #getConcurrency
     * @see #isReadOnly
     */
    public void setConcurrency(int concur) throws SQLException {

       int oldVal;

       try {
          oldVal = getConcurrency();
        }catch(NullPointerException ex) {
           oldVal = 0;
        }

       if(oldVal != concur) {
           super.setConcurrency(concur);
           propertyChangeSupport.firePropertyChange("concurrency",oldVal,concur);
       }

    }

    /**
     * Sets the transaction isolation property for this JDBC <code>RowSet</code> object to the given
     * constant. The DBMS will use this transaction isolation level for
     * transactions if it can.
     * <p>
     * For <code>RowSet</code> implementations such as
     * the <code>CachedRowSet</code> that operate in a disconnected environment,
     * the <code>SyncProvider</code> object being used
     * offers complementary locking and data integrity options. The
     * options described below are pertinent only to connected <code>RowSet</code>
     * objects (<code>JdbcRowSet</code> objects).
     *
     * @param transIso one of the following constants, listed in ascending order:
     *              <code>Connection.TRANSACTION_NONE</code>,
     *              <code>Connection.TRANSACTION_READ_UNCOMMITTED</code>,
     *              <code>Connection.TRANSACTION_READ_COMMITTED</code>,
     *              <code>Connection.TRANSACTION_REPEATABLE_READ</code>, or
     *              <code>Connection.TRANSACTION_SERIALIZABLE</code>
     * @throws SQLException if the given parameter is not one of the Connection
     *          constants
     * @see javax.sql.rowset.spi.SyncFactory
     * @see javax.sql.rowset.spi.SyncProvider
     * @see #getTransactionIsolation
     */
    public void setTransactionIsolation(int transIso) throws SQLException {

       int oldVal;

       try {
          oldVal = getTransactionIsolation();
        }catch(NullPointerException ex) {
           oldVal = 0;
        }

       if(oldVal != transIso) {
           super.setTransactionIsolation(transIso);
           propertyChangeSupport.firePropertyChange("transactionIsolation",oldVal,transIso);
       }

    }

    /**
     * Sets the maximum number of rows that this <code>RowSet</code> object may contain to
     * the given number. If this limit is exceeded, the excess rows are
     * silently dropped.
     *
     * @param mRows an <code>int</code> indicating the current maximum number
     *     of rows; zero means that there is no limit
     * @throws SQLException if an error occurs internally setting the
     *     maximum limit on the number of rows that a JDBC <code>RowSet</code> object
     *     can contain; or if <i>max</i> is less than <code>0</code>; or
     *     if <i>max</i> is less than the <code>fetchSize</code> of the
     *     <code>RowSet</code>
     */
    public void setMaxRows(int mRows) throws SQLException {

       int oldVal;

       try {
          oldVal = getMaxRows();
        }catch(NullPointerException ex) {
           oldVal = 0;
        }

       if(oldVal != mRows) {
           super.setMaxRows(mRows);
           propertyChangeSupport.firePropertyChange("maxRows",oldVal,mRows);
       }

    }

    /**
     * Retrieves the value of the designated <code>SQL XML</code> parameter as a
     * <code>SQLXML</code> object in the Java programming language.
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a SQLXML object that maps an SQL XML value
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the value of the designated <code>SQL XML</code> parameter as a
     * <code>SQLXML</code> object in the Java programming language.
     * @param colName the name of the column from which to retrieve the value
     * @return a SQLXML object that maps an SQL XML value
     * @throws SQLException if a database access error occurs
     */
    public SQLXML getSQLXML(String colName) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the value of the designated column in the current row of this
     * <code>ResultSet</code> object as a java.sql.RowId object in the Java
     * programming language.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @return the column value if the value is a SQL <code>NULL</code> the
     *     value returned is <code>null</code>
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the value of the designated column in the current row of this
     * <code>ResultSet</code> object as a java.sql.RowId object in the Java
     * programming language.
     *
     * @param columnName the name of the column
     * @return the column value if the value is a SQL <code>NULL</code> the
     *     value returned is <code>null</code>
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public RowId getRowId(String columnName) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow<code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param x the column value
     * @throws SQLException if a database access occurs
     * @since 6.0
     */
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow<code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnName the name of the column
     * @param x the column value
     * @throws SQLException if a database access occurs
     * @since 6.0
     */
    public void updateRowId(String columnName, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the holdability of this ResultSet object
     * @return  either ResultSet.HOLD_CURSORS_OVER_COMMIT or ResultSet.CLOSE_CURSORS_AT_COMMIT
     * @throws SQLException if a database error occurs
     * @since 6.0
     */
    public int getHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves whether this ResultSet object has been closed. A ResultSet is closed if the
     * method close has been called on it, or if it is automatically closed.
     * @return true if this ResultSet object is closed; false if it is still open
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public boolean isClosed() throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * This method is used for updating columns that support National Character sets.
     * It can be used for updating NCHAR,NVARCHAR and LONGNVARCHAR columns.
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nString the value for the column to be updated
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * This method is used for updating columns that support National Character sets.
     * It can be used for updating NCHAR,NVARCHAR and LONGNVARCHAR columns.
     * @param columnName name of the Column
     * @param nString the value for the column to be updated
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public void updateNString(String columnName, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


    /*o
     * This method is used for updating SQL <code>NCLOB</code>  type that maps
     * to <code>java.sql.Types.NCLOB</code>
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nClob the value for the column to be updated
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * This method is used for updating SQL <code>NCLOB</code>  type that maps
     * to <code>java.sql.Types.NCLOB</code>
     * @param columnName name of the column
     * @param nClob the value for the column to be updated
     * @throws SQLException if a database access error occurs
     * @since 6.0
     */
    public void updateNClob(String columnName, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>NClob</code> object
     * in the Java programming language.
     *
     * @param i the first column is 1, the second is 2, ...
     * @return a <code>NClob</code> object representing the SQL
     *         <code>NCLOB</code> value in the specified column
     * @exception SQLException if a database access error occurs
     * @since 6.0
     */
    public NClob getNClob(int i) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


  /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a <code>NClob</code> object
     * in the Java programming language.
     *
     * @param colName the name of the column from which to retrieve the value
     * @return a <code>NClob</code> object representing the SQL <code>NCLOB</code>
     * value in the specified column
     * @exception SQLException if a database access error occurs
     * @since 6.0
     */
    public NClob getNClob(String colName) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    public <T> T unwrap(java.lang.Class<T> iface) throws java.sql.SQLException{
        return null;
    }

    public boolean isWrapperFor(Class<?> interfaces) throws SQLException {
        return false;
    }

    /**
      * Sets the designated parameter to the given <code>java.sql.SQLXML</code> object. The driver converts this to an
      * SQL <code>XML</code> value when it sends it to the database.
      * @param parameterIndex index of the first parameter is 1, the second is 2, ...
      * @param xmlObject a <code>SQLXML</code> object that maps an SQL <code>XML</code> value
      * @throws SQLException if a database access error occurs
      * @since 1.6
      */
     public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }

    /**
     * Sets the designated parameter to the given <code>java.sql.SQLXML</code> object. The driver converts this to an
     * <code>SQL XML</code> value when it sends it to the database.
     * @param parameterName the name of the parameter
     * @param xmlObject a <code>SQLXML</code> object that maps an <code>SQL XML</code> value
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }

    /**
     * Sets the designated parameter to the given <code>java.sql.RowId</code> object. The
     * driver converts this to a SQL <code>ROWID</code> value when it sends it
     * to the database
     *
     * @param parameterIndex the first parameter is 1, the second is 2, ...
     * @param x the parameter value
     * @throws SQLException if a database access error occurs
     *
     * @since 1.6
     */
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }

    /**
    * Sets the designated parameter to the given <code>java.sql.RowId</code> object. The
    * driver converts this to a SQL <code>ROWID</code> when it sends it to the
    * database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @throws SQLException if a database access error occurs
    * @since 1.6
    */
   public void setRowId(String parameterName, RowId x) throws SQLException {
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }


   /**
     * Sets the designated paramter to the given <code>String</code> object.
     * The driver converts this to a SQL <code>NCHAR</code> or
     * <code>NVARCHAR</code> or <code>LONGNVARCHAR</code> value
     * (depending on the argument's
     * size relative to the driver's limits on <code>NVARCHAR</code> values)
     * when it sends it to the database.
     *
     * @param parameterIndex of the first parameter is 1, the second is 2, ...
     * @param value the parameter value
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur ; or if a database access error occurs
     * @since 1.6
     */
     public void setNString(int parameterIndex, String value) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }


   /**
    * Sets the designated parameter in this <code>RowSet</code> object's command
    * to a <code>Reader</code> object. The
    * <code>Reader</code> reads the data till end-of-file is reached. The
    * driver does the necessary conversion from Java character format to
    * the national character set in the database.

    * <P><B>Note:</B> This stream object can either be a standard
    * Java stream object or your own subclass that implements the
    * standard interface.
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setNCharacterStream</code> which takes a length parameter.
    *
    * @param parameterIndex of the first parameter is 1, the second is 2, ...
    * @param value the parameter value
    * @throws SQLException if the driver does not support national
    *         character sets;  if the driver can detect that a data conversion
    *  error could occur ; if a database access error occurs; or
    * this method is called on a closed <code>PreparedStatement</code>
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    * @since 1.6
    */
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

  /**
    * Sets the designated parameter to a <code>java.sql.NClob</code> object. The object
    * implements the <code>java.sql.NClob</code> interface. This <code>NClob</code>
    * object maps to a SQL <code>NCLOB</code>.
    * @param parameterName the name of the column to be set
    * @param value the parameter value
    * @throws SQLException if the driver does not support national
    *         character sets;  if the driver can detect that a data conversion
    *  error could occur; or if a database access error occurs
    * @since 1.6
    */
    public void setNClob(String parameterName, NClob value) throws SQLException {
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }


  /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @param columnIndex the first column is 1, the second is 2, ...
     * @exception SQLException if a database access error occurs
     * @since 1.6
     */
    public java.io.Reader getNCharacterStream(int columnIndex) throws SQLException {
       throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }


    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as a
     * <code>java.io.Reader</code> object.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnName the name of the column
     * @return a <code>java.io.Reader</code> object that contains the column
     * value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @exception SQLException if a database access error occurs
     * @since 1.6
     */
    public java.io.Reader getNCharacterStream(String columnName) throws SQLException {
       throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
     }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value.
     * The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     * @param columnIndex the first column is 1, the second 2, ...
     * @param xmlObject the value for the column to be updated
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value.
     * The updater
     * methods are used to update column values in the current row or the insert
     * row. The updater methods do not update the underlying database; instead
     * the <code>updateRow</code> or <code>insertRow</code> methods are called
     * to update the database.
     *
     * @param columnName the name of the column
     * @param xmlObject the column value
     * @throws SQLException if a database access occurs
     * @since 1.6
     */
    public void updateSQLXML(String columnName, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

     /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @exception SQLException if a database access error occurs
     * @since 1.6
     */
    public String getNString(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Retrieves the value of the designated column in the current row
     * of this <code>ResultSet</code> object as
     * a <code>String</code> in the Java programming language.
     * It is intended for use when
     * accessing  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnName the SQL name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the
     * value returned is <code>null</code>
     * @exception SQLException if a database access error occurs
     * @since 1.6
     */
    public String getNString(String columnName) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

     /**
       * Updates the designated column with a character stream value, which will
       * have the specified number of bytes. The driver does the necessary conversion
       * from Java character format to the national character set in the database.
       * It is intended for use when updating NCHAR,NVARCHAR and LONGNVARCHAR columns.
       * The updater methods are used to update column values in the current row or
       * the insert row. The updater methods do not update the underlying database;
       * instead the updateRow or insertRow methods are called to update the database.
       *
       * @param columnIndex - the first column is 1, the second is 2, ...
       * @param x - the new column value
       * @param length - the length of the stream
       * @exception SQLException if a database access error occurs
       * @since 1.6
       */
       public void updateNCharacterStream(int columnIndex,
                            java.io.Reader x,
                            long length)
                            throws SQLException {
          throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
       }

     /**
       * Updates the designated column with a character stream value, which will
       * have the specified number of bytes. The driver does the necessary conversion
       * from Java character format to the national character set in the database.
       * It is intended for use when updating NCHAR,NVARCHAR and LONGNVARCHAR columns.
       * The updater methods are used to update column values in the current row or
       * the insert row. The updater methods do not update the underlying database;
       * instead the updateRow or insertRow methods are called to update the database.
       *
       * @param columnName - name of the Column
       * @param x - the new column value
       * @param length - the length of the stream
       * @exception SQLException if a database access error occurs
       * @since 1.6
       */
       public void updateNCharacterStream(String columnName,
                            java.io.Reader x,
                            long length)
                            throws SQLException {
          throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
       }

    /**
     * Updates the designated column with a character stream value.   The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNCharacterStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNCharacterStream(int columnIndex,
                             java.io.Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a character stream value.  The
     * driver does the necessary conversion from Java character format to
     * the national character set in the database.
     * It is intended for use when
     * updating  <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns.
     *
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNCharacterStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param reader the <code>java.io.Reader</code> object containing
     *        the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
      * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNCharacterStream(String columnLabel,
                             java.io.Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given input stream, which
     * will have the specified number of bytes.
     * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.InputStream</code>. Data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from ASCII to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter
     * value to.
     * @param length the number of bytes in the parameter data.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given input stream, which
     * will have the specified number of bytes.
     * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.InputStream</code>. Data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from ASCII to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param inputStream An object that contains the data to set the parameter
     * value to.
     * @param length the number of bytes in the parameter data.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given input stream.
     * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.InputStream</code>. Data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from ASCII to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     *
     *  <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBlob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter
     * value to.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given input stream.
     * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.InputStream</code>. Data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from ASCII to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     *   <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBlob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param inputStream An object that contains the data to set the parameter
     * value to.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateClob(int columnIndex,  Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateClob(String columnLabel,  Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     *   <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateClob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateClob(int columnIndex,  Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     *  <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateClob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateClob(String columnLabel,  Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

   /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur; this method is called on a closed result set,
     * if a database access error occurs or
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNClob(int columnIndex,  Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object, which is the given number of characters long.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur; this method is called on a closed result set;
     *  if a database access error occurs or
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNClob(String columnLabel,  Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNClob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur; this method is called on a closed result set,
     * if a database access error occurs or
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNClob(int columnIndex,  Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column using the given <code>Reader</code>
     * object.
     * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
     * parameter, it may be more practical to send it via a
     * <code>java.io.Reader</code> object. The data will be read from the stream
     * as needed until end-of-file is reached.  The JDBC driver will
     * do any necessary conversion from UNICODE to the database char format.
     *
     * <P><B>Note:</B> This stream object can either be a standard
     * Java stream object or your own subclass that implements the
     * standard interface.
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateNClob</code> which takes a length parameter.
     * <p>
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur; this method is called on a closed result set;
     *  if a database access error occurs or
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateNClob(String columnLabel,  Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


        /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateAsciiStream(int columnIndex,
                           java.io.InputStream x,
                           long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBinaryStream(int columnIndex,
                            java.io.InputStream x,
                            long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateCharacterStream(int columnIndex,
                             java.io.Reader x,
                             long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

     /**
     * Updates the designated column with an ascii stream value, which will have
     * the specified number of bytes..
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateAsciiStream(String columnLabel,
                           java.io.InputStream x,
                           long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateAsciiStream(int columnIndex,
                           java.io.InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with an ascii stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateAsciiStream(String columnLabel,
                           java.io.InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


    /**
     * Updates the designated column with a binary stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param x the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBinaryStream(String columnLabel,
                            java.io.InputStream x,
                            long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a binary stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBinaryStream(int columnIndex,
                            java.io.InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


    /**
     * Updates the designated column with a binary stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateBinaryStream(String columnLabel,
                            java.io.InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


    /**
     * Updates the designated column with a character stream value, which will have
     * the specified number of bytes.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the name of the column
     * @param reader the <code>java.io.Reader</code> object containing
     *        the new column value
     * @param length the length of the stream
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateCharacterStream(String columnLabel,
                             java.io.Reader reader,
                             long length) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a character stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param x the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateCharacterStream(int columnIndex,
                             java.io.Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }

    /**
     * Updates the designated column with a character stream value.
     * The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
     * it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the la
bel is the name of the column
     * @param reader the <code>java.io.Reader</code> object containing
     *        the new column value
     * @exception SQLException if a database access error occurs,
     * the result set concurrency is <code>CONCUR_READ_ONLY</code>
     * or this method is called on a closed result set
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since 1.6
     */
    public void updateCharacterStream(String columnLabel,
                             java.io.Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


     /**
  * Sets the designated parameter to the given <code>java.net.URL</code> value.
  * The driver converts this to an SQL <code>DATALINK</code> value
  * when it sends it to the database.
  *
  * @param parameterIndex the first parameter is 1, the second is 2, ...
  * @param x the <code>java.net.URL</code> object to be set
  * @exception SQLException if a database access error occurs or
  * this method is called on a closed <code>PreparedStatement</code>
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
  * @since 1.4
  */
  public void setURL(int parameterIndex, java.net.URL x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


   /**
  * Sets the designated parameter to a <code>Reader</code> object.
  * This method differs from the <code>setCharacterStream (int, Reader)</code> method
  * because it informs the driver that the parameter value should be sent to
  * the server as a <code>NCLOB</code>.  When the <code>setCharacterStream</code> method is used, the
  * driver may have to do extra work to determine whether the parameter
  * data should be sent to the server as a <code>LONGNVARCHAR</code> or a <code>NCLOB</code>
  * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
  * it might be more efficient to use a version of
  * <code>setNClob</code> which takes a length parameter.
  *
  * @param parameterIndex index of the first parameter is 1, the second is 2, ...
  * @param reader An object that contains the data to set the parameter value to.
  * @throws SQLException if parameterIndex does not correspond to a parameter
  * marker in the SQL statement;
  * if the driver does not support national character sets;
  * if the driver can detect that a data conversion
  *  error could occur;  if a database access error occurs or
  * this method is called on a closed <code>PreparedStatement</code>
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
  *
  * @since 1.6
  */
  public void setNClob(int parameterIndex, Reader reader)
    throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

   /**
  * Sets the designated parameter to a <code>Reader</code> object.  The <code>reader</code> must contain the number
             * of characters specified by length otherwise a <code>SQLException</code> will be
            * generated when the <code>CallableStatement</code> is executed.
            * This method differs from the <code>setCharacterStream (int, Reader, int)</code> method
            * because it informs the driver that the parameter value should be sent to
            * the server as a <code>NCLOB</code>.  When the <code>setCharacterStream</code> method is used, the
            * driver may have to do extra work to determine whether the parameter
            * data should be send to the server as a <code>LONGNVARCHAR</code> or a <code>NCLOB</code>
            *
            * @param parameterName the name of the parameter to be set
            * @param reader An object that contains the data to set the parameter value to.
            * @param length the number of characters in the parameter data.
            * @throws SQLException if parameterIndex does not correspond to a parameter
            * marker in the SQL statement; if the length specified is less than zero;
            * if the driver does not support national
            *         character sets;  if the driver can detect that a data conversion
            *  error could occur; if a database access error occurs or
            * this method is called on a closed <code>CallableStatement</code>
            * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
            * this method
            * @since 1.6
            */
            public void setNClob(String parameterName, Reader reader, long length)
    throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
  * Sets the designated parameter to a <code>Reader</code> object.
  * This method differs from the <code>setCharacterStream (int, Reader)</code> method
  * because it informs the driver that the parameter value should be sent to
  * the server as a <code>NCLOB</code>.  When the <code>setCharacterStream</code> method is used, the
  * driver may have to do extra work to determine whether the parameter
  * data should be send to the server as a <code>LONGNVARCHAR</code> or a <code>NCLOB</code>
  * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
  * it might be more efficient to use a version of
  * <code>setNClob</code> which takes a length parameter.
  *
  * @param parameterName the name of the parameter
  * @param reader An object that contains the data to set the parameter value to.
  * @throws SQLException if the driver does not support national character sets;
  * if the driver can detect that a data conversion
  *  error could occur;  if a database access error occurs or
  * this method is called on a closed <code>CallableStatement</code>
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
  *
  * @since 1.6
  */
  public void setNClob(String parameterName, Reader reader)
    throws SQLException{
             throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


   /**
     ** of characters specified by length otherwise a <code>SQLException</code> will becontain  the number
     * generated when the <code>PreparedStatement</code> is executed.
     * This method differs from the <code>setCharacterStream (int, Reader, int)</code> method
     * because it informs the driver that the parameter value should be sent to
     * the server as a <code>NCLOB</code>.  When the <code>setCharacterStream</code> method is used, the
     * driver may have to do extra work to determine whether the parameter
     * data should be sent to the server as a <code>LONGNVARCHAR</code> or a <code>NCLOB</code>
     * @param parameterIndex index of the first parameter is 1, the second is 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws SQLException if parameterIndex does not correspond to a parameter
     * marker in the SQL statement; if the length specified is less than zero;
     * if the driver does not support national character sets;
     * if the driver can detect that a data conversion
     *  error could occur;  if a database access error occurs or
     * this method is called on a closed <code>PreparedStatement</code>
     * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
     *
     * @since 1.6
     */
     public void setNClob(int parameterIndex, Reader reader, long length)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


    /**
     * Sets the designated parameter to a <code>java.sql.NClob</code> object. The driver converts this to
a
     * SQL <code>NCLOB</code> value when it sends it to the database.
     * @param parameterIndex of the first parameter is 1, the second is 2, ...
     * @param value the parameter value
     * @throws SQLException if the driver does not support national
     *         character sets;  if the driver can detect that a data conversion
     *  error could occur ; or if a database access error occurs
     * @since 1.6
     */
     public void setNClob(int parameterIndex, NClob value) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
  * Sets the designated paramter to the given <code>String</code> object.
  * The driver converts this to a SQL <code>NCHAR</code> or
  * <code>NVARCHAR</code> or <code>LONGNVARCHAR</code>
  * @param parameterName the name of the column to be set
  * @param value the parameter value
  * @throws SQLException if the driver does not support national
  *         character sets;  if the driver can detect that a data conversion
  *  error could occur; or if a database access error occurs
  * @since 1.6
  */
 public void setNString(String parameterName, String value)
         throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
  * Sets the designated parameter to a <code>Reader</code> object. The
  * <code>Reader</code> reads the data till end-of-file is reached. The
  * driver does the necessary conversion from Java character format to
  * the national character set in the database.
  * @param parameterIndex of the first parameter is 1, the second is 2, ...
  * @param value the parameter value
  * @param length the number of characters in the parameter data.
  * @throws SQLException if the driver does not support national
  *         character sets;  if the driver can detect that a data conversion
  *  error could occur ; or if a database access error occurs
  * @since 1.6
  */
  public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }



 /**
  * Sets the designated parameter to a <code>Reader</code> object. The
  * <code>Reader</code> reads the data till end-of-file is reached. The
  * driver does the necessary conversion from Java character format to
  * the national character set in the database.
  * @param parameterName the name of the column to be set
  * @param value the parameter value
  * @param length the number of characters in the parameter data.
  * @throws SQLException if the driver does not support national
  *         character sets;  if the driver can detect that a data conversion
  *  error could occur; or if a database access error occurs
  * @since 1.6
  */
 public void setNCharacterStream(String parameterName, Reader value, long length)
         throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

  /**
  * Sets the designated parameter to a <code>Reader</code> object. The
  * <code>Reader</code> reads the data till end-of-file is reached. The
  * driver does the necessary conversion from Java character format to
  * the national character set in the database.

  * <P><B>Note:</B> This stream object can either be a standard
  * Java stream object or your own subclass that implements the
  * standard interface.
  * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
  * it might be more efficient to use a version of
  * <code>setNCharacterStream</code> which takes a length parameter.
  *
  * @param parameterName the name of the parameter
  * @param value the parameter value
  * @throws SQLException if the driver does not support national
  *         character sets;  if the driver can detect that a data conversion
  *  error could occur ; if a database access error occurs; or
  * this method is called on a closed <code>CallableStatement</code>
  * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
  * @since 1.6
  */
  public void setNCharacterStream(String parameterName, Reader value) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

  /**
    * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value,
    * using the given <code>Calendar</code> object.  The driver uses
    * the <code>Calendar</code> object to construct an SQL <code>TIMESTAMP</code> value,
    * which the driver then sends to the database.  With a
    * a <code>Calendar</code> object, the driver can calculate the timestamp
    * taking into account a custom timezone.  If no
    * <code>Calendar</code> object is specified, the driver uses the default
    * timezone, which is that of the virtual machine running the application.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @param cal the <code>Calendar</code> object the driver will use
    *            to construct the timestamp
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getTimestamp
    * @since 1.4
    */
    public void setTimestamp(String parameterName, java.sql.Timestamp x, Calendar cal)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

    /**
    * Sets the designated parameter to a <code>Reader</code> object.  The <code>reader</code> must contain  the number
               * of characters specified by length otherwise a <code>SQLException</code> will be
               * generated when the <code>CallableStatement</code> is executed.
              * This method differs from the <code>setCharacterStream (int, Reader, int)</code> method
              * because it informs the driver that the parameter value should be sent to
              * the server as a <code>CLOB</code>.  When the <code>setCharacterStream</code> method is used, the
               * driver may have to do extra work to determine whether the parameter
               * data should be send to the server as a <code>LONGVARCHAR</code> or a <code>CLOB</code>
               * @param parameterName the name of the parameter to be set
              * @param reader An object that contains the data to set the parameter value to.
              * @param length the number of characters in the parameter data.
              * @throws SQLException if parameterIndex does not correspond to a parameter
              * marker in the SQL statement; if the length specified is less than zero;
              * a database access error occurs or
              * this method is called on a closed <code>CallableStatement</code>
              * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
              * this method
              *
              * @since 1.6
              */
      public  void setClob(String parameterName, Reader reader, long length)
      throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }



  /**
    * Sets the designated parameter to the given <code>java.sql.Clob</code> object.
    * The driver converts this to an SQL <code>CLOB</code> value when it
    * sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x a <code>Clob</code> object that maps an SQL <code>CLOB</code> value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @since 1.6
    */
    public void setClob (String parameterName, Clob x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to a <code>Reader</code> object.
    * This method differs from the <code>setCharacterStream (int, Reader)</code> method
    * because it informs the driver that the parameter value should be sent to
    * the server as a <code>CLOB</code>.  When the <code>setCharacterStream</code> method is used, the
    * driver may have to do extra work to determine whether the parameter
    * data should be send to the server as a <code>LONGVARCHAR</code> or a <code>CLOB</code>
    *
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setClob</code> which takes a length parameter.
    *
    * @param parameterName the name of the parameter
    * @param reader An object that contains the data to set the parameter value to.
    * @throws SQLException if a database access error occurs or this method is called on
    * a closed <code>CallableStatement</code>
    *
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    * @since 1.6
    */
    public void setClob(String parameterName, Reader reader)
      throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to the given <code>java.sql.Date</code> value
    * using the default time zone of the virtual machine that is running
    * the application.
    * The driver converts this
    * to an SQL <code>DATE</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getDate
    * @since 1.4
    */
    public void setDate(String parameterName, java.sql.Date x)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

   /**
    * Sets the designated parameter to the given <code>java.sql.Date</code> value,
    * using the given <code>Calendar</code> object.  The driver uses
    * the <code>Calendar</code> object to construct an SQL <code>DATE</code> value,
    * which the driver then sends to the database.  With a
    * a <code>Calendar</code> object, the driver can calculate the date
    * taking into account a custom timezone.  If no
    * <code>Calendar</code> object is specified, the driver uses the default
    * timezone, which is that of the virtual machine running the application.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @param cal the <code>Calendar</code> object the driver will use
    *            to construct the date
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getDate
    * @since 1.4
    */
   public void setDate(String parameterName, java.sql.Date x, Calendar cal)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to the given <code>java.sql.Time</code> value.
    * The driver converts this
    * to an SQL <code>TIME</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getTime
    * @since 1.4
    */
   public void setTime(String parameterName, java.sql.Time x)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given <code>java.sql.Time</code> value,
    * using the given <code>Calendar</code> object.  The driver uses
    * the <code>Calendar</code> object to construct an SQL <code>TIME</code> value,
    * which the driver then sends to the database.  With a
    * a <code>Calendar</code> object, the driver can calculate the time
    * taking into account a custom timezone.  If no
    * <code>Calendar</code> object is specified, the driver uses the default
    * timezone, which is that of the virtual machine running the application.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @param cal the <code>Calendar</code> object the driver will use
    *            to construct the time
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getTime
    * @since 1.4
    */
   public void setTime(String parameterName, java.sql.Time x, Calendar cal)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

   /**
   * Sets the designated parameter to a <code>Reader</code> object.
   * This method differs from the <code>setCharacterStream (int, Reader)</code> method
   * because it informs the driver that the parameter value should be sent to
   * the server as a <code>CLOB</code>.  When the <code>setCharacterStream</code> method is used, the
   * driver may have to do extra work to determine whether the parameter
   * data should be sent to the server as a <code>LONGVARCHAR</code> or a <code>CLOB</code>
   *
   * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
   * it might be more efficient to use a version of
   * <code>setClob</code> which takes a length parameter.
   *
   * @param parameterIndex index of the first parameter is 1, the second is 2, ...
   * @param reader An object that contains the data to set the parameter value to.
   * @throws SQLException if a database access error occurs, this method is called on
   * a closed <code>PreparedStatement</code>or if parameterIndex does not correspond to a parameter
   * marker in the SQL statement
   *
   * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
   * @since 1.6
   */
   public void setClob(int parameterIndex, Reader reader)
     throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


   /**
   * Sets the designated parameter to a <code>Reader</code> object.  The reader must contain  the number
   * of characters specified by length otherwise a <code>SQLException</code> will be
   * generated when the <code>PreparedStatement</code> is executed.
   *This method differs from the <code>setCharacterStream (int, Reader, int)</code> method
   * because it informs the driver that the parameter value should be sent to
   * the server as a <code>CLOB</code>.  When the <code>setCharacterStream</code> method is used, the
   * driver may have to do extra work to determine whether the parameter
   * data should be sent to the server as a <code>LONGVARCHAR</code> or a <code>CLOB</code>
   * @param parameterIndex index of the first parameter is 1, the second is 2, ...
   * @param reader An object that contains the data to set the parameter value to.
   * @param length the number of characters in the parameter data.
   * @throws SQLException if a database access error occurs, this method is called on
   * a closed <code>PreparedStatement</code>, if parameterIndex does not correspond to a parameter
   * marker in the SQL statement, or if the length specified is less than zero.
   *
   * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
   * @since 1.6
   */
   public void setClob(int parameterIndex, Reader reader, long length)
     throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to a <code>InputStream</code> object.  The inputstream must contain  the number
    * of characters specified by length otherwise a <code>SQLException</code> will be
    * generated when the <code>PreparedStatement</code> is executed.
    * This method differs from the <code>setBinaryStream (int, InputStream, int)</code>
    * method because it informs the driver that the parameter value should be
    * sent to the server as a <code>BLOB</code>.  When the <code>setBinaryStream</code> method is used,
    * the driver may have to do extra work to determine whether the parameter
    * data should be sent to the server as a <code>LONGVARBINARY</code> or a <code>BLOB</code>
    * @param parameterIndex index of the first parameter is 1,
    * the second is 2, ...
    * @param inputStream An object that contains the data to set the parameter
    * value to.
    * @param length the number of bytes in the parameter data.
    * @throws SQLException if a database access error occurs,
    * this method is called on a closed <code>PreparedStatement</code>,
    * if parameterIndex does not correspond
    * to a parameter marker in the SQL statement,  if the length specified
    * is less than zero or if the number of bytes in the inputstream does not match
    * the specfied length.
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    *
    * @since 1.6
    */
    public void setBlob(int parameterIndex, InputStream inputStream, long length)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to a <code>InputStream</code> object.
    * This method differs from the <code>setBinaryStream (int, InputStream)</code>
    * This method differs from the <code>setBinaryStream (int, InputStream)</code>
    * method because it informs the driver that the parameter value should be
    * sent to the server as a <code>BLOB</code>.  When the <code>setBinaryStream</code> method is used,
    * the driver may have to do extra work to determine whether the parameter
    * data should be sent to the server as a <code>LONGVARBINARY</code> or a <code>BLOB</code>
    *
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setBlob</code> which takes a length parameter.
    *
    * @param parameterIndex index of the first parameter is 1,
    * the second is 2, ...


    * @param inputStream An object that contains the data to set the parameter
    * value to.
    * @throws SQLException if a database access error occurs,
    * this method is called on a closed <code>PreparedStatement</code> or
    * if parameterIndex does not correspond
    * to a parameter marker in the SQL statement,
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    *
    * @since 1.6
    */
    public void setBlob(int parameterIndex, InputStream inputStream)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to a <code>InputStream</code> object.  The <code>inputstream</code> must contain  the number
      * of characters specified by length, otherwise a <code>SQLException</code> will be
      * generated when the <code>CallableStatement</code> is executed.
      * This method differs from the <code>setBinaryStream (int, InputStream, int)</code>
      * method because it informs the driver that the parameter value should be
      * sent to the server as a <code>BLOB</code>.  When the <code>setBinaryStream</code> method is used,
      * the driver may have to do extra work to determine whether the parameter
      * data should be sent to the server as a <code>LONGVARBINARY</code> or a <code>BLOB</code>
      *
      * @param parameterName the name of the parameter to be set
      * the second is 2, ...
      *
      * @param inputStream An object that contains the data to set the parameter
      * value to.
      * @param length the number of bytes in the parameter data.
      * @throws SQLException  if parameterIndex does not correspond
      * to a parameter marker in the SQL statement,  or if the length specified
      * is less than zero; if the number of bytes in the inputstream does not match
      * the specfied length; if a database access error occurs or
      * this method is called on a closed <code>CallableStatement</code>
      * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
      * this method
      *
      * @since 1.6
      */
      public void setBlob(String parameterName, InputStream inputStream, long length)
         throws SQLException{
         throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
    }


 /**
    * Sets the designated parameter to the given <code>java.sql.Blob</code> object.
    * The driver converts this to an SQL <code>BLOB</code> value when it
    * sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x a <code>Blob</code> object that maps an SQL <code>BLOB</code> value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @since 1.6
    */
   public void setBlob (String parameterName, Blob x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to a <code>InputStream</code> object.
    * This method differs from the <code>setBinaryStream (int, InputStream)</code>
    * method because it informs the driver that the parameter value should be
    * sent to the server as a <code>BLOB</code>.  When the <code>setBinaryStream</code> method is used,
    * the driver may have to do extra work to determine whether the parameter
    * data should be send to the server as a <code>LONGVARBINARY</code> or a <code>BLOB</code>
    *
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setBlob</code> which takes a length parameter.
    *
    * @param parameterName the name of the parameter
    * @param inputStream An object that contains the data to set the parameter
    * value to.
    * @throws SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    *
    * @since 1.6
    */
    public void setBlob(String parameterName, InputStream inputStream)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

  /**
  * Sets the value of the designated parameter with the given object. The second
  * argument must be an object type; for integral values, the
  * <code>java.lang</code> equivalent objects should be used.
  *
  * <p>The given Java object will be converted to the given targetSqlType
  * before being sent to the database.
  *
  * If the object has a custom mapping (is of a class implementing the
  * interface <code>SQLData</code>),
  * the JDBC driver should call the method <code>SQLData.writeSQL</code> to write it
  * to the SQL data stream.
  * If, on the other hand, the object is of a class implementing
  * <code>Ref</code>, <code>Blob</code>, <code>Clob</code>,  <code>NClob</code>,
  *  <code>Struct</code>, <code>java.net.URL</code>,
  * or <code>Array</code>, the driver should pass it to the database as a
  * value of the corresponding SQL type.
  * <P>
  * Note that this method may be used to pass datatabase-
  * specific abstract data types.
  *
  * @param parameterName the name of the parameter
  * @param x the object containing the input parameter value
  * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
  * sent to the database. The scale argument may further qualify this type.
  * @param scale for java.sql.Types.DECIMAL or java.sql.Types.NUMERIC types,
  *          this is the number of digits after the decimal point.  For all other
  *          types, this value will be ignored.
  * @exception SQLException if a database access error occurs or
  * this method is called on a closed <code>CallableStatement</code>
  * @exception SQLFeatureNotSupportedException if <code>targetSqlType</code> is
  * a <code>ARRAY</code>, <code>BLOB</code>, <code>CLOB</code>,
  * <code>DATALINK</code>, <code>JAVA_OBJECT</code>, <code>NCHAR</code>,
  * <code>NCLOB</code>, <code>NVARCHAR</code>, <code>LONGNVARCHAR</code>,
  *  <code>REF</code>, <code>ROWID</code>, <code>SQLXML</code>
  * or  <code>STRUCT</code> data type and the JDBC driver does not support
  * this data type
  * @see Types
  * @see #getObject
  * @since 1.4
  */
  public void setObject(String parameterName, Object x, int targetSqlType, int scale)
     throws SQLException{
      throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
 }

  /**
    * Sets the value of the designated parameter with the given object.
    * This method is like the method <code>setObject</code>
    * above, except that it assumes a scale of zero.
    *
    * @param parameterName the name of the parameter
    * @param x the object containing the input parameter value
    * @param targetSqlType the SQL type (as defined in java.sql.Types) to be
    *                      sent to the database
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if <code>targetSqlType</code> is
    * a <code>ARRAY</code>, <code>BLOB</code>, <code>CLOB</code>,
    * <code>DATALINK</code>, <code>JAVA_OBJECT</code>, <code>NCHAR</code>,
    * <code>NCLOB</code>, <code>NVARCHAR</code>, <code>LONGNVARCHAR</code>,
    *  <code>REF</code>, <code>ROWID</code>, <code>SQLXML</code>
    * or  <code>STRUCT</code> data type and the JDBC driver does not support
    * this data type
    * @see #getObject
    * @since 1.4
    */
    public void setObject(String parameterName, Object x, int targetSqlType)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
   * Sets the value of the designated parameter with the given object.
   * The second parameter must be of type <code>Object</code>; therefore, the
   * <code>java.lang</code> equivalent objects should be used for built-in types.
   *
   * <p>The JDBC specification specifies a standard mapping from
   * Java <code>Object</code> types to SQL types.  The given argument
   * will be converted to the corresponding SQL type before being
   * sent to the database.
   *
   * <p>Note that this method may be used to pass datatabase-
   * specific abstract data types, by using a driver-specific Java
   * type.
   *
   * If the object is of a class implementing the interface <code>SQLData</code>,
   * the JDBC driver should call the method <code>SQLData.writeSQL</code>
   * to write it to the SQL data stream.
   * If, on the other hand, the object is of a class implementing
   * <code>Ref</code>, <code>Blob</code>, <code>Clob</code>,  <code>NClob</code>,
   *  <code>Struct</code>, <code>java.net.URL</code>,
   * or <code>Array</code>, the driver should pass it to the database as a
   * value of the corresponding SQL type.
   * <P>
   * This method throws an exception if there is an ambiguity, for example, if the
   * object is of a class implementing more than one of the interfaces named above.
   *
   * @param parameterName the name of the parameter
   * @param x the object containing the input parameter value
   * @exception SQLException if a database access error occurs,
   * this method is called on a closed <code>CallableStatement</code> or if the given
   *            <code>Object</code> parameter is ambiguous
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * this method
   * @see #getObject
   * @since 1.4
   */
   public void setObject(String parameterName, Object x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

  /**
  * Sets the designated parameter to the given input stream, which will have
  * the specified number of bytes.
  * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
  * parameter, it may be more practical to send it via a
  * <code>java.io.InputStream</code>. Data will be read from the stream
  * as needed until end-of-file is reached.  The JDBC driver will
  * do any necessary conversion from ASCII to the database char format.
  *
  * <P><B>Note:</B> This stream object can either be a standard
  * Java stream object or your own subclass that implements the
  * standard interface.
  *
  * @param parameterName the name of the parameter
  * @param x the Java input stream that contains the ASCII parameter value
  * @param length the number of bytes in the stream
  * @exception SQLException if a database access error occurs or
  * this method is called on a closed <code>CallableStatement</code>
  * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
  * this method
  * @since 1.4
  */
 public void setAsciiStream(String parameterName, java.io.InputStream x, int length)
     throws SQLException{
      throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
 }


/**
  * Sets the designated parameter to the given input stream, which will have
  * the specified number of bytes.
  * When a very large binary value is input to a <code>LONGVARBINARY</code>
  * parameter, it may be more practical to send it via a
  * <code>java.io.InputStream</code> object. The data will be read from the stream
  * as needed until end-of-file is reached.
  *
  * <P><B>Note:</B> This stream object can either be a standard
  * Java stream object or your own subclass that implements the
  * standard interface.
  *
  * @param parameterName the name of the parameter
  * @param x the java input stream which contains the binary parameter value
  * @param length the number of bytes in the stream
  * @exception SQLException if a database access error occurs or
  * this method is called on a closed <code>CallableStatement</code>
  * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
  * this method
  * @since 1.4
  */
 public void setBinaryStream(String parameterName, java.io.InputStream x,
                      int length) throws SQLException{
      throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
 }

 /**
   * Sets the designated parameter to the given <code>Reader</code>
   * object, which is the given number of characters long.
   * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
   * parameter, it may be more practical to send it via a
   * <code>java.io.Reader</code> object. The data will be read from the stream
   * as needed until end-of-file is reached.  The JDBC driver will
   * do any necessary conversion from UNICODE to the database char format.
   *
   * <P><B>Note:</B> This stream object can either be a standard
   * Java stream object or your own subclass that implements the
   * standard interface.
   *
   * @param parameterName the name of the parameter
   * @param reader the <code>java.io.Reader</code> object that
   *        contains the UNICODE data used as the designated parameter
   * @param length the number of characters in the stream
   * @exception SQLException if a database access error occurs or
   * this method is called on a closed <code>CallableStatement</code>
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * this method
   * @since 1.4
   */
  public void setCharacterStream(String parameterName,
                          java.io.Reader reader,
                          int length) throws SQLException{
       throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
  }

  /**
   * Sets the designated parameter to the given input stream.
   * When a very large ASCII value is input to a <code>LONGVARCHAR</code>
   * parameter, it may be more practical to send it via a
   * <code>java.io.InputStream</code>. Data will be read from the stream
   * as needed until end-of-file is reached.  The JDBC driver will
   * do any necessary conversion from ASCII to the database char format.
   *
   * <P><B>Note:</B> This stream object can either be a standard
   * Java stream object or your own subclass that implements the
   * standard interface.
   * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
   * it might be more efficient to use a version of
   * <code>setAsciiStream</code> which takes a length parameter.
   *
   * @param parameterName the name of the parameter
   * @param x the Java input stream that contains the ASCII parameter value
   * @exception SQLException if a database access error occurs or
   * this method is called on a closed <code>CallableStatement</code>
   * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
     * @since 1.6
  */
  public void setAsciiStream(String parameterName, java.io.InputStream x)
          throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to the given input stream.
    * When a very large binary value is input to a <code>LONGVARBINARY</code>
    * parameter, it may be more practical to send it via a
    * <code>java.io.InputStream</code> object. The data will be read from the
    * stream as needed until end-of-file is reached.
    *
    * <P><B>Note:</B> This stream object can either be a standard
    * Java stream object or your own subclass that implements the
    * standard interface.
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setBinaryStream</code> which takes a length parameter.
    *
    * @param parameterName the name of the parameter
    * @param x the java input stream which contains the binary parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    * @since 1.6
    */
   public void setBinaryStream(String parameterName, java.io.InputStream x)
   throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given <code>Reader</code>
    * object.
    * When a very large UNICODE value is input to a <code>LONGVARCHAR</code>
    * parameter, it may be more practical to send it via a
    * <code>java.io.Reader</code> object. The data will be read from the stream
    * as needed until end-of-file is reached.  The JDBC driver will
    * do any necessary conversion from UNICODE to the database char format.
    *
    * <P><B>Note:</B> This stream object can either be a standard
    * Java stream object or your own subclass that implements the
    * standard interface.
    * <P><B>Note:</B> Consult your JDBC driver documentation to determine if
    * it might be more efficient to use a version of
    * <code>setCharacterStream</code> which takes a length parameter.
    *
    * @param parameterName the name of the parameter
    * @param reader the <code>java.io.Reader</code> object that contains the
    *        Unicode data
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @throws SQLFeatureNotSupportedException  if the JDBC driver does not support this method
    * @since 1.6
    */
   public void setCharacterStream(String parameterName,
                         java.io.Reader reader) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

   /**
    * Sets the designated parameter to the given
    * <code>java.math.BigDecimal</code> value.
    * The driver converts this to an SQL <code>NUMERIC</code> value when
    * it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getBigDecimal
    * @since 1.4
    */
   public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given Java <code>String</code> value.
    * The driver converts this
    * to an SQL <code>VARCHAR</code> or <code>LONGVARCHAR</code> value
    * (depending on the argument's
    * size relative to the driver's limits on <code>VARCHAR</code> values)
    * when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getString
    * @since 1.4
    */
   public void setString(String parameterName, String x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }



 /**
    * Sets the designated parameter to the given Java array of bytes.
    * The driver converts this to an SQL <code>VARBINARY</code> or
    * <code>LONGVARBINARY</code> (depending on the argument's size relative
    * to the driver's limits on <code>VARBINARY</code> values) when it sends
    * it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getBytes
    * @since 1.4
    */
   public void setBytes(String parameterName, byte x[]) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given <code>java.sql.Timestamp</code> value.
    * The driver
    * converts this to an SQL <code>TIMESTAMP</code> value when it sends it to the
    * database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getTimestamp
    * @since 1.4
    */
   public void setTimestamp(String parameterName, java.sql.Timestamp x)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

    /**
    * Sets the designated parameter to SQL <code>NULL</code>.
    *
    * <P><B>Note:</B> You must specify the parameter's SQL type.
    *
    * @param parameterName the name of the parameter
    * @param sqlType the SQL type code defined in <code>java.sql.Types</code>
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @since 1.4
    */
   public void setNull(String parameterName, int sqlType) throws SQLException {
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to SQL <code>NULL</code>.
    * This version of the method <code>setNull</code> should
    * be used for user-defined types and REF type parameters.  Examples
    * of user-defined types include: STRUCT, DISTINCT, JAVA_OBJECT, and
    * named array types.
    *
    * <P><B>Note:</B> To be portable, applications must give the
    * SQL type code and the fully-qualified SQL type name when specifying
    * a NULL user-defined or REF parameter.  In the case of a user-defined type
    * the name is the type name of the parameter itself.  For a REF
    * parameter, the name is the type name of the referenced type.  If
    * a JDBC driver does not need the type code or type name information,
    * it may ignore it.
    *
    * Although it is intended for user-defined and Ref parameters,
    * this method may be used to set a null parameter of any JDBC type.
    * If the parameter does not have a user-defined or REF type, the given
    * typeName is ignored.
    *
    *
    * @param parameterName the name of the parameter
    * @param sqlType a value from <code>java.sql.Types</code>
    * @param typeName the fully-qualified name of an SQL user-defined type;
    *        ignored if the parameter is not a user-defined type or
    *        SQL <code>REF</code> value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @since 1.4
    */
   public void setNull (String parameterName, int sqlType, String typeName)
       throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given Java <code>boolean</code> value.
    * The driver converts this
    * to an SQL <code>BIT</code> or <code>BOOLEAN</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @see #getBoolean
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @since 1.4
    */
   public void setBoolean(String parameterName, boolean x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }



 /**
    * Sets the designated parameter to the given Java <code>byte</code> value.
    * The driver converts this
    * to an SQL <code>TINYINT</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getByte
    * @since 1.4
    */
   public void setByte(String parameterName, byte x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to the given Java <code>short</code> value.
    * The driver converts this
    * to an SQL <code>SMALLINT</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getShort
    * @since 1.4
    */
   public void setShort(String parameterName, short x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


   /**
    * Sets the designated parameter to the given Java <code>int</code> value.
    * The driver converts this
    * to an SQL <code>INTEGER</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getInt
    * @since 1.4
    */
   public void setInt(String parameterName, int x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given Java <code>long</code> value.
    * The driver converts this
    * to an SQL <code>BIGINT</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getLong
    * @since 1.4
    */
   public void setLong(String parameterName, long x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }


 /**
    * Sets the designated parameter to the given Java <code>float</code> value.
    * The driver converts this
    * to an SQL <code>FLOAT</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getFloat
    * @since 1.4
    */
   public void setFloat(String parameterName, float x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

 /**
    * Sets the designated parameter to the given Java <code>double</code> value.
    * The driver converts this
    * to an SQL <code>DOUBLE</code> value when it sends it to the database.
    *
    * @param parameterName the name of the parameter
    * @param x the parameter value
    * @exception SQLException if a database access error occurs or
    * this method is called on a closed <code>CallableStatement</code>
    * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
    * this method
    * @see #getDouble
    * @since 1.4
    */
   public void setDouble(String parameterName, double x) throws SQLException{
        throw new SQLFeatureNotSupportedException(resBundle.handleGetObject("jdbcrowsetimpl.featnotsupp").toString());
   }

    /**
     * This method re populates the resBundle
     * during the deserialization process
     *
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Default state initialization happens here
        ois.defaultReadObject();
        // Initialization of transient Res Bundle happens here .
        try {
           resBundle = JdbcRowSetResourceBundle.getJdbcRowSetResourceBundle();
        } catch(IOException ioe) {}

    }

   static final long serialVersionUID = -3591946023893483003L;

 //------------------------- JDBC 4.1 -----------------------------------

    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not supported yet.");
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException("Not supported yet.");
    }
}
