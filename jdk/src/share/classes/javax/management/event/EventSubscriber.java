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

package javax.management.event;

import com.sun.jmx.remote.util.ClassLogger;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryEval;
import javax.management.QueryExp;

/**
 * <p>An object that can be used to subscribe for notifications from all MBeans
 * in an MBeanServer that match a pattern.  For example, to listen for
 * notifications from all MBeans in the MBeanServer {@code mbs} that match
 * {@code com.example:type=Controller,name=*} you could write:</p>
 *
 * <pre>
 * EventSubscriber subscriber = EventSubscriber.getEventSubscriber(mbs);
 * ObjectName pattern = new ObjectName("com.example:type=Controller,name=*");
 * NotificationListener myListener = ...;
 * NotificationFilter myFilter = null;  // or whatever
 * Object handback = null;              // or whatever
 * subscriber.subscribe(pattern, myListener, myFilter, myHandback);
 * </pre>
 */
public class EventSubscriber implements EventConsumer {
    /**
     * Returns an {@code EventSubscriber} object to subscribe for notifications
     * from the given {@code MBeanServer}.  Calling this method more
     * than once with the same parameter may or may not return the same object.
     *
     * @param mbs the {@code MBeanServer} containing MBeans to be subscribed to.
     * @return An {@code EventSubscriber} object.
     *
     * @throws NullPointerException if mbs is null.
     */
    public static EventSubscriber getEventSubscriber(MBeanServer mbs) {
        if (mbs == null)
            throw new NullPointerException("Null MBeanServer");

        EventSubscriber eventSubscriber = null;
        synchronized (subscriberMap) {
            final WeakReference<EventSubscriber> wrf = subscriberMap.get(mbs);
            eventSubscriber = (wrf == null) ? null : wrf.get();

            if (eventSubscriber == null) {
                eventSubscriber = new EventSubscriber(mbs);

                subscriberMap.put(mbs,
                        new WeakReference<EventSubscriber>(eventSubscriber));
            }
        }

        return eventSubscriber;
    }

    private EventSubscriber(final MBeanServer mbs) {
        logger.trace("EventSubscriber", "create a new one");
        this.mbeanServer = mbs;

        Exception x = null;
        try {
            AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Void>() {
                public Void run() throws Exception {
                    mbs.addNotificationListener(
                            MBeanServerDelegate.DELEGATE_NAME,
                            myMBeanServerListener, null, null);
                    return null;
                }
            });
        } catch (PrivilegedActionException ex) {
            x = ex.getException();
        }

