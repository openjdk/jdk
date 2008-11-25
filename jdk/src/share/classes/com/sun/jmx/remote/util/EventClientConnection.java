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
 */

package com.sun.jmx.remote.util;

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.event.EventClientFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventClient;
import javax.management.event.EventClientDelegate;
import javax.management.namespace.JMXNamespaces;

/**
 * Class EventClientConnection - a {@link Proxy} that wraps an
 * {@link MBeanServerConnection} and an {@link EventClient}.
 * All methods are routed to the underlying {@code MBeanServerConnection},
 * except add/remove notification listeners which are routed to the
 * {@code EventClient}.
 * The caller only sees an {@code MBeanServerConnection} which uses an
 * {@code EventClient} behind the scenes.
 *
 * @author Sun Microsystems, Inc.
 */
public class EventClientConnection implements InvocationHandler,
        EventClientFactory {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NOTIFICATION_LOGGER;

    private static final int NAMESPACE_SEPARATOR_LENGTH =
            JMXNamespaces.NAMESPACE_SEPARATOR.length();

    /**
     * Creates a new {@code EventClientConnection}.
     * @param  connection The underlying MBeanServerConnection.
     */
    public EventClientConnection(MBeanServerConnection connection) {
        this(connection,null);
    }

    /**
     * Creates a new {@code EventClientConnection}.
     * @param connection The underlying MBeanServerConnection.
     * @param eventClientFactory a factory object that will be invoked
     *        to create an {@link EventClient} when needed.
     *        The {@code EventClient} is created lazily, when it is needed
     *        for the first time. If null, a default factory will be used
     *        (see {@link #createEventClient}).
     */
    public EventClientConnection(MBeanServerConnection connection,
                                 Callable<EventClient> eventClientFactory) {

        if (connection == null) {
            throw new IllegalArgumentException("Null connection");
        }
        this.connection = connection;
        if (eventClientFactory == null) {
            eventClientFactory = new Callable<EventClient>() {
                public final EventClient call() throws Exception {
                    return createEventClient(EventClientConnection.this.connection);
                }
            };
        }
        this.eventClientFactory = eventClientFactory;
        this.lock = new ReentrantLock();
     }

    /**
     * <p>The MBean server connection through which the methods of
     * a proxy using this handler are forwarded.</p>
     *
     * @return the MBean server connection.
     *
     * @since 1.6
     */
    public MBeanServerConnection getMBeanServerConnection() {
        return connection;
    }




    /**
     * Creates a new EventClientConnection proxy instance.
     *
     * @param <T> The underlying {@code MBeanServerConnection} - which should
     *        not be using the Event Service itself.
     * @param interfaceClass {@code MBeanServerConnection.class}, or a subclass.
     * @param eventClientFactory a factory used to create the EventClient.
     *        If null, a default factory is used (see {@link
     *        #createEventClient}).
     * @return the new proxy instance, which will route add/remove notification
     *         listener calls through an {@code EventClient}.
     *
     */
    private static <T extends MBeanServerConnection> T
            newProxyInstance(T connection,
            Class<T> interfaceClass, Callable<EventClient> eventClientFactory) {
        final InvocationHandler handler =
                new EventClientConnection(connection,eventClientFactory);
        final Class<?>[] interfaces =
                new Class<?>[] {interfaceClass, EventClientFactory.class};

        Object proxy =
                Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                interfaces,
                handler);
        return interfaceClass.cast(proxy);
    }


    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
        final String methodName = method.getName();

        // add/remove notification listener are routed to the EventClient
        if (methodName.equals("addNotificationListener")
            || methodName.equals("removeNotificationListener")) {
            final Class<?>[] sig = method.getParameterTypes();
            if (sig.length>1 &&
                    NotificationListener.class.isAssignableFrom(sig[1])) {
                return invokeBroadcasterMethod(proxy,method,args);
            }
        }

        // subscribe/unsubscribe are also routed to the EventClient.
        final Class<?> clazz = method.getDeclaringClass();
        if (clazz.equals(EventClientFactory.class)) {
            return invokeEventClientSubscriberMethod(proxy,method,args);
        }

        // local or not: equals, toString, hashCode
        if (shouldDoLocally(proxy, method))
            return doLocally(proxy, method, args);

        return call(connection,method,args);
    }

    // The purpose of this method is to unwrap InvocationTargetException,
    // in order to avoid throwing UndeclaredThrowableException for
    // declared exceptions.
    //
    // When calling method.invoke(), any exception thrown by the invoked
    // method will be wrapped in InvocationTargetException. If we don't
    // unwrap this exception, the proxy will always throw
    // UndeclaredThrowableException, even for runtime exceptions.
    //
    private Object call(final Object obj, final Method m,
            final Object[] args)
        throws Throwable {
        try {
            return m.invoke(obj,args);
        } catch (InvocationTargetException x) {
            final Throwable xx = x.getTargetException();
            if (xx == null) throw x;
            else throw xx;
        }
    }

    /**
     * Route add/remove notification listener to the event client.
     **/
    private Object invokeBroadcasterMethod(Object proxy, Method method,
                                           Object[] args) throws Exception {
        final String methodName = method.getName();
        final int nargs = (args == null) ? 0 : args.length;

        if (nargs < 1) {
           final String msg =
                    "Bad arg count: " + nargs;
           throw new IllegalArgumentException(msg);
        }

        final ObjectName mbean = (ObjectName) args[0];
        final EventClient evtClient = getEventClient();

        // Fails if evtClient is null AND the MBean we try to listen to is
        // in a subnamespace. We fail here because we know this will not
        // work.
        //
        // Note that if the wrapped MBeanServerConnection points to a an
        // earlier agent (JDK 1.6 or earlier), then the EventClient will
        // be null (we can't use the event service with earlier JDKs).
        //
        // In principle a null evtClient indicates that the remote VM is of
        // an earlier version, in which case it shouldn't contain any namespace.
        //
        // So having a null evtClient AND an MBean contained in a namespace is
        // clearly an error case.
        //
        if (evtClient == null) {
            final String domain = mbean.getDomain();
            final int index = domain.indexOf(JMXNamespaces.NAMESPACE_SEPARATOR);
            if (index > -1 && index <
                    (domain.length()-NAMESPACE_SEPARATOR_LENGTH)) {
                throw new UnsupportedOperationException(method.getName()+
                        " on namespace "+domain.substring(0,index+
                        NAMESPACE_SEPARATOR_LENGTH));
            }
        }

        if (methodName.equals("addNotificationListener")) {
            /* The various throws of IllegalArgumentException here
               should not happen, since we know what the methods in
               NotificationBroadcaster and NotificationEmitter
               are.  */
            if (nargs != 4) {
                final String msg =
                    "Bad arg count to addNotificationListener: " + nargs;
                throw new IllegalArgumentException(msg);
            }
            /* Other inconsistencies will produce ClassCastException
               below.  */

            final NotificationListener listener = (NotificationListener) args[1];
            final NotificationFilter filter = (NotificationFilter) args[2];
            final Object handback = args[3];

            if (evtClient != null) {
                // general case
                evtClient.addNotificationListener(mbean,listener,filter,handback);
            } else {
                // deprecated case. Only works for mbean in local namespace.
                connection.addNotificationListener(mbean,listener,filter,
                                                   handback);
            }
            return null;

        } else if (methodName.equals("removeNotificationListener")) {

            /* NullPointerException if method with no args, but that
               shouldn't happen because removeNL does have args.  */
            NotificationListener listener = (NotificationListener) args[1];

            switch (nargs) {
            case 2:
                if (evtClient != null) {
                    // general case
                    evtClient.removeNotificationListener(mbean,listener);
                } else {
                    // deprecated case. Only works for mbean in local namespace.
                    connection.removeNotificationListener(mbean, listener);
                }
                return null;

            case 4:
                NotificationFilter filter = (NotificationFilter) args[2];
                Object handback = args[3];
                if (evtClient != null) {
                    evtClient.removeNotificationListener(mbean,
                                                      listener,
                                                      filter,
                                                      handback);
                } else {
                    connection.removeNotificationListener(mbean,
                                                      listener,
                                                      filter,
                                                      handback);
                }
                return null;

            default:
                final String msg =
                    "Bad arg count to removeNotificationListener: " + nargs;
                throw new IllegalArgumentException(msg);
            }

        } else {
            throw new IllegalArgumentException("Bad method name: " +
                                               methodName);
        }
    }

    private boolean shouldDoLocally(Object proxy, Method method) {
        final String methodName = method.getName();
        if ((methodName.equals("hashCode") || methodName.equals("toString"))
            && method.getParameterTypes().length == 0
                && isLocal(proxy, method))
            return true;
        if (methodName.equals("equals")
            && Arrays.equals(method.getParameterTypes(),
                new Class<?>[] {Object.class})
                && isLocal(proxy, method))
            return true;
        return false;
    }

    private Object doLocally(Object proxy, Method method, Object[] args) {
        final String methodName = method.getName();

        if (methodName.equals("equals")) {

            if (this == args[0]) {
                return true;
            }

            if (!(args[0] instanceof Proxy)) {
                return false;
            }

            final InvocationHandler ihandler =
                Proxy.getInvocationHandler(args[0]);

            if (ihandler == null ||
                !(ihandler instanceof EventClientConnection)) {
                return false;
            }

            final EventClientConnection handler =
                (EventClientConnection)ihandler;

            return connection.equals(handler.connection) &&
                proxy.getClass().equals(args[0].getClass());
        } else if (methodName.equals("hashCode")) {
            return connection.hashCode();
        }

        throw new RuntimeException("Unexpected method name: " + methodName);
    }

    private static boolean isLocal(Object proxy, Method method) {
        final Class<?>[] interfaces = proxy.getClass().getInterfaces();
        if(interfaces == null) {
            return true;
        }

        final String methodName = method.getName();
        final Class<?>[] params = method.getParameterTypes();
        for (Class<?> intf : interfaces) {
            try {
                intf.getMethod(methodName, params);
                return false; // found method in one of our interfaces
            } catch (NoSuchMethodException nsme) {
                // OK.
            }
        }

        return true;  // did not find in any interface
    }

    /**
     * Return the EventClient used by this object. Can be null if the
     * remote VM is of an earlier JDK version which doesn't have the
     * event service.<br>
     * This method will invoke the event client factory the first time
     * it is called.
     **/
    public final EventClient getEventClient()  {
        if (initialized) return client;
        try {
            if (!lock.tryLock(TRYLOCK_TIMEOUT,TimeUnit.SECONDS))
                throw new IllegalStateException("can't acquire lock");
            try {
                client = eventClientFactory.call();
                initialized = true;
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new IllegalStateException("Can't create EventClient: "+x,x);
        }
        return client;
    }

    /**
     * Returns an event client for the wrapped {@code MBeanServerConnection}.
     * This is the method invoked by the default event client factory.
     * @param connection the  wrapped {@code MBeanServerConnection}.
     **/
    protected EventClient createEventClient(MBeanServerConnection connection)
        throws Exception {
        final ObjectName name =
           EventClientDelegate.OBJECT_NAME;
        if (connection.isRegistered(name)) {
            return new EventClient(connection);
        }
        return null;
    }

    /**
     * Creates a new {@link MBeanServerConnection} that goes through an
     * {@link EventClient} to receive/subscribe to notifications.
     * @param connection the underlying {@link MBeanServerConnection}.
     *        The given <code>connection</code> shouldn't be already
     *        using an {@code EventClient}.
     * @param eventClientFactory a factory object that will be invoked
     *        to create an {@link EventClient} when needed.
     *        The {@code EventClient} is created lazily, when it is needed
     *        for the first time. If null, a default factory will be used
     *        (see {@link #createEventClient}).
     * @return the MBeanServerConnection.
     **/
    public static MBeanServerConnection getEventConnectionFor(
                    MBeanServerConnection connection,
                    Callable<EventClient> eventClientFactory) {
        if (connection instanceof EventClientFactory
            && eventClientFactory != null)
            throw new IllegalArgumentException("connection already uses EventClient");

        if (connection instanceof EventClientFactory)
            return connection;

        // create a new proxy using an event client.
        //
        if (LOG.isLoggable(Level.FINE))
            LOG.fine("Creating EventClient for: "+connection);
        return newProxyInstance(connection,
                MBeanServerConnection.class,
                eventClientFactory);
    }

    private Object invokeEventClientSubscriberMethod(Object proxy,
            Method method, Object[] args) throws Throwable {
        return call(this,method,args);
    }

    // Maximum lock timeout in seconds. Obviously arbitrary.
    //
    private final static short TRYLOCK_TIMEOUT = 3;

    private final MBeanServerConnection connection;
    private final Callable<EventClient> eventClientFactory;
    private final Lock lock;
    private volatile EventClient client = null;
    private volatile boolean initialized = false;

}
