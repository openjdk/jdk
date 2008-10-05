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

package javax.management.event;

import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

/**
 * This class specifies all the information required to register a user listener into
 * a remote MBean server. This class is not serializable because a user listener
 * is not serialized in order to be sent to the remote server.
 *
 * @since JMX 2.0
 */
public class ListenerInfo {

    /**
     * Constructs a {@code ListenerInfo} object.
     *
     * @param name The name of the MBean to which the listener should
     * be added.
     * @param listener The listener object which will handle the
     * notifications emitted by the MBean.
     * @param filter The filter object. If the filter is null, no
     * filtering will be performed before notifications are handled.
     * @param handback The context to be sent to the listener when a
     * notification is emitted.
     * @param isSubscription If true, the listener is subscribed via
     * an {@code EventManager}. Otherwise it is added to a registered MBean.
     */
    public ListenerInfo(ObjectName name,
            NotificationListener listener,
            NotificationFilter filter,
            Object handback,
            boolean isSubscription) {
        this.name = name;
        this.listener = listener;
        this.filter = filter;
        this.handback = handback;
        this.isSubscription = isSubscription;
    }

    /**
     * Returns an MBean or an MBean pattern that the listener listens to.
     *
     * @return An MBean or an MBean pattern.
     */
    public ObjectName getObjectName() {
        return name;
    }

    /**
     * Returns the listener.
     *
     * @return The listener.
     */
    public NotificationListener getListener() {
        return listener;
    }

    /**
     * Returns the listener filter.
     *
     * @return The filter.
     */
    public NotificationFilter getFilter() {
        return filter;
    }

    /**
     * Returns the listener handback.
     *
     * @return The handback.
     */
    public Object getHandback() {
        return handback;
    }

    /**
     * Returns true if this is a subscription listener.
     *
     * @return True if this is a subscription listener.
     *
     * @see EventClient#addListeners
     */
    public boolean isSubscription() {
        return isSubscription;
    }

    /**
     * <p>Indicates whether some other object is "equal to" this one.
     * The return value is true if and only if {@code o} is an instance of
     * {@code ListenerInfo} and has equal values for all of its properties.</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ListenerInfo)) {
            return false;
        }

        ListenerInfo li = (ListenerInfo)o;

        boolean ret = name.equals(li.name) &&
                (listener == li.listener) &&
                (isSubscription == li.isSubscription);

        if (filter != null) {
            ret &= filter.equals(li.filter);
        } else {
            ret &= (li.filter == null);
        }

        if (handback != null) {
            ret &= handback.equals(li.handback);
        } else {
            ret &= (li.handback == null);
        }

        return ret;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + listener.hashCode();
    }

    @Override
    public String toString() {
        return name.toString() + "_" +
                listener + "_" +
                filter + "_" +
                handback + "_" +
                isSubscription;
    }

    private final ObjectName name;
    private final NotificationListener listener;
    private final NotificationFilter filter;
    private final Object handback;
    private final boolean isSubscription;
}
