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

package com.sun.jmx.namespace;

import com.sun.jmx.defaults.JmxProperties;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegateMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/**
 * A collection of methods that provide JMXConnector wrappers for
 * JMXRemoteNamepaces underlying connectors.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public final class JMXNamespaceUtils {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;


    private static <K,V> Map<K,V> newWeakHashMap() {
        return new WeakHashMap<K,V>();
    }

    /** There are no instances of this class */
    private JMXNamespaceUtils() {
    }

    // returns un unmodifiable view of a map.
    public static <K,V> Map<K,V> unmodifiableMap(Map<K,V> aMap) {
        if (aMap == null || aMap.isEmpty())
            return Collections.emptyMap();
        return Collections.unmodifiableMap(aMap);
    }


    /**
     * A base class that helps writing JMXConnectors that return
     * MBeanServerConnection wrappers.
     * This base class wraps an inner JMXConnector (the source), and preserve
     * its caching policy. If a connection is cached in the source, its wrapper
     * will be cached in this connector too.
     * Author's note: rewriting this with java.lang.reflect.Proxy could be
     * envisaged. It would avoid the combinatory sub-classing introduced by
     * JMXAddressable.
     * <p>
     * Note: all the standard JMXConnector implementations are serializable.
     *       This implementation here is not. Should it be?
     *       I believe it must not be serializable unless it becomes
     *       part of a public API (either standard or officially exposed
     *       and supported in a documented com.sun package)
     **/
     static class JMXCachingConnector
            implements JMXConnector  {

        // private static final long serialVersionUID = -2279076110599707875L;

        final JMXConnector source;

        // if this object is made serializable, then the variable below
        // needs to become volatile transient and be lazyly-created...
        private final
                Map<MBeanServerConnection,MBeanServerConnection> connectionMap;


        public JMXCachingConnector(JMXConnector source) {
            this.source = checkNonNull(source, "source");
            connectionMap = newWeakHashMap();
        }

        private MBeanServerConnection
                getCached(MBeanServerConnection inner) {
            return connectionMap.get(inner);
        }

        private MBeanServerConnection putCached(final MBeanServerConnection inner,
                final MBeanServerConnection wrapper) {
            if (inner == wrapper) return wrapper;
            synchronized (this) {
                final MBeanServerConnection concurrent =
                        connectionMap.get(inner);
                if (concurrent != null) return concurrent;
                connectionMap.put(inner,wrapper);
            }
            return wrapper;
        }

        public void addConnectionNotificationListener(NotificationListener
                listener, NotificationFilter filter, Object handback) {
            source.addConnectionNotificationListener(listener,filter,handback);
        }

        public void close() throws IOException {
            source.close();
        }

        public void connect() throws IOException {
            source.connect();
        }

        public void connect(Map<String,?> env) throws IOException {
            source.connect(env);
        }

        public String getConnectionId() throws IOException {
            return source.getConnectionId();
        }

        /**
         * Preserve caching policy of the underlying connector.
         **/
        public MBeanServerConnection
                getMBeanServerConnection() throws IOException {
            final MBeanServerConnection inner =
                    source.getMBeanServerConnection();
            final MBeanServerConnection cached = getCached(inner);
            if (cached != null) return cached;
            final MBeanServerConnection wrapper = wrap(inner);
            return putCached(inner,wrapper);
        }

        public MBeanServerConnection
                getMBeanServerConnection(Subject delegationSubject)
                throws IOException {
            final MBeanServerConnection wrapped =
                    source.getMBeanServerConnection(delegationSubject);
            synchronized (this) {
                final MBeanServerConnection cached = getCached(wrapped);
                if (cached != null) return cached;
                final MBeanServerConnection wrapper =
                    wrapWithSubject(wrapped,delegationSubject);
                return putCached(wrapped,wrapper);
            }
        }

        public void removeConnectionNotificationListener(
                NotificationListener listener)
                throws ListenerNotFoundException {
            source.removeConnectionNotificationListener(listener);
        }

        public void removeConnectionNotificationListener(
                NotificationListener l, NotificationFilter f,
                Object handback) throws ListenerNotFoundException {
            source.removeConnectionNotificationListener(l,f,handback);
        }

        /**
         * This is the method that subclass will redefine. This method
         * is called by {@code this.getMBeanServerConnection()}.
         * {@code inner} is the connection returned by
         * {@code source.getMBeanServerConnection()}.
         **/
        protected MBeanServerConnection wrap(MBeanServerConnection inner)
            throws IOException {
            return inner;
        }

        /**
         * Subclass may also want to redefine this method.
         * By default it calls wrap(inner). This method
         * is called by {@code this.getMBeanServerConnection(Subject)}.
         * {@code inner} is the connection returned by
         * {@code source.getMBeanServerConnection(Subject)}.
         **/
        protected MBeanServerConnection wrapWithSubject(
                MBeanServerConnection inner, Subject delegationSubject)
            throws IOException {
                return wrap(inner);
        }

        @Override
        public String toString() {
            if (source instanceof JMXAddressable) {
                final JMXServiceURL address =
                        ((JMXAddressable)source).getAddress();
                if (address != null)
                    return address.toString();
            }
            return source.toString();
        }

    }


    /**
     * The name space connector can do 'cd'
     **/
    static class JMXNamespaceConnector extends JMXCachingConnector {

        // private static final long serialVersionUID = -4813611540843020867L;

        private final String toDir;
        private final boolean closeable;

        public JMXNamespaceConnector(JMXConnector source, String toDir,
                boolean closeable) {
            super(source);
            this.toDir = toDir;
            this.closeable = closeable;
        }

        @Override
        public void close() throws IOException {
            if (!closeable)
                throw new UnsupportedOperationException("close");
            else super.close();
        }

        @Override
        protected MBeanServerConnection wrap(MBeanServerConnection wrapped)
               throws IOException {
            if (LOG.isLoggable(Level.FINER))
                LOG.finer("Creating name space proxy connection for source: "+
                        "namespace="+toDir);
            return JMXNamespaces.narrowToNamespace(wrapped,toDir);
        }

        @Override
        public String toString() {
            return "JMXNamespaces.narrowToNamespace("+
                    super.toString()+
                    ", \""+toDir+"\")";
        }

    }

    static class JMXEventConnector extends JMXCachingConnector {

        // private static final long serialVersionUID = 4742659236340242785L;

        JMXEventConnector(JMXConnector wrapped) {
            super(wrapped);
        }

        @Override
        protected MBeanServerConnection wrap(MBeanServerConnection inner)
                throws IOException {
            return EventClient.getEventClientConnection(inner);
        }


        @Override
        public String toString() {
            return "EventClient.withEventClient("+super.toString()+")";
        }
    }

    static class JMXAddressableEventConnector extends JMXEventConnector
        implements JMXAddressable {

        // private static final long serialVersionUID = -9128520234812124712L;

        JMXAddressableEventConnector(JMXConnector wrapped) {
            super(wrapped);
        }

        public JMXServiceURL getAddress() {
            return ((JMXAddressable)source).getAddress();
        }
    }

    /**
     * Creates a connector whose MBeamServerConnection will point to the
     * given sub name space inside the source connector.
     * @see JMXNamespace
     **/
    public static JMXConnector cd(final JMXConnector source,
                                  final String toNamespace,
                                  final boolean closeable)
        throws IOException {

        checkNonNull(source, "JMXConnector");

        if (toNamespace == null || toNamespace.equals(""))
            return source;

        return new JMXNamespaceConnector(source,toNamespace,closeable);
    }


    /**
     * Returns a JMX Connector that will use an {@link EventClient}
     * to subscribe for notifications. If the server doesn't have
     * an {@link EventClientDelegateMBean}, then the connector will
     * use the legacy notification mechanism instead.
     *
     * @param source The underlying JMX Connector wrapped by the returned
     *               connector.
     * @return A JMX Connector that will uses an {@link EventClient}, if
     *         available.
     * @see EventClient#getEventClientConnection(MBeanServerConnection)
     */
    public static JMXConnector withEventClient(final JMXConnector source) {
        checkNonNull(source, "JMXConnector");
        if (source instanceof JMXAddressable)
            return new JMXAddressableEventConnector(source);
        else
            return new JMXEventConnector(source);
    }

    public static <T> T checkNonNull(T parameter, String name) {
        if (parameter == null)
            throw new IllegalArgumentException(name+" must not be null");
         return parameter;
    }


}
