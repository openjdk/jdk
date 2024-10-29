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


import com.sun.jmx.remote.security.MBeanServerFileAccessController;
import com.sun.jmx.remote.util.EnvHelp;
import com.sun.jmx.remote.util.ClassLogger;

import jdk.internal.management.remote.rest.PlatformRestAdapter;
import jdk.internal.management.remote.rest.JMXRestAdapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.remote.JMXAuthenticator;

import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;

/**
 * <p>A JMX API connector server that creates HTTP-based connections
 * from remote clients.  Usually, such connector servers are made
 * using {@link javax.management.remote.JMXConnectorServerFactory
 * JMXConnectorServerFactory}.  However, specialized applications can
 * use this class directly.</p>
 *
 */
public class HttpRestConnectorServer extends JMXConnectorServer {

    /**
     * <p>Makes an <code>HttpRestConnectorServer</code>.
     *
     * @param url the URL defining how to create the connector server.
     * Cannot be null.
     *
     * @param environment attributes governing the creation and
     * storing of the RMI object.  Can be null, which is equivalent to
     * an empty Map.
     *
     * @exception IllegalArgumentException if <code>url</code> is null.
     *
     * @exception MalformedURLException if <code>url</code> does not
     * conform to the syntax for an RMI connector, or if its protocol
     * is not recognized by this implementation. Only "rmi" is valid when
     * this constructor is used.
     *
     * @exception IOException if the connector server cannot be created
     * for some reason or if it is inevitable that its {@link #start()
     * start} method will fail.
     */
    public HttpRestConnectorServer(JMXServiceURL url, Map<String,?> environment)
            throws IOException {
        this(url, environment, (MBeanServer) null);
    }

    /**
     * <p>Makes an <code>HttpRestConnectorServer</code> for the given MBean
     * server.</p>
     *
     * @param url the URL defining how to create the connector server.
     * Cannot be null.
     *
     * @param environment attributes governing the creation and
     * storing of the RMI object.  Can be null, which is equivalent to
     * an empty Map.
     *
     * @param mbeanServer the MBean server to which the new connector
     * server is attached, or null if it will be attached by being
     * registered as an MBean in the MBean server.
     *
     * @exception IllegalArgumentException if <code>url</code> is null.
     *
     * @exception MalformedURLException if <code>url</code> does not
     * conform to the syntax for an HTTP connector, or if its protocol
     * is not recognized by this implementation.
     *
     * @exception IOException if the connector server cannot be created
     * for some reason or if it is inevitable that its {@link #start()
     * start} method will fail.
     *
     * @see #start
     */
    public HttpRestConnectorServer(JMXServiceURL url, Map<String,?> environment,
                              MBeanServer mbeanServer)
            throws IOException {
        super(mbeanServer);

        if (url == null) {
            throw new IllegalArgumentException("Null JMXServiceURL");
        }

        if (environment == null) {
            this.attributes = Collections.emptyMap();
        } else {
            EnvHelp.checkAttributes(environment);
            this.attributes = Collections.unmodifiableMap(environment);
        }

        this.address = url;
    }

    /**
     * <p>Returns a client stub for this connector server.  A client
     * stub is a serializable object whose {@link
     * JMXConnector#connect(Map) connect} method can be used to make
     * one new connection to this connector server.</p>
     *
     * @param env client connection parameters of the same sort that
     * could be provided to {@link JMXConnector#connect(Map)
     * JMXConnector.connect(Map)}.  Can be null, which is equivalent
     * to an empty map.
     *
     * @return a client stub that can be used to make a new connection
     * to this connector server.
     *
     * @exception UnsupportedOperationException if this connector
     * server does not support the generation of client stubs.
     *
     * @exception IllegalStateException if the JMXConnectorServer is
     * not started (see {@link #isActive()}).
     *
     * @exception IOException if a communications problem means that a
     * stub cannot be created.
     **/
    public JMXConnector toJMXConnector(Map<String,?> env) throws IOException {
        if (!isActive()) throw new
            IllegalStateException("Connector is not active");

        // Merge maps
        Map<String, Object> usemap = new HashMap<String, Object>(
                (this.attributes==null)?Collections.<String, Object>emptyMap():
                    this.attributes);

        if (env != null) {
            EnvHelp.checkAttributes(env);
            usemap.putAll(env);
        }

        usemap = EnvHelp.filterAttributes(usemap);

        throw new RuntimeException("not implemented XXXX");
    }

