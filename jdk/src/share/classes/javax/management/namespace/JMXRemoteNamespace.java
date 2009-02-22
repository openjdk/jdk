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

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.mbeanserver.Util;
import com.sun.jmx.remote.util.EnvHelp;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeChangeNotification;

import javax.management.ClientContext;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * A {@link JMXNamespace} that will connect to a remote MBeanServer
 * by creating a {@link javax.management.remote.JMXConnector} from a
 * {@link javax.management.remote.JMXServiceURL}.
 * <p>
 * You can call {@link #connect() connect()} and {@link #close close()}
 * several times. This MBean will emit an {@link AttributeChangeNotification}
 * when the value of its {@link #isConnected Connected} attribute changes.
 * </p>
 * <p>
 * The JMX Remote Namespace MBean is not connected until {@link
 * #connect() connect()} is explicitly called. The usual sequence of code to
 * create a JMX Remote Namespace is thus:
 * </p>
 * <pre>
 *     final String namespace = "mynamespace";
 *     final ObjectName name = {@link JMXNamespaces#getNamespaceObjectName
 *       JMXNamespaces.getNamespaceObjectName(namespace)};
 *     final JMXServiceURL remoteServerURL = .... ;
 *     final Map<String,Object> optionsMap = .... ;
 *     final MBeanServer masterMBeanServer = .... ;
 *     final JMXRemoteNamespace namespaceMBean = {@link #newJMXRemoteNamespace
 *        JMXRemoteNamespace.newJMXRemoteNamespace(remoteServerURL, optionsMap)};
 *     masterMBeanServer.registerMBean(namespaceMBean, name);
 *     namespaceMBean.connect();
 *     // or: masterMBeanServer.invoke(name, {@link #connect() "connect"}, null, null);
 * </pre>
 * <p>
 * The JMX Remote Namespace MBean will register for {@linkplain
 * JMXConnectionNotification JMX Connection Notifications} with its underlying
 * {@link JMXConnector}. When a JMX Connection Notification indicates that
 * the underlying connection has failed, the JMX Remote Namespace MBean
 * closes its underlying connector and switches its {@link #isConnected
 * Connected} attribute to false, emitting an {@link
 * AttributeChangeNotification}.
 * </p>
 * <p>
 * At this point, a managing application (or an administrator connected
 * through a management console) can attempt to reconnect the
 * JMX Remote Namespace MBean by calling its {@link #connect() connect()} method
 * again.
 * </p>
 * <p>Note that when the connection with the remote namespace fails, or when
 *    {@link #close} is called, then any notification subscription to
 *    MBeans registered in that namespace will be lost - unless a custom
 *    {@linkplain javax.management.event event service} supporting connection-less
 *    mode was used.
 * </p>
 * @since 1.7
 */
public class JMXRemoteNamespace
        extends JMXNamespace
        implements JMXRemoteNamespaceMBean, NotificationEmitter {

    /**
     * A logger for this class.
     */
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;


    // This connection listener is used to listen for connection events from
    // the underlying JMXConnector. It is used in particular to maintain the
    // "connected" state in this MBean.
    //
    private class ConnectionListener implements NotificationListener {
        private ConnectionListener() {
        }
        public void handleNotification(Notification notification,
                Object handback) {
            if (!(notification instanceof JMXConnectionNotification))
                return;
            final JMXConnectionNotification cn =
                    (JMXConnectionNotification)notification;
            final String type = cn.getType();
            if (JMXConnectionNotification.CLOSED.equals(type)
                    || JMXConnectionNotification.FAILED.equals(type)) {
                checkState(this,cn,(JMXConnector)handback);
            }
        }
    }

    // When the JMXRemoteNamespace is originally created, it is not connected,
    // which means that the source MBeanServer should be one that throws
    // exceptions for most methods.  When it is subsequently connected,
    // the methods should be forwarded to the MBeanServerConnection.
    // We handle this using MBeanServerConnectionWrapper.  The
    // MBeanServerConnection that is supplied to the constructor of
    // MBeanServerConnectionWrapper is ignored (and in fact it is null)
    // because the one that is actually used is the one supplied by the
    // override of getMBeanServerConnection().
    private static class JMXRemoteNamespaceDelegate
            extends MBeanServerConnectionWrapper {
        private volatile JMXRemoteNamespace parent=null;

        JMXRemoteNamespaceDelegate() {
            super(null,null);
        }
        @Override
        public MBeanServerConnection getMBeanServerConnection() {
            return parent.getMBeanServerConnection();
        }
        @Override
        public ClassLoader getDefaultClassLoader() {
            return parent.getDefaultClassLoader();
        }

        // Because this class is instantiated in the super() call from the
        // constructor of JMXRemoteNamespace, it cannot be an inner class.
        // This method achieves the effect that an inner class would have
        // had, of giving the class a reference to the outer "this".
        synchronized void initParentOnce(JMXRemoteNamespace parent) {
            if (this.parent != null)
                throw new UnsupportedOperationException("parent already set");
            this.parent=parent;

        }

    }

    private static final MBeanNotificationInfo connectNotification =
        new MBeanNotificationInfo(new String[] {
            AttributeChangeNotification.ATTRIBUTE_CHANGE},
            "Connected",
            "Emitted when the Connected state of this object changes");

    private static AtomicLong seqNumber = new AtomicLong(0);

    private final NotificationBroadcasterSupport broadcaster;
    private final ConnectionListener listener;
    private final JMXServiceURL jmxURL;
    private final Map<String,?> optionsMap;

    private volatile MBeanServerConnection server = null;
    private volatile JMXConnector conn = null;
    private volatile ClassLoader defaultClassLoader = null;

    /**
     * Creates a new instance of {@code JMXRemoteNamespace}.
     * <p>
     * This constructor is provided for subclasses.
     * To create a new instance of {@code JMXRemoteNamespace} call
     * {@link #newJMXRemoteNamespace
     *  JMXRemoteNamespace.newJMXRemoteNamespace(sourceURL, optionsMap)}.
     * </p>
     * @param sourceURL a JMX service URL that can be used to {@linkplain
     *        #connect() connect} to the
     *        source MBean Server. The source MBean Server is the remote
     *        MBean Server which contains the MBeans that will be mirrored
     *        in this namespace.
     * @param optionsMap the options map that will be passed to the
     *        {@link JMXConnectorFactory} when {@linkplain
     *        JMXConnectorFactory#newJMXConnector creating} the
     *        {@link JMXConnector} used to {@linkplain #connect() connect}
     *        to the remote source MBean Server.  Can be null, which is
     *        equivalent to an empty map.
     * @see #newJMXRemoteNamespace JMXRemoteNamespace.newJMXRemoteNamespace
     * @see #connect
     */
    protected JMXRemoteNamespace(JMXServiceURL sourceURL,
            Map<String,?> optionsMap) {
         super(new JMXRemoteNamespaceDelegate());
        ((JMXRemoteNamespaceDelegate)super.getSourceServer()).
                initParentOnce(this);

        // URL must not be null.
        if (sourceURL == null)
            throw new IllegalArgumentException("Null URL");
        this.jmxURL     = sourceURL;
        this.broadcaster =
            new NotificationBroadcasterSupport(connectNotification);

        // handles options
        this.optionsMap = unmodifiableMap(optionsMap);

        // handles (dis)connection events
        this.listener = new ConnectionListener();
    }

    // returns un unmodifiable view of a map.
    private static <K,V> Map<K,V> unmodifiableMap(Map<K,V> aMap) {
        if (aMap == null || aMap.isEmpty())
            return Collections.emptyMap();
        return Collections.unmodifiableMap(aMap);
    }

   /**
    * Returns the {@code JMXServiceURL} that is (or will be) used to
    * connect to the remote name space. <p>
    * @see #connect
    * @return The {@code JMXServiceURL} used to connect to the remote
    *         name space.
    */
    public JMXServiceURL getJMXServiceURL() {
        return jmxURL;
    }

    /**
    * In this class, this method never returns {@code null}, and the
    * address returned is the {@link  #getJMXServiceURL JMXServiceURL}
    * that is used by  this object to {@linkplain #connect} to the remote
    * name space. <p>
    * This behaviour might be overriden by subclasses, if needed.
    * For instance, a subclass might want to return {@code null} if it
    * doesn't want to expose that JMXServiceURL.
    */
    public JMXServiceURL getAddress() {
        return getJMXServiceURL();
    }

    private Map<String,?> getEnvMap() {
        return optionsMap;
    }

    public void addNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback) {
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    /**
     * A subclass that needs to send its own notifications must override
     * this method in order to return an {@link MBeanNotificationInfo
     * MBeanNotificationInfo[]} array containing both its own notification
     * infos and the notification infos of its super class. <p>
     * The implementation should probably look like:
     * <pre>
     *      final MBeanNotificationInfo[] myOwnNotifs = { .... };
     *      final MBeanNotificationInfo[] parentNotifs =
     *            super.getNotificationInfo();
     *      final Set<MBeanNotificationInfo> mergedResult =
     *            new HashSet<MBeanNotificationInfo>();
     *      mergedResult.addAll(Arrays.asList(myOwnNotifs));
     *      mergedResult.addAll(Arrays.asList(parentNotifs));
     *      return mergeResult.toArray(
     *             new MBeanNotificationInfo[mergedResult.size()]);
     * </pre>
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
        return broadcaster.getNotificationInfo();
    }

    public void removeNotificationListener(NotificationListener listener)
    throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    public void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback)
            throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, handback);
    }

    private static long getNextSeqNumber() {
        return seqNumber.getAndIncrement();
    }


    /**
     * Sends a notification to registered listeners. Before the notification
     * is sent, the following steps are performed:
     * <ul><li>
     * If {@code n.getSequenceNumber() <= 0} set it to the next available
     * sequence number.</li>
     * <li>If {@code n.getSource() == null}, set it to the value returned by {@link
     * #getObjectName getObjectName()}.
     * </li></ul>
     * <p>This method can be called by subclasses in order to send their own
     *    notifications.
     *    In that case, these subclasses might also need to override
     *    {@link #getNotificationInfo} in order to declare their own
     *    {@linkplain MBeanNotificationInfo notification types}.
     * </p>
     * @param n The notification to send to registered listeners.
     * @see javax.management.NotificationBroadcasterSupport
     * @see #getNotificationInfo
     **/
    protected void sendNotification(Notification n) {
        if (n.getSequenceNumber()<=0)
            n.setSequenceNumber(getNextSeqNumber());
        if (n.getSource()==null)
            n.setSource(getObjectName());
        broadcaster.sendNotification(n);
    }

    private void checkState(ConnectionListener listener,
                            JMXConnectionNotification cn,
                            JMXConnector emittingConnector) {

        // Due to the asynchronous handling of notifications, it is
        // possible that this method is called for a JMXConnector
        // (or connection) which is already closed and replaced by a newer
        // one.
        //
        // This method attempts to determine the real state of the
        // connection - which might be different from what the notification
        // says.
        //
        // This is quite complex logic - because we try not to hold any
        // lock while evaluating the true value of the connected state,
        // while anyone might also call close() or connect() from a
        // different thread.
        // The method switchConnection() (called from here too) also has the
        // same kind of complex logic:
        //
        // We use the JMXConnector has a handback to the notification listener
        // (emittingConnector) in order to be able to determine whether the
        // notification concerns the current connector in use, or an older
        // one. The 'emittingConnector' is the connector from which the
        // notification originated. This could be an 'old' connector - as
        // closed() and connect() could already have been called before the
        // notification arrived. So what we do is to compare the
        // 'emittingConnector' with the current connector, to see if the
        // notification actually comes from the curent connector.
        //
        boolean remove = false;

        // whether the emittingConnector is already 'removed'
        synchronized (this) {
            if (this.conn != emittingConnector ||
                    JMXConnectionNotification.FAILED.equals(cn.getType()))
                remove = true;
        }

        // We need to unregister our listener from this 'removed' connector.
        // This is the only place where we remove the listener.
        //
        if (remove) {
            try {
                // This may fail if the connector is already closed.
                // But better unregister anyway...
                //
                emittingConnector.removeConnectionNotificationListener(
                        listener,null,
                        emittingConnector);
            } catch (Exception x) {
                LOG.log(Level.FINE,
                        "Failed to unregister connection listener"+x);
                LOG.log(Level.FINEST,
                        "Failed to unregister connection listener",x);
            }
            try {
                // This may fail if the connector is already closed.
                // But better call close twice and get an exception than
                // leaking...
                //
                emittingConnector.close();
            } catch (Exception x) {
                LOG.log(Level.FINEST,
                        "Failed to close old connector " +
                        "(failure was expected): "+x);
            }
        }

        // Now we checked whether our current connector is still alive.
        //
        boolean closed = false;
        final JMXConnector thisconn = this.conn;
        try {
            if (thisconn != null)
                thisconn.getConnectionId();
        } catch (IOException x) {
            LOG.finest("Connector already closed: "+x);
            closed = true;
        }

        // We got an IOException - the connector is not connected.
        // Need to forget it and switch our state to closed.
        //
        if (closed) {
            switchConnection(thisconn,null,null);
            try {
                // Usually this will fail... Better call close twice
                // and get an exception than leaking...
                //
                if (thisconn != emittingConnector || !remove)
                    thisconn.close();
            } catch (IOException x) {
                LOG.log(Level.FINEST,
                        "Failed to close connector (failure was expected): "
                        +x);
            }
        }
    }

    private final void switchConnection(JMXConnector oldc,
                                   JMXConnector newc,
                                   MBeanServerConnection mbs) {
        boolean connect = false;
        boolean close   = false;
        synchronized (this) {
            if (oldc != conn) {
                if (newc != null) {
                    try {
                        newc.close();
                    } catch (IOException x) {
                        LOG.log(Level.FINEST,
                                "Failed to close connector",x);
                    }
                }
                return;
            }
            if (conn == null && newc != null) connect=true;
            if (newc == null && conn != null) close = true;
            conn = newc;
            server = mbs;
        }
        if (connect || close) {
            boolean oldstate = close;
            boolean newstate = connect;
            final ObjectName myName = getObjectName();

            // In the uncommon case where the MBean is connected before
            // being registered, myName can be null...
            // If myName is null - we use 'this' as the source instead...
            //
            final Object source = (myName==null)?this:myName;
            final AttributeChangeNotification acn =
                    new AttributeChangeNotification(source,
                    getNextSeqNumber(),System.currentTimeMillis(),
                    String.valueOf(source)+
                    (newstate?" connected":" closed"),
                    "Connected",
                    "boolean",
                    Boolean.valueOf(oldstate),
                    Boolean.valueOf(newstate));
            sendNotification(acn);
        }
    }

    private void close(JMXConnector c) {
        try {
            if (c != null) c.close();
        } catch (Exception x) {
            // OK: we're gonna throw the original exception later.
            LOG.finest("Ignoring exception when closing connector: "+x);
        }
    }

    private JMXConnector connect(JMXServiceURL url, Map<String,?> env)
            throws IOException {
        final JMXConnector c = newJMXConnector(url, env);
        c.connect(env);
        return c;
    }

    /**
     * <p>Creates a new JMXConnector with the specified {@code url} and
     * {@code env} options map.  The default implementation of this method
     * returns {@link JMXConnectorFactory#newJMXConnector
     * JMXConnectorFactory.newJMXConnector(jmxURL, env)}.  Subclasses can
     * override this method to customize behavior.</p>
     *
     * @param url  The JMXServiceURL of the remote server.
     * @param optionsMap An options map that will be passed to the
     *        {@link JMXConnectorFactory} when {@linkplain
     *        JMXConnectorFactory#newJMXConnector creating} the
     *        {@link JMXConnector} that can connect to the remote source
     *        MBean Server.
     * @return A JMXConnector to use to connect to the remote server
     * @throws IOException if the connector could not be created.
     * @see JMXConnectorFactory#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
     * @see #JMXRemoteNamespace
     */
    protected JMXConnector newJMXConnector(JMXServiceURL url,
            Map<String,?> optionsMap) throws IOException {
        return JMXConnectorFactory.newJMXConnector(jmxURL, optionsMap);
    }

    /**
     * <p>Called when a new connection is established using {@link #connect}
     * so that subclasses can customize the connection.  The default
     * implementation of this method effectively does the following:</p>
     *
     * <pre>
     * MBeanServerConnection mbsc = {@link JMXConnector#getMBeanServerConnection()
     *                               jmxc.getMBeanServerConnection()};
     * try {
     *     return {@link ClientContext#withDynamicContext
     *             ClientContext.withDynamicContext(mbsc)};
     * } catch (IllegalArgumentException e) {
     *     return mbsc;
     * }
     * </pre>
     *
     * <p>In other words, it arranges for the client context to be forwarded
     * to the remote MBean Server if the remote MBean Server supports contexts;
     * otherwise it ignores the client context.</p>
     *
     * <h4>Example: connecting to a remote namespace</h4>
     *
     * <p>A subclass that wanted to narrow into a namespace of
     * the remote MBeanServer might look like this:</p>
     *
     * <pre>
     * class JMXRemoteSubNamespace extends JMXRemoteNamespace {
     *     private final String subnamespace;
     *
     *     JMXRemoteSubNamespace(
     *             JMXServiceURL url, Map{@code <String, ?>} env, String subnamespace) {
     *        super(url, env);
     *        this.subnamespace = subnamespace;
     *     }
     *
     *     {@code @Override}
     *     protected MBeanServerConnection getMBeanServerConnection(
     *             JMXConnector jmxc) throws IOException {
     *         MBeanServerConnection mbsc = super.getMBeanServerConnection(jmxc);
     *         return {@link JMXNamespaces#narrowToNamespace(MBeanServerConnection,String)
     *                 JMXNamespaces.narrowToNamespace(mbsc, subnamespace)};
     *     }
     * }
     * </pre>
     *
     * <h4>Example: using the Event Service for notifications</h4>
     *
     * <p>Some connectors may have been designed to work with an earlier
     * version of the JMX API, and may not have been upgraded to use
     * the {@linkplain javax.management.event Event Service} defined in
     * this version of the JMX API.  In that case, and if the remote
     * server to which this JMXRemoteNamespace connects also contains
     * namespaces, it may be necessary to configure explicitly an {@linkplain
     * javax.management.event.EventClientDelegate#newForwarder Event Client
     * Forwarder} on the remote server side, and to force the use of an {@link
     * EventClient} on this client side.</p>
     *
     * <p>A subclass of {@link JMXRemoteNamespace} can provide an
     * implementation of {@code getMBeanServerConnection} that will force
     * notification subscriptions to flow through an {@link EventClient} over
     * a legacy protocol.  It can do so by overriding this method in the
     * following way:</p>
     *
     * <pre>
     * class JMXRemoteEventClientNamespace extends JMXRemoteNamespace {
     *     JMXRemoteEventClientNamespace(JMXServiceURL url, {@code Map<String,?>} env) {
     *         super(url, env);
     *     }
     *
     *     {@code @Override}
     *     protected MBeanServerConnection getMBeanServerConnection(JMXConnector jmxc)
     *             throws IOException {
     *         MBeanServerConnection mbsc = super.getMBeanServerConnection(jmxc);
     *         return EventClient.getEventClientConnection(mbsc);
     *     }
     * }
     * </pre>
     *
     * <p>
     * Note that the remote server also needs to provide an {@link
     * javax.management.event.EventClientDelegateMBean}: configuring only
     * the client side (this object) is not enough.</p>
     *
     * <p>In summary, this technique should be used if the remote server
     * supports JMX namespaces, but uses a JMX Connector Server whose
     * implementation does not transparently use the new Event Service
     * (as would be the case with the JMXMPConnectorServer implementation
     * from the reference implementation of the JMX Remote API 1.0
     * specification).</p>
     *
     * @param jmxc the newly-created {@code JMXConnector}.
     *
     * @return an {@code MBeanServerConnection} connected to the remote
     * MBeanServer.
     *
     * @throws IOException if the connection cannot be made.  If this method
     * throws {@code IOException} then the calling {@link #connect()} method
     * will also fail with an {@code IOException}.
     *
     * @see #connect
     */
    protected MBeanServerConnection getMBeanServerConnection(JMXConnector jmxc)
            throws IOException {
        final MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        try {
            return ClientContext.withDynamicContext(mbsc);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.FINER, "ClientContext.withDynamicContext", e);
            return mbsc;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The sequence of events when this method is called includes,
     * effectively, the following code:</p>
     *
     * <pre>
     * JMXServiceURL url = {@link #getJMXServiceURL getJMXServiceURL}();
     * JMXConnector jmxc = {@link #newJMXConnector newJMXConnector}(url, env);
     * jmxc.connect();
     * MBeanServerConnection mbsc = {@link #getMBeanServerConnection(JMXConnector)
     *                               getMBeanServerConnection}(jmxc);
     * </pre>
     *
     * <p>Here, {@code env} is a {@code Map} containing the entries from the
     * {@code optionsMap} that was passed to the {@linkplain #JMXRemoteNamespace
     * constructor} or to the {@link #newJMXRemoteNamespace newJMXRemoteNamespace}
     * factory method.</p>
     *
     * <p>Subclasses can customize connection behavior by overriding the
     * {@code getJMXServiceURL}, {@code newJMXConnector}, or
     * {@code getMBeanServerConnection} methods.</p>
     */
    public void connect() throws IOException {
        LOG.fine("connecting...");
        final Map<String,Object> env =
                new HashMap<String,Object>(getEnvMap());
        try {
            // XXX: We should probably document this...
            // This allows to specify a loader name - which will be
            // retrieved from the parent MBeanServer.
            defaultClassLoader =
                EnvHelp.resolveServerClassLoader(env,getMBeanServer());
        } catch (InstanceNotFoundException x) {
            final IOException io =
                    new IOException("ClassLoader not found");
            io.initCause(x);
            throw io;
        }
        env.put(JMXConnectorFactory.DEFAULT_CLASS_LOADER,defaultClassLoader);
        final JMXServiceURL url = getJMXServiceURL();
        final JMXConnector aconn = connect(url,env);
        final MBeanServerConnection msc;
        try {
            msc = getMBeanServerConnection(aconn);
            aconn.addConnectionNotificationListener(listener,null,aconn);
        } catch (IOException io) {
            close(aconn);
            throw io;
        } catch (RuntimeException x) {
            close(aconn);
            throw x;
        }

        switchConnection(conn,aconn,msc);

        LOG.fine("connected.");
    }

    public void close() throws IOException {
        if (conn == null) return;
        LOG.fine("closing...");
        // System.err.println(toString()+": closing...");
        conn.close();
        // System.err.println(toString()+": connector closed");
        switchConnection(conn,null,null);
        LOG.fine("closed.");
        // System.err.println(toString()+": closed");
    }

    MBeanServerConnection getMBeanServerConnection() {
        if (conn == null)
            throw newRuntimeIOException("getMBeanServerConnection: not connected");
        return server;
    }

    // Better than throwing UndeclaredThrowableException ...
    private RuntimeException newRuntimeIOException(String msg) {
        final IllegalStateException illegal = new IllegalStateException(msg);
        return Util.newRuntimeIOException(new IOException(msg,illegal));
    }

    /**
     * Returns the default class loader used by the underlying
     * {@link JMXConnector}.
     * @return the default class loader used when communicating with the
     *         remote source MBean server.
     **/
    ClassLoader getDefaultClassLoader() {
        if (conn == null)
            throw newRuntimeIOException("getMBeanServerConnection: not connected");
        return defaultClassLoader;
    }

    public boolean isConnected() {
        // This is a pleonasm
        return (conn != null) && (server != null);
    }


    /**
     * This name space handler will automatically {@link #close} its
     * connection with the remote source in {@code preDeregister}.
     **/
    @Override
    public void preDeregister() throws Exception {
        try {
            close();
        } catch (IOException x) {
            LOG.fine("Failed to close properly - exception ignored: " + x);
            LOG.log(Level.FINEST,
                    "Failed to close properly - exception ignored",x);
        }
        super.preDeregister();
    }

   /**
    * This method calls {@link
    * javax.management.MBeanServerConnection#getMBeanCount
    * getMBeanCount()} on the remote namespace.
    * @throws java.io.IOException if an {@link IOException} is raised when
    *         communicating with the remote source namespace.
    */
    @Override
    public Integer getMBeanCount() throws IOException {
        return getMBeanServerConnection().getMBeanCount();
    }

   /**
    * This method returns the result of calling {@link
    * javax.management.MBeanServerConnection#getDomains
    * getDomains()} on the remote namespace.
    * @throws java.io.IOException if an {@link IOException} is raised when
    *         communicating with the remote source namespace.
    */
    @Override
   public String[] getDomains() throws IOException {
       return getMBeanServerConnection().getDomains();
    }

   /**
    * This method returns the result of calling {@link
    * javax.management.MBeanServerConnection#getDefaultDomain
    * getDefaultDomain()} on the remote namespace.
    * @throws java.io.IOException if an {@link IOException} is raised when
    *         communicating with the remote source namespace.
    */
    @Override
    public String getDefaultDomain() throws IOException {
        return getMBeanServerConnection().getDefaultDomain();
    }

    /**
     * Creates a new instance of {@code JMXRemoteNamespace}.
     * @param sourceURL a JMX service URL that can be used to connect to the
     *        source MBean Server. The source MBean Server is the remote
     *        MBean Server which contains the MBeans that will be mirrored
     *        in this namespace.
     * @param optionsMap An options map that will be passed to the
     *        {@link JMXConnectorFactory} when {@linkplain
     *        JMXConnectorFactory#newJMXConnector creating} the
     *        {@link JMXConnector} used to connect to the remote source
     *        MBean Server.  Can be null, which is equivalent to an empty map.
     * @see #JMXRemoteNamespace JMXRemoteNamespace(sourceURL,optionsMap)
     * @see JMXConnectorFactory#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
     */
     public static JMXRemoteNamespace newJMXRemoteNamespace(
             JMXServiceURL sourceURL,
             Map<String,?> optionsMap) {
         return new JMXRemoteNamespace(sourceURL, optionsMap);
     }
}
