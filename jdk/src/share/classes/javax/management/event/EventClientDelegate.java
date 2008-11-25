/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 * @since JMX 2.0
 */

package javax.management.event;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.NotificationResult;
import com.sun.jmx.event.EventBuffer;
import com.sun.jmx.event.LeaseManager;
import com.sun.jmx.interceptor.SingleMBeanForwarder;
import com.sun.jmx.mbeanserver.Util;
import com.sun.jmx.remote.util.ClassLogger;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.MBeanPermission;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MBeanServerNotification;
import javax.management.ObjectInstance;
import javax.management.StandardMBean;
import javax.management.remote.MBeanServerForwarder;

/**
 * This is the default implementation of the MBean
 * {@link EventClientDelegateMBean}.
 */
public class EventClientDelegate implements EventClientDelegateMBean {

    private EventClientDelegate(MBeanServer server) {
        if (server == null) {
            throw new NullPointerException("Null MBeanServer.");
        }

        if (logger.traceOn()) {
            logger.trace("EventClientDelegate", "new one");
        }
        mbeanServer = server;
        eventSubscriber = EventSubscriber.getEventSubscriber(mbeanServer);
    }

    /**
     * Returns an {@code EventClientDelegate} instance for the given
     * {@code MBeanServer}.  Calling this method more than once with the same
     * {@code server} argument may return the same object or a different object
     * each time.  See {@link EventClientDelegateMBean} for an example use of
     * this method.
     *
     * @param server An MBean server instance to work with.
     * @return An {@code EventClientDelegate} instance.
     * @throws NullPointerException If {@code server} is null.
     */
    public static EventClientDelegate getEventClientDelegate(MBeanServer server) {
        EventClientDelegate delegate = null;
        synchronized(delegateMap) {
            final WeakReference<EventClientDelegate> wrf = delegateMap.get(server);
            delegate = (wrf == null) ? null : wrf.get();

            if (delegate == null) {
                delegate = new EventClientDelegate(server);
                try {
                    // TODO: this may not work with federated MBean, because
                    // the delegate will *not* emit notifications for those MBeans.
                    delegate.mbeanServer.addNotificationListener(
                            MBeanServerDelegate.DELEGATE_NAME,
                            delegate.cleanListener, null, null);
                } catch (InstanceNotFoundException e) {
                    logger.fine(
                            "getEventClientDelegate",
                            "Could not add MBeanServerDelegate listener", e);
                }
                delegateMap.put(server,
                                new WeakReference<EventClientDelegate>(delegate));
            }
        }

        return delegate;
    }

    // Logic for the MBeanServerForwarder that simulates the existence of the
    // EventClientDelegate MBean. Things are complicated by the fact that
    // there may not be anything in the chain after this forwarder when it is
    // created - the connection to a real MBeanServer might only come later.
    // Recall that there are two ways of creating a JMXConnectorServer -
    // either you specify its MBeanServer when you create it, or you specify
    // no MBeanServer and register it in an MBeanServer later. In the latter
    // case, the forwarder chain points nowhere until this registration
    // happens. Since EventClientDelegate wants to add a listener to the
    // MBeanServerDelegate, we can't create an EventClientDelegate until
    // there is an MBeanServer. So the forwarder initially has
    // a dummy ECD where every method throws an exception, and
    // the real ECD is created as soon as doing so does not produce an
    // exception.
    // TODO: rewrite so that the switch from the dummy to the real ECD happens
    // just before we would otherwise have thrown UnsupportedOperationException.
    // This is more correct, because it's not guaranteed that we will see the
    // moment where the real MBeanServer is attached, if it happens by virtue
    // of a setMBeanServer on some other forwarder later in the chain.

    private static class Forwarder extends SingleMBeanForwarder {
        private MBeanServer loopMBS;

        private static class UnsupportedInvocationHandler
                implements InvocationHandler {
            public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                throw new UnsupportedOperationException(
                        "EventClientDelegate unavailable: no MBeanServer, or " +
                        "MBeanServer inaccessible");
            }
        }

