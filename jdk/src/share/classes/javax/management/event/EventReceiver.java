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

import javax.management.remote.NotificationResult;

/**
 * An object implementing this interface is passed by an {@link EventClient}
 * to its {@link EventRelay}, to allow the {@code EventRelay} to communicate
 * received notifications to the {@code EventClient}.
 *
 * @see <a href="package-summary.html#transports">Custom notification
 * transports</a>
 */
public interface EventReceiver {

    /**
     * This method is implemented by {@code EventClient} as a callback to
     * receive notifications from {@code EventRelay}.
     * <P>The notifications are included in an object specified by the class
     * {@link NotificationResult}. In
     * addition to a set of notifications, the class object also contains two values:
     * {@code earliestSequenceNumber} and {@code nextSequenceNumber}.
     * These two values determine whether any notifications have been lost.
     * The {@code nextSequenceNumber} value of the last time is compared
     * to the received value {@code earliestSequenceNumber}. If the
     * received {@code earliesSequenceNumber} is greater, than the difference
     * signifies the number of lost notifications. A sender should
     * ensure the sequence of notifications sent, meaning that the value
     * {@code earliestSequenceNumber} of the next return should be always equal to
     * or greater than the value {@code nextSequenceNumber} of the last return.
     *
     * @param nr the received notifications and sequence numbers.
     */
    public void receive(NotificationResult nr);

    /**
     * Allows the {@link EventRelay} to report when it receives an unexpected
     * exception, which may be fatal and which may make it stop receiving
     * notifications.
     *
     * @param t The unexpected exception received while {@link EventRelay} was running.
     */
    public void failed(Throwable t);

    /**
     * Allows the {@link EventRelay} to report when it receives an unexpected
     * exception that is not fatal. For example, a notification received is not
     * serializable or its class is not found.
     *
     * @param e The unexpected exception received while notifications are being received.
     */
    public void nonFatal(Exception e);
}
