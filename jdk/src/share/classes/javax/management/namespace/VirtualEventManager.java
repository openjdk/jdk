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

import com.sun.jmx.remote.util.ClassLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.event.EventConsumer;

/**
 * <p>This class maintains a list of subscribers for ObjectName patterns and
 * allows a notification to be sent to all subscribers for a given ObjectName.
 * It is typically used in conjunction with {@link MBeanServerSupport}
 * to implement a namespace with Virtual MBeans that can emit notifications.
 * The {@code VirtualEventManager} keeps track of the listeners that have been
 * added to each Virtual MBean. When an event occurs that should trigger a
 * notification from a Virtual MBean, the {@link #publish publish} method can
 * be used to send it to the appropriate listeners.</p>
 * @since 1.7
 */
public class VirtualEventManager implements EventConsumer {
    /**
     * <p>Create a new {@code VirtualEventManager}.</p>
     */
    public VirtualEventManager() {
    }

    public void subscribe(
            ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback) {

        if (logger.traceOn())
            logger.trace("subscribe", "" + name);

        if (name == null)
            throw new IllegalArgumentException("Null MBean name");

        if (listener == null)
            throw new IllegalArgumentException("Null listener");

        Map<ObjectName, List<ListenerInfo>> map =
                name.isPattern() ? patternSubscriptionMap : exactSubscriptionMap;

        final ListenerInfo li = new ListenerInfo(listener, filter, handback);
        List<ListenerInfo> list;

        synchronized (map) {
            list = map.get(name);
            if (list == null) {
                list = new ArrayList<ListenerInfo>();
                map.put(name, list);
            }
            list.add(li);
        }
    }

    public void unsubscribe(
            ObjectName name, NotificationListener listener)
            throws ListenerNotFoundException {

        if (logger.traceOn())
            logger.trace("unsubscribe2", "" + name);

        if (name == null)
            throw new IllegalArgumentException("Null MBean name");

        if (listener == null)
            throw new ListenerNotFoundException();

        Map<ObjectName, List<ListenerInfo>> map =
                name.isPattern() ? patternSubscriptionMap : exactSubscriptionMap;

        final ListenerInfo li = new ListenerInfo(listener, null, null);
        List<ListenerInfo> list;
        synchronized (map) {
            list = map.get(name);
            if (list == null || !list.remove(li))
                throw new ListenerNotFoundException();

            if (list.isEmpty())
                map.remove(name);
        }
    }

    /**
     * <p>Unsubscribes a listener which is listening to an MBean or a set of
     * MBeans represented by an {@code ObjectName} pattern.</p>
     *
     * <p>The listener to be removed must have been added by the {@link
     * #subscribe subscribe} method with the given {@code name}, {@code filter},
     * and {@code handback}. If the {@code
     * name} is a pattern, then the {@code subscribe} must have used the same
     * pattern. If the same listener has been subscribed more than once to the
     * {@code name} with the same filter and handback, only one listener is
     * removed.</p>
     *
     * @param name The name of the MBean or an {@code ObjectName} pattern
     * representing a set of MBeans to which the listener was subscribed.
     * @param listener A listener that was previously subscribed to the
     * MBean(s).
     *
     * @throws ListenerNotFoundException The given {@code listener} was not
     * subscribed to the given {@code name}.
     *
     * @see #subscribe
     */
    public void unsubscribe(
            ObjectName name, NotificationListener listener,
            NotificationFilter filter, Object handback)
            throws ListenerNotFoundException {

        if (logger.traceOn())
            logger.trace("unsubscribe4", "" + name);

        if (name == null)
            throw new IllegalArgumentException("Null MBean name");

        if (listener == null)
            throw new ListenerNotFoundException();

        Map<ObjectName, List<ListenerInfo>> map =
                name.isPattern() ? patternSubscriptionMap : exactSubscriptionMap;

        List<ListenerInfo> list;
        synchronized (map) {
            list = map.get(name);
            boolean removed = false;
            for (Iterator<ListenerInfo> it = list.iterator(); it.hasNext(); ) {
                ListenerInfo li = it.next();
                if (li.equals(listener, filter, handback)) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed)
                throw new ListenerNotFoundException();

            if (list.isEmpty())
                map.remove(name);
        }
    }

