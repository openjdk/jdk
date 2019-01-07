/**
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * <p>
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * Provides the API for server side data source access and processing from
 * the Java&trade; programming language.
 * This package supplements the <code>java.sql</code>
 * package and, as of the version 1.4 release, is included in the
 * Java Platform, Standard Edition (Java SE&trade;).
 * It remains an essential part of the Java Platform, Enterprise Edition
 * (Java EE&trade;).
 * <p>
 * The <code>javax.sql</code> package provides for the following:
 * <OL>
 * <LI>The <code>DataSource</code> interface as an alternative to the
 * <code>DriverManager</code> for establishing a
 * connection with a data source
 * <LI>Connection pooling and Statement pooling
 * <LI>Distributed transactions
 * <LI>Rowsets
 * </OL>
 * <p>
 * Applications use the <code>DataSource</code> and <code>RowSet</code>
 * APIs directly, but the connection pooling and distributed transaction
 * APIs are used internally by the middle-tier infrastructure.
 *
 * <H2>Using a <code>DataSource</code> Object to Make a Connection</H2>
 * <p>
 * The <code>javax.sql</code> package provides the preferred
 * way to make a connection with a data source.  The <code>DriverManager</code>
 * class, the original mechanism, is still valid, and code using it will
 * continue to run.  However, the newer <code>DataSource</code> mechanism
 * is preferred because it offers many advantages over the
 * <code>DriverManager</code> mechanism.
 * <p>
 * These are the main advantages of using a <code>DataSource</code> object to
 * make a connection:
 * <UL>
 *
 * <LI>Changes can be made to a data source's properties, which means
 * that it is not necessary to make changes in application code when
 * something about the data source or driver changes.
 * <LI>Connection  and Statement pooling and distributed transactions are available
 * through a <code>DataSource</code> object that is
 * implemented to work with the middle-tier infrastructure.
 * Connections made through the <code>DriverManager</code>
 * do not have connection and statement pooling or distributed transaction
 * capabilities.
 * </UL>
 * <p>
 * Driver vendors provide <code>DataSource</code> implementations. A
 * particular <code>DataSource</code> object represents a particular
 * physical data source, and each connection the <code>DataSource</code> object
 * creates is a connection to that physical data source.
 * <p>
 * A logical name for the data source is registered with a naming service that
 * uses the Java Naming and Directory Interface&trade;
 * (JNDI) API, usually by a system administrator or someone performing the
 * duties of a system administrator. An application can retrieve the
 * <code>DataSource</code> object it wants by doing a lookup on the logical
 * name that has been registered for it.  The application can then use the
 * <code>DataSource</code> object to create a connection to the physical data
 * source it represents.
 * <p>
 * A <code>DataSource</code> object can be implemented to work with the
 * middle tier infrastructure so that the connections it produces will be
 * pooled for reuse. An application that uses such a <code>DataSource</code>
 * implementation will automatically get a connection that participates in
 * connection pooling.
 * A <code>DataSource</code> object can also be implemented to work with the
 * middle tier infrastructure so that the connections it produces can be
 * used for distributed transactions without any special coding.
 *
 * <H2>Connection Pooling and Statement Pooling</H2>
 * <p>
 * Connections made via a <code>DataSource</code>
 * object that is implemented to work with a middle tier connection pool manager
 * will participate in connection pooling.  This can improve performance
 * dramatically because creating new connections is very expensive.
 * Connection pooling allows a connection to be used and reused,
 * thus cutting down substantially on the number of new connections
 * that need to be created.
 * <p>
 * Connection pooling is totally transparent.  It is done automatically
 * in the middle tier of a Java EE configuration, so from an application's
 * viewpoint, no change in code is required. An application simply uses
 * the <code>DataSource.getConnection</code> method to get the pooled
 * connection and uses it the same way it uses any <code>Connection</code>
 * object.
 * <p>
 * The classes and interfaces used for connection pooling are:
 * <UL>
 * <LI><code>ConnectionPoolDataSource</code>
 * <LI><code>PooledConnection</code>
 * <LI><code>ConnectionEvent</code>
 * <LI><code>ConnectionEventListener</code>
 * <LI><code>StatementEvent</code>
 * <LI><code>StatementEventListener</code>
 * </UL>
 * The connection pool manager, a facility in the middle tier of
 * a three-tier architecture, uses these classes and interfaces
 * behind the scenes.  When a <code>ConnectionPoolDataSource</code> object
 * is called on to create a <code>PooledConnection</code> object, the
 * connection pool manager will register as a <code>ConnectionEventListener</code>
 * object with the new <code>PooledConnection</code> object.  When the connection
 * is closed or there is an error, the connection pool manager (being a listener)
 * gets a notification that includes a <code>ConnectionEvent</code> object.
 * <p>
 * If the connection pool manager supports <code>Statement</code> pooling, for
 * <code>PreparedStatements</code>, which can be determined by invoking the method
 * <code>DatabaseMetaData.supportsStatementPooling</code>,  the
 * connection pool manager will register as a <code>StatementEventListener</code>
 * object with the new <code>PooledConnection</code> object.  When the
 * <code>PreparedStatement</code> is closed or there is an error, the connection
 * pool manager (being a listener)
 * gets a notification that includes a <code>StatementEvent</code> object.
 *
 * <H2>Distributed Transactions</H2>
 * <p>
 * As with pooled connections, connections made via a <code>DataSource</code>
 * object that is implemented to work with the middle tier infrastructure
 * may participate in distributed transactions.  This gives an application
 * the ability to involve data sources on multiple servers in a single
 * transaction.
 * <p>
 * The classes and interfaces used for distributed transactions are:
 * <UL>
 * <LI><code>XADataSource</code>
 * <LI><code>XAConnection</code>
 * </UL>
 * These interfaces are used by the transaction manager; an application does
 * not use them directly.
 * <p>
 * The <code>XAConnection</code> interface is derived from the
 * <code>PooledConnection</code> interface, so what applies to a pooled connection
 * also applies to a connection that is part of a distributed transaction.
 * A transaction manager in the middle tier handles everything transparently.
 * The only change in application code is that an application cannot do anything
 * that would interfere with the transaction manager's handling of the transaction.
 * Specifically, an application cannot call the methods <code>Connection.commit</code>
 * or <code>Connection.rollback</code>, and it cannot set the connection to be in
 * auto-commit mode (that is, it cannot call
 * <code>Connection.setAutoCommit(true)</code>).
 * <p>
 * An application does not need to do anything special to participate in a
 * distributed transaction.
 * It simply creates connections to the data sources it wants to use via
 * the <code>DataSource.getConnection</code> method, just as it normally does.
 * The transaction manager manages the transaction behind the scenes.  The
 * <code>XADataSource</code> interface creates <code>XAConnection</code> objects, and
 * each <code>XAConnection</code> object creates an <code>XAResource</code> object
 * that the transaction manager uses to manage the connection.
 *
 *
 * <H2>Rowsets</H2>
 * The <code>RowSet</code> interface works with various other classes and
 * interfaces behind the scenes. These can be grouped into three categories.
 * <OL>
 * <LI>Event Notification
 * <UL>
 * <LI><code>RowSetListener</code><br>
 * A <code>RowSet</code> object is a JavaBeans&trade;
 * component because it has properties and participates in the JavaBeans
 * event notification mechanism. The <code>RowSetListener</code> interface
 * is implemented by a component that wants to be notified about events that
 * occur to a particular <code>RowSet</code> object.  Such a component registers
 * itself as a listener with a rowset via the <code>RowSet.addRowSetListener</code>
 * method.
 * <p>
 * When the <code>RowSet</code> object changes one of its rows, changes all of
 * it rows, or moves its cursor, it also notifies each listener that is registered
 * with it.  The listener reacts by carrying out its implementation of the
 * notification method called on it.
 * <LI><code>RowSetEvent</code><br>
 * As part of its internal notification process, a <code>RowSet</code> object
 * creates an instance of <code>RowSetEvent</code> and passes it to the listener.
 * The listener can use this <code>RowSetEvent</code> object to find out which rowset
 * had the event.
 * </UL>
 * <LI>Metadata
 * <UL>
 * <LI><code>RowSetMetaData</code><br>
 * This interface, derived from the
 * <code>ResultSetMetaData</code> interface, provides information about
 * the columns in a <code>RowSet</code> object.  An application can use
 * <code>RowSetMetaData</code> methods to find out how many columns the
 * rowset contains and what kind of data each column can contain.
 * <p>
 * The <code>RowSetMetaData</code> interface provides methods for
 * setting the information about columns, but an application would not
 * normally use these methods.  When an application calls the <code>RowSet</code>
 * method <code>execute</code>, the <code>RowSet</code> object will contain
 * a new set of rows, and its <code>RowSetMetaData</code> object will have been
 * internally updated to contain information about the new columns.
 * </UL>
 * <LI>The Reader/Writer Facility<br>
 * A <code>RowSet</code> object that implements the <code>RowSetInternal</code>
 * interface can call on the <code>RowSetReader</code> object associated with it
 * to populate itself with data.  It can also call on the <code>RowSetWriter</code>
 * object associated with it to write any changes to its rows back to the
 * data source from which it originally got the rows.
 * A rowset that remains connected to its data source does not need to use a
 * reader and writer because it can simply operate on the data source directly.
 *
 * <UL>
 * <LI><code>RowSetInternal</code><br>
 * By implementing the <code>RowSetInternal</code> interface, a
 * <code>RowSet</code> object gets access to
 * its internal state and is able to call on its reader and writer. A rowset
 * keeps track of the values in its current rows and of the values that immediately
 * preceded the current ones, referred to as the <i>original</i> values.  A rowset
 * also keeps track of (1) the parameters that have been set for its command and
 * (2) the connection that was passed to it, if any.  A rowset uses the
 * <code>RowSetInternal</code> methods behind the scenes to get access to
 * this information.  An application does not normally invoke these methods directly.
 *
 * <LI><code>RowSetReader</code><br>
 * A disconnected <code>RowSet</code> object that has implemented the
 * <code>RowSetInternal</code> interface can call on its reader (the
 * <code>RowSetReader</code> object associated with it) to populate it with
 * data.  When an application calls the <code>RowSet.execute</code> method,
 * that method calls on the rowset's reader to do much of the work. Implementations
 * can vary widely, but generally a reader makes a connection to the data source,
 * reads data from the data source and populates the rowset with it, and closes
 * the connection. A reader may also update the <code>RowSetMetaData</code> object
 * for its rowset.  The rowset's internal state is also updated, either by the
 * reader or directly by the method <code>RowSet.execute</code>.
 *
 *
 * <LI><code>RowSetWriter</code><br>
 * A disconnected <code>RowSet</code> object that has implemented the
 * <code>RowSetInternal</code> interface can call on its writer (the
 * <code>RowSetWriter</code> object associated with it) to write changes
 * back to the underlying data source.  Implementations may vary widely, but
 * generally, a writer will do the following:
 *
 * <UL>
 * <LI>Make a connection to the data source
 * <LI>Check to see whether there is a conflict, that is, whether
 * a value that has been changed in the rowset has also been changed
 * in the data source
 * <LI>Write the new values to the data source if there is no conflict
 * <LI>Close the connection
 * </UL>
 *
 *
 * </UL>
 * </OL>
 * <p>
 * The <code>RowSet</code> interface may be implemented in any number of
 * ways, and anyone may write an implementation. Developers are encouraged
 * to use their imaginations in coming up with new ways to use rowsets.
 *
 *
 * <h2>Package Specification</h2>
 *
 * <ul>
 * <li><a href="https://jcp.org/en/jsr/detail?id=221">JDBC 4.3 Specification</a>
 * </ul>
 *
 * <h2>Related Documentation</h2>
 * <p>
 * The Java Series book published by Addison-Wesley Longman provides detailed
 * information about the classes and interfaces in the <code>javax.sql</code>
 * package:
 *
 * <ul>
 * <li><a href="http://www.oracle.com/technetwork/java/index-142838.html">
 * <i>JDBC&#8482;API Tutorial and Reference, Third Edition</i></a>
 * </ul>
 */
package javax.sql;