        // handle possible exceptions.
        //
        // Fail unless x is null or x is instance of InstanceNotFoundException
        // The logic here is that if the MBeanServerDelegate is not present,
        // we will assume that the connection will not emit any
        // MBeanServerNotifications.
        //
        if (x != null && !(x instanceof InstanceNotFoundException)) {
            if (x instanceof RuntimeException)
                throw (RuntimeException) x;
            throw new RuntimeException(
                    "Can't add listener to MBean server delegate: " + x, x);
        }
    }

    public void subscribe(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback)
            throws IOException {

        if (logger.traceOn())
            logger.trace("subscribe", "" + name);

        if (name == null)
            throw new IllegalArgumentException("Null MBean name");

        if (listener == null)
            throw new IllegalArgumentException("Null listener");

        final MyListenerInfo li = new MyListenerInfo(listener, filter, handback);
        List<MyListenerInfo> list;

        Map<ObjectName, List<MyListenerInfo>> map;
        Set<ObjectName> names;
        if (name.isPattern()) {
            map = patternSubscriptionMap;
            names = mbeanServer.queryNames(name, notificationBroadcasterExp);
        } else {
            map = exactSubscriptionMap;
            names = Collections.singleton(name);
        }

        synchronized (map) {
            list = map.get(name);
            if (list == null) {
                list = new ArrayList<MyListenerInfo>();
                map.put(name, list);
            }
            list.add(li);
        }

        for (ObjectName mbeanName : names) {
            try {
                mbeanServer.addNotificationListener(mbeanName,
                                                    listener,
                                                    filter,
                                                    handback);
            } catch (Exception e) {
                logger.fine("subscribe", "addNotificationListener", e);
            }
        }
    }

    public void unsubscribe(ObjectName name,
            NotificationListener listener)
            throws ListenerNotFoundException, IOException {
        if (logger.traceOn())
            logger.trace("unsubscribe", "" + name);

        if (name == null)
            throw new IllegalArgumentException("Null MBean name");

        if (listener == null)
            throw new ListenerNotFoundException();

        Map<ObjectName, List<MyListenerInfo>> map;
        Set<ObjectName> names;

        if (name.isPattern()) {
            map = patternSubscriptionMap;
            names = mbeanServer.queryNames(name, notificationBroadcasterExp);
        } else {
            map = exactSubscriptionMap;
            names = Collections.singleton(name);
        }

        List<MyListenerInfo> toRemove = new ArrayList<MyListenerInfo>();
        synchronized (map) {
            List<MyListenerInfo> list = map.get(name);
            if (list == null) {
                throw new ListenerNotFoundException();
            }

            for (MyListenerInfo info : list) {
                if (info.listener == listener) {
                    toRemove.add(info);
                }
            }

            if (toRemove.isEmpty()) {
                throw new ListenerNotFoundException();
            }

            for (MyListenerInfo info : toRemove) {
                list.remove(info);
            }

            if (list.isEmpty())
                map.remove(name);
        }

        for (ObjectName mbeanName : names) {
            for (MyListenerInfo i : toRemove) {
                try {
                    mbeanServer.removeNotificationListener(mbeanName,
                        i.listener, i.filter, i.handback);
                } catch (Exception e) {
                    logger.fine("unsubscribe", "removeNotificationListener", e);
                }
            }
        }
    }

    // ---------------------------------
    // private stuff
    // ---------------------------------
    // used to receive MBeanServerNotification
    private NotificationListener myMBeanServerListener =
            new NotificationListener() {
        public void handleNotification(Notification n, Object hb) {
            if (!(n instanceof MBeanServerNotification) ||
                    !MBeanServerNotification.
                    REGISTRATION_NOTIFICATION.equals(n.getType())) {
                return;
            }

            final ObjectName name =
                    ((MBeanServerNotification)n).getMBeanName();
            try {
                if (!mbeanServer.isInstanceOf(name,
                        NotificationBroadcaster.class.getName())) {
                    return;
                }
            } catch (Exception e) {
                // The only documented exception is InstanceNotFoundException,
                // which could conceivably happen if the MBean is unregistered
                // immediately after being registered.
                logger.fine("myMBeanServerListener.handleNotification",
                        "isInstanceOf", e);
                return;
            }

            final List<MyListenerInfo> listeners = new ArrayList<MyListenerInfo>();

            // If there are subscribers for the exact name that has just arrived
            // then add their listeners to the list.
            synchronized (exactSubscriptionMap) {
                List<MyListenerInfo> exactListeners = exactSubscriptionMap.get(name);
                if (exactListeners != null)
                    listeners.addAll(exactListeners);
            }

            // For every subscription pattern that matches the new name,
            // add all the listeners for that pattern to "listeners".
            synchronized (patternSubscriptionMap) {
                for (ObjectName on : patternSubscriptionMap.keySet()) {
                    if (on.apply(name)) {
                        listeners.addAll(patternSubscriptionMap.get(on));
                    }
                }
            }

            // Add all the listeners just found to the new MBean.
            for (MyListenerInfo li : listeners) {
                try {
                    mbeanServer.addNotificationListener(
                            name,
                            li.listener,
                            li.filter,
                            li.handback);
                } catch (Exception e) {
                    logger.fine("myMBeanServerListener.handleNotification",
                            "addNotificationListener", e);
                }
            }
        }
    };

    private static class MyListenerInfo {
        public final NotificationListener listener;
        public final NotificationFilter filter;
        public final Object handback;

        public MyListenerInfo(NotificationListener listener,
                NotificationFilter filter,
                Object handback) {

            if (listener == null)
                throw new IllegalArgumentException("Null listener");

            this.listener = listener;
            this.filter = filter;
            this.handback = handback;
        }
    }

    // ---------------------------------
    // private methods
    // ---------------------------------
    // ---------------------------------
    // private variables
    // ---------------------------------
    private final MBeanServer mbeanServer;

    private final Map<ObjectName, List<MyListenerInfo>> exactSubscriptionMap =
            new HashMap<ObjectName, List<MyListenerInfo>>();
    private final Map<ObjectName, List<MyListenerInfo>> patternSubscriptionMap =
            new HashMap<ObjectName, List<MyListenerInfo>>();



    // trace issues
    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventSubscriber");

    // Compatibility code, so we can run on Tiger:
    private static final QueryExp notificationBroadcasterExp;
    static {
        QueryExp broadcasterExp;
        try {
            final Method m = Query.class.getMethod("isInstanceOf", String.class);
            broadcasterExp = (QueryExp)m.invoke(Query.class,
                    new Object[] {NotificationBroadcaster.class.getName()});
        } catch (Exception e) {
            broadcasterExp = new BroadcasterQueryExp();
        }
        notificationBroadcasterExp = broadcasterExp;
    }
    private static class BroadcasterQueryExp extends QueryEval implements QueryExp {
        private static final long serialVersionUID = 1234L;
        public boolean apply(ObjectName name) {
            try {
                return getMBeanServer().isInstanceOf(
                        name, NotificationBroadcaster.class.getName());
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final
            Map<MBeanServerConnection, WeakReference<EventSubscriber>> subscriberMap =
            new WeakHashMap<MBeanServerConnection, WeakReference<EventSubscriber>>();
}
