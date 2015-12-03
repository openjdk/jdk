/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.sql.*;

/**
 * An object that provides support for distributed transactions. An
 * {@code XAConnection} object may be enlisted in a distributed transaction
 * by means of an {@code XAResource} object. A transaction manager, usually
 * part of a middle tier server, manages an {@code XAConnection} object
 * through the {@code XAResource} object.
 * <P>
 * An application programmer does not use this interface directly; rather, it is
 * used by a transaction manager working in the middle tier server.
 *
 * @since 1.4
 */
public interface XAConnection extends PooledConnection {

    /**
     * Retrieves an {@code XAResource} object that the transaction manager
     * will use to manage this {@code XAConnection} object's participation
     * in a distributed transaction.
     *
     * @return the {@code XAResource} object
     * @exception SQLException if a database access error occurs
     * @exception SQLFeatureNotSupportedException if the JDBC driver does not
     * support this method
     * @since 1.4
     */
    javax.transaction.xa.XAResource getXAResource() throws SQLException;

    // JDBC 4.3

    /**
     * Sets and validates the sharding keys for this connection.
     *
     * @implSpec The default implementation will throw a
     * {@code SQLFeatureNotSupportedException}.
     *
     * @apiNote This method validates that the sharding keys are valid for the
     * {@code Connection}. The timeout value indicates how long the driver
     * should wait for the {@code Connection} to verify that the sharding key is
     * valid before {@code setShardingKeyIfValid} returns false.
     * @param shardingKey the sharding key to be validated against this
     * connection
     * @param superShardingKey the super sharding key to be validated against
     * this connection. The super sharding key may be {@code null}.
     * @param timeout time in seconds before which the validation process is
     * expected to be completed, otherwise the validation process is aborted. A
     * value of 0 indicates the validation process will not time out.
     * @return true if the connection is valid and the sharding keys are valid
     * and set on this connection; false if the sharding keys are not valid or
     * the timeout period expires before the operation completes.
     * @throws SQLException if an error occurs while performing this validation;
     * the {@code shardingkey} is {@code null}; a {@code superSharedingKey} is specified
     * without a {@code shardingKey}; this method is called on a closed
     * {@code connection}; or the {@code timeout} value is less than 0.
     * @throws SQLFeatureNotSupportedException if the driver does not support
     * sharding
     * @since 1.9
     * @see ShardingKey
     * @see ShardingKeyBuilder
     */
    default boolean setShardingKeyIfValid(ShardingKey shardingKey,
            ShardingKey superShardingKey, int timeout)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("setShardingKeyIfValid not implemented");
    }

    /**
     * Sets and validates the sharding key for this connection.
     * @implSpec
     * The default implementation will throw a
     * {@code SQLFeatureNotSupportedException}.
     * @apiNote
     * This method validates  that the sharding key is valid for the
     * {@code Connection}. The timeout value indicates how long the driver
     * should wait for the {@code Connection} to verify that the sharding key
     * is valid before {@code setShardingKeyIfValid} returns false.
     * @param shardingKey the sharding key to be validated against this connection
     * @param timeout time in seconds before which the validation process is expected to
     * be completed,else the validation process is aborted. A value of 0 indicates
     * the validation process will not time out.
     * @return true if the connection is valid and the sharding key is valid to be
     * set on this connection; false if the sharding key is not valid or
     * the timeout period expires before the operation completes.
     * @throws SQLException if there is an error while performing this validation;
     * this method is called on a closed {@code connection}; the {@code shardingkey}
     * is {@code null}; or the {@code timeout} value is less than 0.
     * @throws SQLFeatureNotSupportedException if the driver does not support sharding
     * @since 1.9
     * @see ShardingKey
     * @see ShardingKeyBuilder
     */
    default boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("setShardingKeyIfValid not implemented");
    }

    /**
     * Specifies a shardingKey and superShardingKey to use with this Connection
     * @implSpec
     * The default implementation will throw a
     * {@code SQLFeatureNotSupportedException}.
     * @apiNote
     * This method sets the specified sharding keys but does not require a
     * round trip to the database to validate that the sharding keys are valid
     * for the {@code Connection}.
     * @param shardingKey the sharding key to set on this connection.
     * @param superShardingKey the super sharding key to set on this connection.
     * The super sharding key may be {@code null}
     * @throws SQLException if an error  occurs setting the sharding keys;
     * this method is called on a closed {@code connection};
     * the {@code shardingkey} is {@code null}; or
     * a {@code superSharedingKey} is specified without a {@code shardingKey}
     * @throws SQLFeatureNotSupportedException if the driver does not support sharding
     * @since 1.9
     * @see ShardingKey
     * @see ShardingKeyBuilder
     */
    default void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("setShardingKey not implemented");
    }

    /**
     * Specifies a shardingKey to use with this Connection
     * @implSpec
     * The default implementation will throw a
     * {@code SQLFeatureNotSupportedException}.
     * @apiNote
     * This method sets the specified sharding key but does not require a
     * round trip to the database to validate that the sharding key is valid
     * for the {@code Connection}.
     * @param shardingKey the sharding key to set on this connection.
     * @throws SQLException if an error  occurs setting the sharding key;
     * this method is called on a closed {@code connection}; or the
     * {@code shardkingKey} is {@code null}
     * @throws SQLFeatureNotSupportedException if the driver does not support sharding
     * @since 1.9
     * @see ShardingKey
     * @see ShardingKeyBuilder
     */
    default void setShardingKey(ShardingKey shardingKey)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("setShardingKey not implemented");
    }
}
