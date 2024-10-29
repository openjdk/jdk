/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.management.remote.http;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.management.*;
import javax.management.remote.*;
import java.net.MalformedURLException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compare with
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnector.java
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnection.java
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnectionImpl.java
 */
public class HttpRestConnector implements JMXConnector, JMXAddressable, Closeable {

    protected volatile boolean connected;
    protected JMXServiceURL url;
    protected String baseURL;
    protected Map<String,?> env;
    protected HttpRestConnection connection;

    private static AtomicInteger id = new AtomicInteger(0);

    public HttpRestConnector(JMXServiceURL url, Map<String,?> env) {
        this.url = url;
        this.env = env;
        if (env == null) {
            this.env = new HashMap<String,Object>();
        }
        System.err.println("XXXXX HttpRestConnector url=" + url);
        fixURL();
    }

    private void fixURL() {
        // Given JMXServiceURL may be basic, e.g. "service:jmx:http://hostname:port"
        // Or may include more detail, e.g. "hostname:port/jmx/servers/platform".
        // Normalise our baseURL to end with /jmx/servers/

        // Convert to just http....
        baseURL = url.toString().substring(12);
        // or rebuild from host/port/protocol

        String path = url.getURLPath(); // e.g. /jmx/servers/platform

        if (path.isEmpty()) {
            path = "/jmx/servers/platform";
            baseURL = baseURL + path;
        }
    }

    public JMXServiceURL getAddress() {
        // JMXAddressable interface
        return url;
    }

    public void connect() throws IOException {
        connect(env);
    }

    public void connect(Map<String,?> env) throws IOException {
        if (connected) {
            return;
        }
        // In RMI, the Connector would call RMIServerImpl.newClient over RMI,
        // whch calls connServer.connectionOpened (which adds to list of connections in JmxConnectorServer)
        // and returns an RMIConnection.
        connection = new HttpRestConnection(url, baseURL, env); 
        connection.setup();
        connection.setConnector(this);
        connected = true;
    }

    public MBeanServerConnection getMBeanServerConnection() throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        return connection;
    }

    public void close() throws IOException {
        // test/jdk/javax/management/remote/mandatory/connection/CloseFailedClientTest.java
        // closes a Connector that failed to connect, and expects no IOException.
        connected = false;
        connection.close(); // Close HttpRestConnection
    }

    public void addConnectionNotificationListener(NotificationListener listener,
                                          NotificationFilter filter,
                                          Object handback) {

        if (listener == null) {
            throw new NullPointerException("listener");
        }
    }

    public void removeConnectionNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {

        if (listener == null) {
            throw new NullPointerException("listener");
        }
    }

    public void removeConnectionNotificationListener(NotificationListener l,
                                                     NotificationFilter f,
                                                     Object handback)
            throws ListenerNotFoundException {

        if (l == null) {
            throw new NullPointerException("listener");
        }
    }

    public String getConnectionId() throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        return connection.getConnectionId();
    }
}
