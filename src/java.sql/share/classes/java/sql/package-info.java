/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * Provides the API for accessing and processing data stored in a
 * data source (usually a relational database) using the
 * Java&trade; programming language.
 * This API includes a framework whereby different
 * drivers can be installed dynamically to access different data sources.
 * Although the JDBC&trade; API is mainly geared
 * to passing SQL statements to a database, it provides for reading and
 * writing data from any data source with a tabular format.
 * The reader/writer facility, available through the
 * <code>javax.sql.RowSet</code> group of interfaces, can be customized to
 * use and update data from a spread sheet, flat file, or any other tabular
 * data source.
 *
 * <h2>What the JDBC&trade; 4.3 API Includes</h2>
 * The JDBC&trade; 4.3 API includes both
 * the <code>java.sql</code> package, referred to as the JDBC core API,
 * and the <code>javax.sql</code> package, referred to as the JDBC Optional
 * Package API. This complete JDBC API
 * is included in the Java&trade; Standard Edition (Java SE&trade;), version 7.
 * The <code>javax.sql</code> package extends the functionality of the JDBC API
 * from a client-side API to a server-side API, and it is an essential part
 * of the Java&trade;  Enterprise Edition
 * (Java EE&trade;) technology.
 *
 * <h2>Versions</h2>
 * The JDBC 4.3 API incorporates all of the previous JDBC API versions:
 * <UL>
 *     <LI> The JDBC 4.2 API</li>
 *     <LI> The JDBC 4.1 API</li>
 *     <LI> The JDBC 4.0 API</li>
 *     <LI> The JDBC 3.0 API</li>
 *     <LI> The JDBC 2.1 core API</li>
 *  <LI> The JDBC 2.0 Optional Package API<br>
 *       (Note that the JDBC 2.1 core API and the JDBC 2.0 Optional Package
 *       API together are referred to as the JDBC 2.0 API.)</li>
 *  <LI> The JDBC 1.2 API</li>
 *  <LI> The JDBC 1.0 API</li>
 * </UL>
 * <P>
 * Classes, interfaces, methods, fields, constructors, and exceptions
 * have the following "since" tags that indicate when they were introduced
 * into the Java platform. When these "since" tags are used in
 * Javadoc&trade; comments for the JDBC API,
 * they indicate the following:
 * <UL>
 *     <LI>Since 9 -- new in the JDBC 4.3 API and part of the Java SE platform,
 *         version 9</li>
 *     <LI>Since 1.8 -- new in the JDBC 4.2 API and part of the Java SE platform,
 *         version 8</li>
 *  <LI>Since 1.7 -- new in the JDBC 4.1 API and part of the Java SE platform,
 *      version 7</li>
 * <LI>Since 1.6 -- new in the JDBC 4.0 API and part of the Java SE platform,
 *     version 6</li>
 *  <LI>Since 1.4 -- new in the JDBC 3.0 API and part of the J2SE platform,
 *      version 1.4</li>
 *  <LI>Since 1.2 -- new in the JDBC 2.0 API and part of the J2SE platform,
 *      version 1.2</li>
 *  <LI>Since 1.1 or no "since" tag -- in the original JDBC 1.0 API and part of
 *      the JDK&trade;, version 1.1</li>
 * </UL>
 * <P>
 * <b>NOTE:</b> Many of the new features are optional; consequently, there is
 * some variation in drivers and the features they support. Always
 * check your driver's documentation to see whether it supports a feature before
 * you try to use it.
 * <P>
 * <b>NOTE:</b> The class <code>SQLPermission</code> was added in the
 * Java&trade; 2 SDK, Standard Edition,
 * version 1.3 release. This class is used to prevent unauthorized
 * access to the logging stream associated with the <code>DriverManager</code>,
 * which may contain information such as table names, column data, and so on.
 *
 * <h2>What the <code>java.sql</code> Package Contains</h2>
 * The <code>java.sql</code> package contains API for the following:
 * <UL>
 *   <LI>Making a connection with a database via the <code>DriverManager</code> facility
 *   <UL>
 *       <LI><code>DriverManager</code> class -- makes a connection with a driver
 *       <LI><code>SQLPermission</code> class -- provides permission when code
 *                   running within a Security Manager, such as an applet,
 *                   attempts to set up a logging stream through the
 *                   <code>DriverManager</code>
 *       <LI><code>Driver</code> interface -- provides the API for registering
 *              and connecting drivers based on JDBC technology ("JDBC drivers");
 *              generally used only by the <code>DriverManager</code> class
 *       <LI><code>DriverPropertyInfo</code> class -- provides properties for a
 *              JDBC driver; not used by the general user
 *   </UL>
 *   <LI>Sending SQL statements to a database
 *   <UL>
 *       <LI><code>Statement</code> --  used to send basic SQL statements
 *       <LI><code>PreparedStatement</code> --  used to send prepared statements or
 *               basic SQL statements (derived from <code>Statement</code>)
 *       <LI><code>CallableStatement</code> --  used to call database stored
 *               procedures (derived from <code>PreparedStatement</code>)
 *       <LI><code>Connection</code> interface --  provides methods for creating
 *              statements and managing connections and their properties
 *       <LI><code>Savepoint</code> --  provides savepoints in a transaction
 *
 *   </UL>
 *   <LI>Retrieving and updating the results of a query
 *   <UL>
 *       <LI><code>ResultSet</code> interface
 *   </UL>
 *   <LI>Standard mappings for SQL types to classes and interfaces in the
 *       Java programming language
 *   <UL>
 *       <LI><code>Array</code> interface -- mapping for SQL <code>ARRAY</code>
 *       <LI><code>Blob</code> interface -- mapping for SQL <code>BLOB</code>
 *       <LI><code>Clob</code> interface -- mapping for SQL <code>CLOB</code>
 *       <LI><code>Date</code> class -- mapping for SQL <code>DATE</code>
 *       <LI><code>NClob</code> interface -- mapping for SQL <code>NCLOB</code>
 *       <LI><code>Ref</code> interface -- mapping for SQL <code>REF</code>
 *       <LI><code>RowId</code> interface -- mapping for SQL <code>ROWID</code>
 *       <LI><code>Struct</code> interface -- mapping for SQL <code>STRUCT</code>
 *       <LI><code>SQLXML</code> interface -- mapping for SQL <code>XML</code>
 *       <LI><code>Time</code> class -- mapping for SQL <code>TIME</code>
 *       <LI><code>Timestamp</code> class -- mapping for SQL <code>TIMESTAMP</code>
 *       <LI><code>Types</code> class -- provides constants for SQL types
 *   </UL>
 *   <LI>Custom mapping an SQL user-defined type (UDT) to a class in the
 *        Java programming language
 *   <UL>
 *       <LI><code>SQLData</code> interface -- specifies the mapping of
 *               a UDT to an instance of this class
 *       <LI><code>SQLInput</code> interface -- provides methods for reading
 *               UDT attributes from a stream
 *       <LI><code>SQLOutput</code> interface -- provides methods for writing
 *               UDT attributes back to a stream
 *   </UL>
 *   <LI>Metadata
 *   <UL>
 *       <LI><code>DatabaseMetaData</code> interface -- provides information
 *               about the database
 *       <LI><code>ResultSetMetaData</code> interface -- provides information
 *               about the columns of a <code>ResultSet</code> object
 *       <LI><code>ParameterMetaData</code> interface -- provides information
 *               about the parameters to <code>PreparedStatement</code> commands
 *   </UL>
 *   <LI>Exceptions
 *      <UL>
 *        <LI><code>SQLException</code> -- thrown by most methods when there
 *            is a problem accessing data and by some methods for other reasons
 *        <LI><code>SQLWarning</code> -- thrown to indicate a warning
 *        <LI><code>DataTruncation</code> -- thrown to indicate that data may have
 *            been truncated
 *        <LI><code>BatchUpdateException</code> -- thrown to indicate that not all
 *            commands in a batch update executed successfully
 *      </UL>
 * </UL>
 *
 *     <h3><code>java.sql</code> and <code>javax.sql</code> Features Introduced in the JDBC 4.3 API</h3>
 * <UL>
 *     <LI>Added <code>Sharding</code> support</LI>
 *     <LI>Enhanced <code>Connection</code> to be able to provide hints
 *         to the driver that a request, an independent unit of work,
 *         is beginning or ending</LI>
 *     <LI>Enhanced <code>DatabaseMetaData</code> to determine if Sharding is
 *     supported</LI>
 *     <LI>Added the method <code>drivers</code> to <code>DriverManager</code>
 *         to return a Stream of the currently loaded and
 *         available JDBC drivers</LI>
 *     <LI>Added support to <code>Statement</code> for enquoting literals
 *     and simple identifiers</LI>
 *     <LI>Clarified the Java SE version that methods were deprecated</LI>
 * </UL>
 *
 *     <h3><code>java.sql</code> and <code>javax.sql</code> Features Introduced in the JDBC 4.2 API</h3>
 * <UL>
 *     <LI>Added <code>JDBCType</code>  enum and <code>SQLType</code> interface</li>
 *     <LI>Support for <code>REF CURSORS</code> in <code>CallableStatement</code>
 *     </LI>
 *     <LI><code>DatabaseMetaData</code> methods to return maximum Logical LOB size
 *         and if Ref Cursors are supported</LI>
 *     <LI>Added support for large update counts</LI>
 *
 * </UL>
 *
 *     <h3><code>java.sql</code> and <code>javax.sql</code> Features Introduced in the JDBC 4.1 API</h3>
 * <UL>
 *     <LI>Allow <code>Connection</code>,
 *         <code>ResultSet</code> and <code>Statement</code> objects to be
 *         used with the try-with-resources statement</LI>
 *     <LI>Support added to <code>CallableStatement</code> and
 *         <code>ResultSet</code> to specify the Java type to convert to via the
 *         <code>getObject</code> method</LI>
 *     <LI><code>DatabaseMetaData</code> methods to return PseudoColumns and if a
 *         generated key is always returned</LI>
 *     <LI>Added support to <code>Connection</code> to specify a database schema,
 *     abort and timeout a physical connection.</LI>
 *     <LI>Added support to close a <code>Statement</code> object when its dependent
 *     objects have been closed</LI>
 *     <LI>Support for obtaining the parent logger for a <code>Driver</code>,
 *      <code>DataSource</code>, <code>ConnectionPoolDataSource</code> and
 *      <code>XADataSource</code></LI>
 *
 * </UL>
 * <h3><code>java.sql</code> and <code>javax.sql</code> Features Introduced in the JDBC 4.0 API</h3>
 * <UL>
 *   <LI>auto java.sql.Driver discovery -- no longer need to load a
 * <code>java.sql.Driver</code> class via <code>Class.forName</code>
 *  <LI>National Character Set support added
 *  <li>Support added for the SQL:2003 XML data type
 *  <lI>SQLException enhancements -- Added support for cause chaining; New SQLExceptions
 *  added for common SQLState class value codes
 *  <li>Enhanced Blob/Clob functionality -- Support provided to create and free a Blob/Clob instance
 *  as well as additional methods added to improve accessibility
 *  <li>Support added for accessing a SQL ROWID
 *  <li>Support added to allow a JDBC application to access an instance of a JDBC resource
 *  that has been wrapped by a vendor, usually in an application server or connection
 *  pooling environment.
 *  <li>Availability to be notified when a <code>PreparedStatement</code> that is associated
 *  with a <code>PooledConnection</code> has been closed or the driver determines is invalid
 *
 *
 * </UL>
 *
 *
 * <h3><code>java.sql</code> and <code>javax.sql</code> Features Introduced in the JDBC 3.0 API</h3>
 * <UL>
 *   <LI>Pooled statements -- reuse of statements associated with a pooled
 *        connection
 *   <LI>Savepoints -- allow a transaction to be rolled back to a designated
 *       savepoint
 *   <LI>Properties defined for <code>ConnectionPoolDataSource</code> -- specify
 *       how connections are to be pooled
 *   <LI>Metadata for parameters of a <code>PreparedStatement</code> object
 *   <LI>Ability to retrieve values from automatically generated columns
 *   <LI>Ability to have multiple <code>ResultSet</code> objects
 *        returned from <code>CallableStatement</code> objects open at the
 *       same time
 *   <LI>Ability to identify parameters to <code>CallableStatement</code>
 *       objects by name as well as by index
 *   <LI><code>ResultSet</code> holdability -- ability to specify whether cursors
 *       should be held open or closed at the end of a transaction
 *   <LI>Ability to retrieve and update the SQL structured type instance that a
 *       <code>Ref</code> object references
 *   <LI>Ability to programmatically update <code>BLOB</code>,
 *       <code>CLOB</code>, <code>ARRAY</code>, and <code>REF</code> values.
 *   <LI>Addition of the <code>java.sql.Types.DATALINK</code> data type --
 *       allows JDBC drivers access to objects stored outside a data source
 *   <LI>Addition of metadata for retrieving SQL type hierarchies
 * </UL>
 *
 * <h3><code>java.sql</code> Features Introduced in the JDBC 2.1 Core API</h3>
 * <UL>
 *   <LI>Scrollable result sets--using new methods in the <code>ResultSet</code>
 *       interface that allow the cursor to be moved to a particular row or to a
 *       position relative to its current position
 *   <LI>Batch updates
 *   <LI>Programmatic updates--using <code>ResultSet</code> updater methods
 *   <LI>New data types--interfaces mapping the SQL3 data types
 *   <LI>Custom mapping of user-defined types (UDTs)
 *   <LI>Miscellaneous features, including performance hints, the use of character
 *       streams, full precision for <code>java.math.BigDecimal</code> values,
 *       additional security, and
 *       support for time zones in date, time, and timestamp values.
 * </UL>
 *
 * <h3><code>javax.sql</code> Features Introduced in the JDBC 2.0 Optional
 * Package API</h3>
 * <UL>
 *   <LI>The <code>DataSource</code> interface as a means of making a connection.  The
 *       Java Naming and Directory Interface&trade;
 *       (JNDI) is used for registering a <code>DataSource</code> object with a
 *       naming service and also for  retrieving it.
 *   <LI>Pooled connections -- allowing connections to be used and reused
 *   <LI>Distributed transactions -- allowing a transaction to span diverse
 *       DBMS servers
 *   <LI><code>RowSet</code> technology -- providing a convenient means of
 *       handling and passing data
 * </UL>
 *
 *
 * <h3>Custom Mapping of UDTs</h3>
 * A user-defined type (UDT) defined in SQL can be mapped to a class in the Java
 * programming language. An SQL structured type or an SQL <code>DISTINCT</code>
 * type are the UDTs that may be custom mapped.  The following three
 * steps set up a custom mapping:
 * <ol>
 *   <li>Defining the SQL structured type or <code>DISTINCT</code> type in SQL
 *   <li>Defining the class in the Java programming language to which the
 *       SQL UDT will be mapped.  This class must implement the
 *       <code>SQLData</code> interface.
 *   <li>Making an entry in a <code>Connection</code> object's type map
 *       that contains two things:
 *    <ul>
 *       <li>the fully-qualified SQL name of the UDT
 *       <li>the <code>Class</code> object for the class that implements the
 *            <code>SQLData</code> interface
 *    </ul>
 * </ol>
 * <p>
 * When these are in place for a UDT, calling the methods
 * <code>ResultSet.getObject</code> or <code>CallableStatement.getObject</code>
 * on that UDT will automatically retrieve the custom mapping for it. Also, the
 * <code>PreparedStatement.setObject</code> method will automatically map the
 * object back to its SQL type to store it in the data source.
 *
 * <h2>Package Specification</h2>
 *
 * <ul>
 *   <li><a href="https://jcp.org/en/jsr/detail?id=221">JDBC 4.3 Specification</a>
 * </ul>
 *
 * <h2>Related Documentation</h2>
 *
 * <ul>
 *   <li><a href="http://docs.oracle.com/javase/tutorial/jdbc/basics/index.html">
 *           Lesson:JDBC Basics(The Javaxx Tutorials &gt; JDBC&trade; Database Access)</a>
 *
 *  <li><a href="http://www.oracle.com/technetwork/java/index-142838.html">
 *           <i>JDBC&trade; API Tutorial and Reference, Third Edition</i></a>
 * </ul>
 */
package java.sql;
