/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.management.namespace;

import java.io.IOException;
import javax.management.remote.JMXServiceURL;

/**
 * A {@link JMXNamespaceMBean} that will connect to a remote MBeanServer
 * by creating a {@link javax.management.remote.JMXConnector} from a
 * {@link javax.management.remote.JMXServiceURL}.
 * You can call {@link #connect connect()} and {@link #close close()}
 * several times.
 * @since 1.7
 */
public interface JMXRemoteNamespaceMBean
        extends JMXNamespaceMBean {

    /**
     * Connects to the underlying remote source name space, if not already
     * {@link #isConnected connected}.
     * If connected, do nothing. Otherwise, creates a new connector from the
     * {@link javax.management.remote.JMXServiceURL JMXServiceURL} provided at
     * creation time, and connects to the remote source name space.
     * <p>
     * The source MBeans will not appear in the target name space until the
     * JMXRemoteNamespaceMBean is connected.
     * </p><p>
     * It is possible to call {@code connect()}, {@link #close close()}, and
     * {@code connect()} again.
     * However, closing the connection with the remote name space may cause
     * notification listeners to be lost, unless the client explicitly uses
     * the new {@linkplain javax.management.event JMX event service}.
     * </p><p>
     * @throws IOException if connection to the remote source name space fails.
     * @see #isConnected isConnected
     **/
    public void connect()
        throws IOException;

    /**
     * Closes the connection with the remote source name space.
     * If the connection is already closed, do nothing.
     * Otherwise, closes the underlying {@link
     * javax.management.remote.JMXConnector}.
     * <p>Once closed, it is possible to reopen the connection by
     * calling {@link #connect connect}.
     * </p>
     * @throws IOException if the connection to the remote source name space
     *         can't be closed properly.
     * @see #isConnected isConnected
     **/
    public void close()
        throws IOException;

    /**
     * Tells whether the connection to the remote source name space is opened.
     * @see #connect connect
     * @see #close close
     * @return {@code true} if connected.
     **/
    public boolean isConnected();

    /**
     * Returns the {@link JMXServiceURL} address that points to the remote name
     * space mirrored by this {@link JMXNamespaceMBean JMXNamespace MBean},
     * if available.
     * @return The {@link JMXServiceURL} address that points to the remote name
     * space mirrored by this {@link JMXNamespaceMBean JMXNamespace MBean},
     * or {@code null}.
     */
    public JMXServiceURL getAddress();
}