    /**
     * <p>Activates the connector server, that is starts listening for
     * client connections.  Calling this method when the connector
     * server is already active has no effect.  Calling this method
     * when the connector server has been stopped will generate an
     * <code>IOException</code>.</p>
     *
     * @exception IllegalStateException if the connector server has
     * not been attached to an MBean server.
     * @exception IOException if the connector server cannot be
     * started.
     */
    public synchronized void start() throws IOException {
        final boolean tracing = logger.traceOn();

        if (state == STARTED) {
            if (tracing) logger.trace("start", "already started");
            return;
        } else if (state == STOPPED) {
            if (tracing) logger.trace("start", "already stopped");
            throw new IOException("The server has already been stopped.");
        }

        if (getMBeanServer() == null) {
            throw new IllegalStateException("This connector server is not " +
                                            "attached to an MBean server");
        }

        // Check the internal access file property to see
        // if an MBeanServerForwarder is to be provided
        //
        if (attributes != null) {
            // Check if access file property is specified
            //
            String accessFile =
                (String) attributes.get("jmx.remote.x.access.file");
            if (accessFile != null) {
                // Access file property specified, create an instance
                // of the MBeanServerFileAccessController class
                //
                MBeanServerForwarder mbsf;
                try {
                    mbsf = new MBeanServerFileAccessController(accessFile);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
                // Set the MBeanServerForwarder
                //
                setMBeanServerForwarder(mbsf);
            }
        }

        try {
            // Create a REST Adapter.
            String serverName = null; // platform
            if (getMBeanServer() == ManagementFactory.getPlatformMBeanServer()) {
                serverName = "platform";
            }

            // Our address may only be a partial/generic address like "service:jmx:http://"
            // so we need the http server to start etc...
            // Therefore connection ID has to be set by JMXRestAdapter.
            // We will query it and send up to JMXConnectorServer by calling connectionOpened.
            JMXRestAdapter rest = PlatformRestAdapter.newRestAdapter(this, getMBeanServer(), serverName /* context */,  attributes /*env */);

            System.err.println("XXXX HTTPConnectorServer start  current address = " + address + " rest = " + rest);

            synchronized(openedServers) {
                openedServers.add(rest);
            }
            address = new JMXServiceURL("service:jmx:" + rest.getUrl());
            connectionOpened(rest.getConnectionId(), "Connection opened", null);

        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            else if (e instanceof IOException)
                throw (IOException) e;
            else
                throw new IOException("Got unexpected exception while " +
                                     "starting the connector server: "
                                     + e, e);
        }

        state = STARTED;

        if (tracing) {
            logger.trace("start", "Connector Server Address = " + address);
            logger.trace("start", "started.");
        }
    }
 
    /**
     * <p>Deactivates the connector server, that is, stops listening for
     * client connections.  Calling this method will also close all
     * client connections that were made by this server.  After this
     * method returns, whether normally or with an exception, the
     * connector server will not create any new client
     * connections.</p>
     *
     * <p>Once a connector server has been stopped, it cannot be started
     * again.</p>
     *
     * <p>Calling this method when the connector server has already
     * been stopped has no effect.  Calling this method when the
     * connector server has not yet been started will disable the
     * connector server object permanently.</p>
     *
     * <p>If closing a client connection produces an exception, that
     * exception is not thrown from this method.  A {@link
     * JMXConnectionNotification} is emitted from this MBean with the
     * connection ID of the connection that could not be closed.</p>
     *
     * <p>Closing a connector server is a potentially slow operation.
     * For example, if a client machine with an open connection has
     * crashed, the close operation might have to wait for a network
     * protocol timeout.  Callers that do not want to block in a close
     * operation should do it in a separate thread.</p>
     *
     * <p>This method calls the method {@link RMIServerImpl#close()
     * close} on the connector server's <code>RMIServerImpl</code>
     * object.</p>
     *
     * <p>If the <code>RMIServerImpl</code> was bound to a JNDI
     * directory by the {@link #start() start} method, it is unbound
     * from the directory by this method.</p>
     *
     * @exception IOException if the server cannot be closed cleanly,
     * or if the <code>RMIServerImpl</code> cannot be unbound from the
     * directory.  When this exception is thrown, the server has
     * already attempted to close all client connections, if
     * appropriate; to call {@link RMIServerImpl#close()}; and to
     * unbind the <code>RMIServerImpl</code> from its directory, if
     * appropriate.  All client connections are closed except possibly
     * those that generated exceptions when the server attempted to
     * close them.
     */
    public void stop() throws IOException {
        final boolean tracing = logger.traceOn();

        synchronized (this) {
            if (state == STOPPED) {
                if (tracing) logger.trace("stop","already stopped.");
                return;
            } else if (state == CREATED) {
                if (tracing) logger.trace("stop","not started yet.");
            }

            if (tracing) logger.trace("stop", "stopping.");
            state = STOPPED;
        }

        synchronized(openedServers) {
            // openedServers.remove(this) ;
            for (JMXRestAdapter a : openedServers) {
                System.err.println("XXXX JMXConnectorServer stop : " + a);
                a.stop();
            }
            openedServers.clear();
        }

        IOException exception = null;

        if (exception != null) throw exception;

        if (tracing) logger.trace("stop", "stopped");
    }

    public synchronized boolean isActive() {
        return (state == STARTED);
    }

    public JMXServiceURL getAddress() {
        if (!isActive())
            return null;
        return address;
    }

    public Map<String,?> getAttributes() {
        Map<String, ?> map = EnvHelp.filterAttributes(attributes);
        return Collections.unmodifiableMap(map);
    }

    @Override
    public synchronized void setMBeanServerForwarder(MBeanServerForwarder mbsf) {
        super.setMBeanServerForwarder(mbsf);
    }

    /* We repeat the definitions of connection{Opened,Closed,Failed}
       here so that they are accessible to other classes in this package
       even though they have protected access.  */

    @Override
    protected void connectionOpened(String connectionId, String message,
                                    Object userData) {
        super.connectionOpened(connectionId, message, userData);
    }

    @Override
    protected void connectionClosed(String connectionId, String message,
                                    Object userData) {
        super.connectionClosed(connectionId, message, userData);
    }

    @Override
    protected void connectionFailed(String connectionId, String message,
                                    Object userData) {
        super.connectionFailed(connectionId, message, userData);
    }

    // Private variables
    // -----------------

    private static ClassLogger logger =
        new ClassLogger("javax.management.remote.rmi", "RMIConnectorServer");

    private JMXServiceURL address;
    private final Map<String, ?> attributes;

    // state
    private static final int CREATED = 0;
    private static final int STARTED = 1;
    private static final int STOPPED = 2;

    private int state = CREATED;

    private static final Set<JMXRestAdapter> openedServers = new HashSet<>();

}