    /**
     * <p>Sends a notification to the subscribers for a given MBean.</p>
     *
     * <p>For each listener subscribed with an {@code ObjectName} that either
     * is equal to {@code emitterName} or is a pattern that matches {@code
     * emitterName}, if the associated filter accepts the notification then it
     * is forwarded to the listener.</p>
     *
     * @param emitterName The name of the MBean emitting the notification.
     * @param n The notification being sent by the MBean called
     * {@code emitterName}.
     *
     * @throws IllegalArgumentException If the emitterName of the
     * notification is null or is an {@code ObjectName} pattern.
     */
    public void publish(ObjectName emitterName, Notification n) {
        if (logger.traceOn())
            logger.trace("publish", "" + emitterName);

        if (n == null)
            throw new IllegalArgumentException("Null notification");

        if (emitterName == null) {
            throw new IllegalArgumentException(
                    "Null emitter name");
        } else if (emitterName.isPattern()) {
            throw new IllegalArgumentException(
                    "The emitter must not be an ObjectName pattern");
        }

        final List<ListenerInfo> listeners = new ArrayList<ListenerInfo>();

        // If there are listeners for this exact name, add them.
        synchronized (exactSubscriptionMap) {
            List<ListenerInfo> exactListeners =
                    exactSubscriptionMap.get(emitterName);
            if (exactListeners != null)
                listeners.addAll(exactListeners);
        }

        // Loop over subscription patterns, and add all listeners for each
        // one that matches the emitterName name.
        synchronized (patternSubscriptionMap) {
            for (ObjectName on : patternSubscriptionMap.keySet()) {
                if (on.apply(emitterName))
                    listeners.addAll(patternSubscriptionMap.get(on));
            }
        }

        // Send the notification to all the listeners we found.
        sendNotif(listeners, n);
    }

    /**
     * <p>Returns a {@link NotificationEmitter} object which can be used to
     * subscribe or unsubscribe for notifications with the named
     * mbean.  The returned object implements {@link
     * NotificationEmitter#addNotificationListener
     * addNotificationListener(listener, filter, handback)} as
     * {@link #subscribe this.subscribe(name, listener, filter, handback)}
     * and the two {@code removeNotificationListener} methods from {@link
     * NotificationEmitter} as the corresponding {@code unsubscribe} methods
     * from this class.</p>
     *
     * @param name   The name of the MBean whose notifications are being
     *        subscribed, or unsuscribed.
     *
     * @return A {@link NotificationEmitter}
     *         that can be used to subscribe or unsubscribe for
     *         notifications emitted by the named MBean, or {@code null} if
     *         the MBean does not emit notifications and should not
     *         be considered as a {@code NotificationBroadcaster}.  This class
     *         never returns null but a subclass is allowed to.
     *
     * @throws InstanceNotFoundException if {@code name} does not exist.
     * This implementation never throws {@code InstanceNotFoundException} but
     * a subclass is allowed to override this method to do so.
     */
    public NotificationEmitter
            getNotificationEmitterFor(final ObjectName name)
            throws InstanceNotFoundException {
        final NotificationEmitter emitter = new NotificationEmitter() {
            public void addNotificationListener(NotificationListener listener,
                    NotificationFilter filter, Object handback)
                    throws IllegalArgumentException {
                subscribe(name, listener, filter, handback);
            }

            public void removeNotificationListener(
                    NotificationListener listener)
                    throws ListenerNotFoundException {
                unsubscribe(name, listener);
            }

            public void removeNotificationListener(NotificationListener listener,
                                                   NotificationFilter filter,
                                                   Object handback)
                    throws ListenerNotFoundException {
                unsubscribe(name, listener, filter, handback);
            }

            public MBeanNotificationInfo[] getNotificationInfo() {
                // Never called.
                return null;
            }
        };
        return emitter;
    }

    // ---------------------------------
    // private stuff
    // ---------------------------------

    private static class ListenerInfo {
        public final NotificationListener listener;
        public final NotificationFilter filter;
        public final Object handback;

        public ListenerInfo(NotificationListener listener,
                NotificationFilter filter,
                Object handback) {

            if (listener == null) {
                throw new IllegalArgumentException("Null listener.");
            }

            this.listener = listener;
            this.filter = filter;
            this.handback = handback;
        }

        /* Two ListenerInfo instances are equal if they have the same
         * NotificationListener.  This means that we can use List.remove
         * to implement the two-argument removeNotificationListener.
         */
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof ListenerInfo)) {
                return false;
            }

            return listener.equals(((ListenerInfo)o).listener);
        }

        /* Method that compares all four fields, appropriate for the
         * four-argument removeNotificationListener.
         */
        boolean equals(
                NotificationListener listener,
                NotificationFilter filter,
                Object handback) {
            return (this.listener == listener && same(this.filter, filter)
                    && same(this.handback, handback));
        }

        private static boolean same(Object x, Object y) {
            if (x == y)
                return true;
            if (x == null)
                return false;
            return x.equals(y);
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }
    }

    private static void sendNotif(List<ListenerInfo> listeners, Notification n) {
        for (ListenerInfo li : listeners) {
            if (li.filter == null ||
                    li.filter.isNotificationEnabled(n)) {
                try {
                    li.listener.handleNotification(n, li.handback);
                } catch (Exception e) {
                    logger.trace("sendNotif", "handleNotification", e);
                }
            }
        }
    }

    // ---------------------------------
    // private variables
    // ---------------------------------

    private final Map<ObjectName, List<ListenerInfo>> exactSubscriptionMap =
            new HashMap<ObjectName, List<ListenerInfo>>();
    private final Map<ObjectName, List<ListenerInfo>> patternSubscriptionMap =
            new HashMap<ObjectName, List<ListenerInfo>>();

    // trace issue
    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "EventManager");
}
