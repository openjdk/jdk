/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.sql;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * An object that provides hooks for connection pool management.
 * A <code>PooledConnection</code> object
 * represents a physical connection to a data source.  The connection
 * can be recycled rather than being closed when an application is
 * finished with it, thus reducing the number of connections that
 * need to be made.
 * <P>
 * An application programmer does not use the <code>PooledConnection</code>
 * interface directly; rather, it is used by a middle tier infrastructure
 * that manages the pooling of connections.
 * <P>
 * When an application calls the method <code>DataSource.getConnection</code>,
 * it gets back a <code>Connection</code> object.  If connection pooling is
 * being done, that <code>Connection</code> object is actually a handle to
 * a <code>PooledConnection</code> object, which is a physical connection.
 * <P>
 * The connection pool manager, typically the application server, maintains
 * a pool of <code>PooledConnection</code> objects.  If there is a
 * <code>PooledConnection</code> object available in the pool, the
 * connection pool manager returns a <code>Connection</code> object that
 * is a handle to that physical connection.
 * If no <code>PooledConnection</code> object is available, the
 * connection pool manager calls the <code>ConnectionPoolDataSource</code>
 * method <code>getPoolConnection</code> to create a new physical connection.  The
 *  JDBC driver implementing <code>ConnectionPoolDataSource</code> creates a
 *  new <code>PooledConnection</code> object and returns a handle to it.
 * <P>
 * When an application closes a connection, it calls the <code>Connection</code>
 * method <code>close</code>. When connection pooling is being done,
 * the connection pool manager is notified because it has registered itself as
 * a <code>ConnectionEventListener</code> object using the
 * <code>ConnectionPool</code> method <code>addConnectionEventListener</code>.
 * The connection pool manager deactivates the handle to
 * the <code>PooledConnection</code> object and  returns the
 * <code>PooledConnection</code> object to the pool of connections so that
 * it can be used again.  Thus, when an application closes its connection,
 * the underlying physical connection is recycled rather than being closed.
 * <P>
 * The physical connection is not closed until the connection pool manager
 * calls the <code>PooledConnection</code> method <code>close</code>.
 * This method is generally called to have an orderly shutdown of the server or
 * if a fatal error has made the connection unusable.
 *
 * <p>
 * A connection pool manager is often also a statement pool manager, maintaining
 *  a pool of <code>PreparedStatement</code> objects.
 *  When an application closes a prepared statement, it calls the
 *  <code>PreparedStatement</code>
 * method <code>close</code>. When <code>Statement</code> pooling is being done,
 * the pool manager is notified because it has registered itself as
 * a <code>StatementEventListener</code> object using the
 * <code>ConnectionPool</code> method <code>addStatementEventListener</code>.
 *  Thus, when an application closes its  <code>PreparedStatement</code>,
 * the underlying prepared statement is recycled rather than being closed.
 *
 * @since 1.4
 */

public interface PooledConnection {

  /**
   * Creates and returns a <code>Connection</code> object that is a handle
   * for the physical connection that
   * this <code>PooledConnection</code> object represents.
   * The connection pool manager calls this method when an application has
   * called the method <code>DataSource.getConnection</code> and there are
   * no <code>PooledConnection</code> objects available. See the
   * {@link PooledConnection interface description} for more information.
   *
   * @return  a <code>Connection</code> object that is a handle to
   *          this <code>PooledConnection</code> object
   * @exception SQLException if a database access error occurs
   * @exception java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
   * this method
   * @since 1.4
   */
  Connection getConnection() throws SQLException;

  /**
   * Closes the physical connection that this <code>PooledConnection</code>
   * object represents.  An application never calls this method directly;
   * it is called by the connection pool module, or manager.
   * <P>
   * See the {@link PooledConnection interface description} for more
   * information.
   *
   * @exception SQLException if a database access error occurs
   * @exception java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support
   * this method
   * @since 1.4
   */
  void close() throws SQLException;

  /**
   * Registers the given event listener so that it will be notified
   * when an event occurs on this <code>PooledConnection</code> object.
   *
   * @param listener a component, usually the connection pool manager,
   *        that has implemented the
   *        <code>ConnectionEventListener</code> interface and wants to be
   *        notified when the connection is closed or has an error
   * @see #removeConnectionEventListener
   */
  void addConnectionEventListener(ConnectionEventListener listener);

  /**
   * Removes the given event listener from the list of components that
   * will be notified when an event occurs on this
   * <code>PooledConnection</code> object.
   *
   * @param listener a component, usually the connection pool manager,
   *        that has implemented the
   *        <code>ConnectionEventListener</code> interface and
   *        been registered with this <code>PooledConnection</code> object as
   *        a listener
   * @see #addConnectionEventListener
   */
  void removeConnectionEventListener(ConnectionEventListener listener);

        /**
         * Registers a <code>StatementEventListener</code> with this <code>PooledConnection</code> object.  Components that
         * wish to be notified when  <code>PreparedStatement</code>s created by the
         * connection are closed or are detected to be invalid may use this method
         * to register a <code>StatementEventListener</code> with this <code>PooledConnection</code> object.
         *
         * @param listener      an component which implements the <code>StatementEventListener</code>
         *                                      interface that is to be registered with this <code>PooledConnection</code> object
         *
         * @since 1.6
         */
        public void addStatementEventListener(StatementEventListener listener);

        /**
         * Removes the specified <code>StatementEventListener</code> from the list of
         * components that will be notified when the driver detects that a
         * <code>PreparedStatement</code> has been closed or is invalid.
         *
         * @param listener      the component which implements the
         *                                      <code>StatementEventListener</code> interface that was previously
         *                                      registered with this <code>PooledConnection</code> object
         *
         * @since 1.6
         */
        public void removeStatementEventListener(StatementEventListener listener);

 }