        private static DynamicMBean makeUnsupportedECD() {
            EventClientDelegateMBean unsupported = (EventClientDelegateMBean)
                Proxy.newProxyInstance(
                    EventClientDelegateMBean.class.getClassLoader(),
                    new Class<?>[] {EventClientDelegateMBean.class},
                    new UnsupportedInvocationHandler());
            return new StandardMBean(
                unsupported, EventClientDelegateMBean.class, false);
        }

        private volatile boolean madeECD;

        Forwarder() {
            super(OBJECT_NAME, makeUnsupportedECD(), true);
        }

        synchronized void setLoopMBS(MBeanServer loopMBS) {
            this.loopMBS = loopMBS;
        }

        @Override
        public synchronized void setMBeanServer(final MBeanServer mbs) {
            super.setMBeanServer(mbs);

            if (!madeECD) {
                try {
                    EventClientDelegate ecd =
                        AccessController.doPrivileged(
                            new PrivilegedAction<EventClientDelegate>() {
                                public EventClientDelegate run() {
                                    return getEventClientDelegate(loopMBS);
                                }
                            });
                    DynamicMBean mbean = new StandardMBean(
                            ecd, EventClientDelegateMBean.class, false);
                    setSingleMBean(mbean);
                    madeECD = true;
                } catch (Exception e) {
                    // OK: assume no MBeanServer
                    logger.fine("setMBeanServer", "isRegistered", e);
                }
            }
        }
    }

    /**
     * <p>Create a new {@link MBeanServerForwarder} that simulates the existence
     * of an {@code EventClientDelegateMBean} with the {@linkplain
     * #OBJECT_NAME default name}.  This forwarder intercepts MBean requests
     * that are targeted for that MBean and handles them itself.  All other
     * requests are forwarded to the next element in the forwarder chain.</p>
     *
     * @param nextMBS the next {@code MBeanServer} in the chain of forwarders,
     * which might be another {@code MBeanServerForwarder} or a plain {@code
     * MBeanServer}.  This is the object to which {@code MBeanServer} requests
     * that do not concern the {@code EventClientDelegateMBean} are sent.
     * It will be the value of {@link MBeanServerForwarder#getMBeanServer()
     * getMBeanServer()} on the returned object, and can be changed with {@link
     * MBeanServerForwarder#setMBeanServer setMBeanServer}.  It can be null but
     * must be set to a non-null value before any {@code MBeanServer} requests
     * arrive.
     *
     * @param loopMBS the {@code MBeanServer} to which requests from the
     * {@code EventClientDelegateMBean} should be sent.  For example,
     * when you invoke the {@link EventClientDelegateMBean#addListener
     * addListener} operation on the {@code EventClientDelegateMBean}, it will
     * result in a call to {@link
     * MBeanServer#addNotificationListener(ObjectName, NotificationListener,
     * NotificationFilter, Object) addNotificationListener} on this object.
     * If this parameter is null, then these requests will be sent to the
     * newly-created {@code MBeanServerForwarder}.  Usually the parameter will
     * either be null or will be the result of {@link
     * javax.management.remote.JMXConnectorServer#getSystemMBeanServerForwarder()
     * getSystemMBeanServerForwarder()} for the connector server in which
     * this forwarder will be installed.
     *
     * @return a new {@code MBeanServerForwarder} that simulates the existence
     * of an {@code EventClientDelegateMBean}.
     *
     * @see javax.management.remote.JMXConnectorServer#installStandardForwarders
     */
    public static MBeanServerForwarder newForwarder(
            MBeanServer nextMBS, MBeanServer loopMBS) {
        Forwarder mbsf = new Forwarder();
        // We must setLoopMBS before setMBeanServer, because when we
        // setMBeanServer that will call getEventClientDelegate(loopMBS).
        if (loopMBS == null)
            loopMBS = mbsf;
        mbsf.setLoopMBS(loopMBS);
        if (nextMBS != null)
            mbsf.setMBeanServer(nextMBS);
        return mbsf;
    }

    /**
     * Returns a proxy of the default {@code EventClientDelegateMBean}.
     *
     * @param conn An {@link MBeanServerConnection} to work with.
     */
    @SuppressWarnings("cast") // cast for jdk 1.5
    public static EventClientDelegateMBean getProxy(MBeanServerConnection conn) {
        return  (EventClientDelegateMBean)MBeanServerInvocationHandler.
                newProxyInstance(conn,
                OBJECT_NAME,
                EventClientDelegateMBean.class,
                false);
    }

    public String addClient(String className, Object[] params, String[] sig)
    throws MBeanException {
        return addClient(className, null, params, sig, true);
    }

    public String addClient(String className,
            ObjectName classLoader,
            Object[] params,
            String[] sig) throws MBeanException {
        return addClient(className, classLoader, params, sig, false);
    }

    private String addClient(String className,
            ObjectName classLoader,
            Object[] params,
            String[] sig,
            boolean classLoaderRepository) throws MBeanException {
        try {
            return addClientX(
                    className, classLoader, params, sig, classLoaderRepository);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new MBeanException(e);
        }
    }

    private String addClientX(String className,
            ObjectName classLoader,
            Object[] params,
            String[] sig,
            boolean classLoaderRepository) throws Exception {
        if (className == null) {
            throw new IllegalArgumentException("Null class name.");
        }

        final Object o;

        // The special treatment of standard EventForwarders is so that no
        // special permissions are necessary to use them.  Otherwise you
        // couldn't use EventClient if you didn't have permission to call
        // MBeanServer.instantiate.  We do require that permission for
        // non-standard forwarders, because otherwise you could instantiate
        // any class with possibly adverse consequences.  We also avoid using
        // MBeanInstantiator because it looks up constructors by loading each
        // class in the sig array, which means a remote user could cause any
        // class to be loaded.  That's probably not hugely risky but still.
        if (className.startsWith("javax.management.event.")) {
            Class<?> c = Class.forName(
                    className, false, this.getClass().getClassLoader());
            Constructor<?> foundCons = null;
            if (sig == null)
                sig = new String[0];
            for (Constructor<?> cons : c.getConstructors()) {
                Class<?>[] types = cons.getParameterTypes();
                String[] consSig = new String[types.length];
                for (int i = 0; i < types.length; i++)
                    consSig[i] = types[i].getName();
                if (Arrays.equals(sig, consSig)) {
                    foundCons = cons;
                    break;
                }
            }
            if (foundCons == null) {
                throw new NoSuchMethodException(
                        "Constructor for " + className + " with argument types " +
                        Arrays.toString(sig));
            }
            o = foundCons.newInstance(params);
        } else if (classLoaderRepository) {
            o = mbeanServer.instantiate(className, params, sig);
        } else {
            o = mbeanServer.instantiate(className, classLoader, params, sig);
        }

        if (!(o instanceof EventForwarder)) {
            throw new IllegalArgumentException(
                    className+" is not an EventForwarder class.");
        }

        final EventForwarder forwarder = (EventForwarder)o;
        final String clientId = UUID.randomUUID().toString();
        ClientInfo clientInfo = new ClientInfo(clientId, forwarder);

        clientInfoMap.put(clientId, clientInfo);

        forwarder.setClientId(clientId);

        if (logger.traceOn()) {
            logger.trace("addClient", clientId);
        }

        return clientId;
    }

    public Integer[] getListenerIds(String clientId)
    throws IOException, EventClientNotFoundException {
        ClientInfo clientInfo = getClientInfo(clientId);

        if (clientInfo == null) {
            throw new EventClientNotFoundException("The client is not found.");
        }

        Map<Integer, AddedListener> listenerInfoMap = clientInfo.listenerInfoMap;
        synchronized (listenerInfoMap) {
            Set<Integer> ids = listenerInfoMap.keySet();
            return ids.toArray(new Integer[ids.size()]);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The execution of this method includes a call to
     * {@link MBeanServer#addNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)}.</p>
     */
    public Integer addListener(String clientId,
            final ObjectName name,
            NotificationFilter filter)
            throws EventClientNotFoundException, InstanceNotFoundException {

        if (logger.traceOn()) {
            logger.trace("addListener", "");
        }

        return getClientInfo(clientId).addListenerInfo(name, filter);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The execution of this method can include call to
     * {@link MBeanServer#removeNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)}.</p>
     */
    public void removeListenerOrSubscriber(String clientId, Integer listenerId)
    throws InstanceNotFoundException,
            ListenerNotFoundException,
            EventClientNotFoundException,
            IOException {
        if (logger.traceOn()) {
            logger.trace("removeListener", ""+listenerId);
        }
        getClientInfo(clientId).removeListenerInfo(listenerId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The execution of this method includes a call to
     * {@link MBeanServer#addNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)} for
     * every MBean matching {@code name}.  If {@code name} is
     * an {@code ObjectName} pattern, then the execution of this
     * method will include a call to {@link MBeanServer#queryNames}.</p>
     */
    public Integer addSubscriber(String clientId, ObjectName name,
            NotificationFilter filter)
            throws EventClientNotFoundException, IOException {
        if (logger.traceOn()) {
            logger.trace("addSubscriber", "");
        }
        return getClientInfo(clientId).subscribeListenerInfo(name, filter);
    }

    public NotificationResult fetchNotifications(String clientId,
            long startSequenceNumber,
            int maxNotifs,
            long timeout)
            throws EventClientNotFoundException {
        if (logger.traceOn()) {
            logger.trace("fetchNotifications", "for "+clientId);
        }
        return getClientInfo(clientId).fetchNotifications(startSequenceNumber,
                maxNotifs,
                timeout);
    }

    public void removeClient(String clientId)
    throws EventClientNotFoundException {
        if (clientId == null)
            throw new EventClientNotFoundException("Null clientId");
        if (logger.traceOn()) {
            logger.trace("removeClient", clientId);
        }
        ClientInfo ci = null;
        ci = clientInfoMap.remove(clientId);

        if (ci == null) {
            throw new EventClientNotFoundException("clientId is "+clientId);
        } else {
            ci.clean();
        }
    }

    public long lease(String clientId, long timeout)
    throws IOException, EventClientNotFoundException {
        if (logger.traceOn()) {
            logger.trace("lease", "for "+clientId);
        }
        return getClientInfo(clientId).lease(timeout);
    }

    // ------------------------------------
    // private classes
    // ------------------------------------
    private class ClientInfo {
        final String clientId;
        final NotificationListener clientListener;
        final Map<Integer, AddedListener> listenerInfoMap =
                new HashMap<Integer, AddedListener>();

        ClientInfo(String clientId, EventForwarder forwarder) {
            this.clientId = clientId;
            this.forwarder = forwarder;
            clientListener =
                    new ForwardingClientListener(listenerInfoMap, forwarder);
        }

        Integer addOrSubscribeListenerInfo(
                ObjectName name, NotificationFilter filter, boolean subscribe)
                throws InstanceNotFoundException, IOException {

            final Integer listenerId = nextListenerId();
            AddedListener listenerInfo = new AddedListener(
                    listenerId, filter, name, subscribe);
            if (subscribe) {
                eventSubscriber.subscribe(name,
                        clientListener,
                        filter,
                        listenerInfo);
            } else {
                mbeanServer.addNotificationListener(name,
                        clientListener,
                        filter,
                        listenerInfo);
            }

            synchronized(listenerInfoMap) {
                listenerInfoMap.put(listenerId, listenerInfo);
            }

            return listenerId;
        }

        Integer addListenerInfo(ObjectName name,
                NotificationFilter filter) throws InstanceNotFoundException {
            try {
                return addOrSubscribeListenerInfo(name, filter, false);
            } catch (IOException e) { // can't happen
                logger.warning(
                        "EventClientDelegate.addListenerInfo",
                        "unexpected exception", e);
                throw new RuntimeException(e);
            }
        }

        Integer subscribeListenerInfo(ObjectName name,
                NotificationFilter filter) throws IOException {
            try {
                return addOrSubscribeListenerInfo(name, filter, true);
            } catch (InstanceNotFoundException e) { // can't happen
                logger.warning(
                        "EventClientDelegate.subscribeListenerInfo",
                        "unexpected exception", e);
                throw new RuntimeException(e);
            }
        }

        private final AtomicInteger nextListenerId = new AtomicInteger();

        private Integer nextListenerId() {
            return nextListenerId.getAndIncrement();
        }

        NotificationResult fetchNotifications(long startSequenceNumber,
                int maxNotifs,
                long timeout) {

            if (!(forwarder instanceof FetchingEventForwarder)) {
                throw new IllegalArgumentException(
                        "This client is using Event Postal Service!");
            }

            return ((FetchingEventForwarder)forwarder).
                    fetchNotifications(startSequenceNumber,
                        maxNotifs, timeout);
        }

        void removeListenerInfo(Integer listenerId)
        throws InstanceNotFoundException, ListenerNotFoundException, IOException {
            AddedListener listenerInfo;
            synchronized(listenerInfoMap) {
                listenerInfo = listenerInfoMap.remove(listenerId);
            }

            if (listenerInfo == null) {
                throw new ListenerNotFoundException("The listener is not found.");
            }

            if (listenerInfo.subscription) {
                eventSubscriber.unsubscribe(listenerInfo.name,
                        clientListener);
            } else {
                mbeanServer.removeNotificationListener(listenerInfo.name,
                        clientListener,
                        listenerInfo.filter,
                        listenerInfo);
            }
        }

        void clean(ObjectName name) {
            synchronized(listenerInfoMap) {
                for (Map.Entry<Integer, AddedListener> entry :
                        listenerInfoMap.entrySet()) {
                    AddedListener li = entry.getValue();
                    if (name.equals(li.name)) {
                        listenerInfoMap.remove(entry.getKey());
                    }
                }
            }
        }

        void clean() {
            synchronized(listenerInfoMap) {
                for (AddedListener li : listenerInfoMap.values()) {
                    try {
                        mbeanServer.removeNotificationListener(li.name,
                                clientListener);
                    } catch (Exception e) {
                        logger.trace("ClientInfo.clean", "removeNL", e);
                    }
                }
                listenerInfoMap.clear();
            }

            try {
                forwarder.close();
            } catch (Exception e) {
                logger.trace(
                        "ClientInfo.clean", "forwarder.close", e);
            }

            if (leaseManager != null) {
                leaseManager.stop();
            }
        }

        long lease(long timeout) {
            return leaseManager.lease(timeout);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o instanceof ClientInfo &&
                    clientId.equals(((ClientInfo)o).clientId)) {
                return true;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return clientId.hashCode();
        }

        private EventForwarder forwarder = null;

        private final Runnable leaseExpiryCallback = new Runnable() {
            public void run() {
                try {
                    removeClient(clientId);
                } catch (Exception e) {
                    logger.trace(
                            "ClientInfo.leaseExpiryCallback", "removeClient", e);
                }
            }
        };

        private LeaseManager leaseManager = new LeaseManager(leaseExpiryCallback);
    }

    private class ForwardingClientListener implements NotificationListener {
        public ForwardingClientListener(Map<Integer, AddedListener> listenerInfoMap,
                EventForwarder forwarder) {
            this.listenerInfoMap = listenerInfoMap;
            this.forwarder = forwarder;
        }

        public void handleNotification(Notification n, Object o) {
            if (n == null || (!(o instanceof AddedListener))) {
                if (logger.traceOn()) {
                    logger.trace("ForwardingClientListener-handleNotification",
                            "received a unknown notif");
                }
                return;
            }

            AddedListener li = (AddedListener) o;

            if (checkListenerPermission(li.name,li.acc)) {
                try {
                    forwarder.forward(n, li.listenerId);
                } catch (Exception e) {
                    if (logger.traceOn()) {
                        logger.trace(
                                "ForwardingClientListener-handleNotification",
                                "forwarding failed.", e);
                    }
                }
            }
        }

        private final Map<Integer, AddedListener> listenerInfoMap;
        private final EventForwarder forwarder;
    }

    private class AddedListener {
        final int listenerId;
        final NotificationFilter filter;
        final ObjectName name;
        final boolean subscription;
        final AccessControlContext acc;

        public AddedListener(
                int listenerId,
                NotificationFilter filter,
                ObjectName name,
                boolean subscription) {
            this.listenerId = listenerId;
            this.filter = filter;
            this.name = name;
            this.subscription = subscription;
            acc = AccessController.getContext();
        }
    }

    private class CleanListener implements NotificationListener {
        public void handleNotification(Notification notification,
                Object handback) {
            if (notification instanceof MBeanServerNotification) {
                if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(
                        notification.getType())) {
                    final ObjectName name =
                            ((MBeanServerNotification)notification).getMBeanName();

                    final Collection <ClientInfo> list =
                            Collections.unmodifiableCollection(clientInfoMap.values());

                    for (ClientInfo ci : list) {
                        ci.clean(name);
                    }
                }

            }
        }
    }

    // -------------------------------------------------
    // private method
    // -------------------------------------------------
    private ClientInfo getClientInfo(String clientId)
    throws EventClientNotFoundException {
        ClientInfo clientInfo = null;
        clientInfo = clientInfoMap.get(clientId);

        if (clientInfo == null) {
            throw new EventClientNotFoundException(
                    "Client not found (id " + clientId + ")");
        }

        return clientInfo;
    }

    /**
     * Explicitly check the MBeanPermission for
     * the current access control context.
     */
    private boolean checkListenerPermission(final ObjectName name,
            final AccessControlContext acc) {
        if (logger.traceOn()) {
            logger.trace("checkListenerPermission", "");
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                final String serverName = getMBeanServerName();

                ObjectInstance oi = (ObjectInstance)
                    AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Object>() {
                    public Object run()
                    throws InstanceNotFoundException {
                        return mbeanServer.getObjectInstance(name);
                    }
                });

                String classname = oi.getClassName();
                MBeanPermission perm = new MBeanPermission(
                        serverName,
                        classname,
                        null,
                        name,
                        "addNotificationListener");
                sm.checkPermission(perm, acc);
            } catch (Exception e) {
                if (logger.debugOn()) {
                    logger.debug("checkListenerPermission", "refused.", e);
                }
                return false;
            }
        }
        return true;
    }

    private String getMBeanServerName() {
        if (mbeanServerName != null) return mbeanServerName;
        else return (mbeanServerName = getMBeanServerName(mbeanServer));
    }

    private static String getMBeanServerName(final MBeanServer server) {
        final PrivilegedAction<String> action = new PrivilegedAction<String>() {
            public String run() {
                return Util.getMBeanServerSecurityName(server);
            }
        };
        return AccessController.doPrivileged(action);
    }

    // ------------------------------------
    // private variables
    // ------------------------------------
    private final MBeanServer mbeanServer;
    private volatile String mbeanServerName = null;
    private Map<String, ClientInfo> clientInfoMap =
            new ConcurrentHashMap<String, ClientInfo>();

    private final CleanListener cleanListener = new CleanListener();
    private final EventSubscriber eventSubscriber;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventClientDelegate");

    private static final
            Map<MBeanServer, WeakReference<EventClientDelegate>> delegateMap =
            new WeakHashMap<MBeanServer, WeakReference<EventClientDelegate>>();
}
