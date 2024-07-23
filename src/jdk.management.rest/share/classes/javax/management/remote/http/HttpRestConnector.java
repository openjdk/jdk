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

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.management.*;
import javax.management.remote.*;
import java.net.MalformedURLException;

/**
 * Compare with
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnector.java
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnection.java
 * src/java.management.rmi/share/classes/javax/management/remote/rmi/RMIConnectionImpl.java
 */
public class HttpRestConnector implements JMXConnector {

    protected boolean connected;
    protected JMXServiceURL url;
    protected Map<String,?> env;
    protected HttpRestConnection connection;

    public HttpRestConnector(JMXServiceURL url, Map<String,?> env) {
        this.url = url;
        this.env = env;
        if (env == null) {
            this.env = new HashMap<String,Object>();
        }
    }

    public void connect() throws IOException {
        connect(env);
    }

    public void connect(Map<String,?> env) throws IOException {
        if (connected) {
            return;
        }
        // Connect and verify available servers
        // JMXServiceURL e.g. service:jmx:http://hostname:1234
        // Normalise our baseURL to end with /jmx/servers/ including final /
        String baseURL = url.toString();
        if (!baseURL.startsWith("service:jmx:")) {
            throw new IOException("URL beginning service:jmx: expected");
        }
        baseURL = baseURL.substring(12); // or rebuild from host/port/protocol
        System.err.println("connect: " + baseURL);
        // Possibly just require URL to end in /jmx/servers/ plus optional mbserver name
        if (!baseURL.endsWith("/")) {
            baseURL = baseURL + "/";
        }
        if (!baseURL.endsWith("/jmx/servers/")) {
            baseURL = baseURL + "jmx/servers/"; // stops you giving a mbserver name
        }
        // Force to /platform/ MBeanServer.
        if (!baseURL.endsWith("/platform/")) {
            baseURL = baseURL + "platform/";
        }
        connection = new HttpRestConnection(baseURL, env);
        connection.setup();
        connected = true;
    }

    public MBeanServerConnection getMBeanServerConnection() throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        return connection;
    }

    public void close() throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        connected = false;
    }

    public void addConnectionNotificationListener(NotificationListener listener,
                                          NotificationFilter filter,
                                          Object handback) {

    }

    public void removeConnectionNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {

    }

    public void removeConnectionNotificationListener(NotificationListener l,
                                                     NotificationFilter f,
                                                     Object handback)
            throws ListenerNotFoundException {

    }

    public String getConnectionId() throws IOException {
        if (!connected) {
            throw new IOException("Not connected");
        }
        return null;
    }
}
