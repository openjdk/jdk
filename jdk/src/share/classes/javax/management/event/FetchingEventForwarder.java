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

import com.sun.jmx.event.EventBuffer;
import com.sun.jmx.remote.util.ClassLogger;
import java.io.IOException;
import java.util.List;
import javax.management.Notification;
import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;

/**
 * This class is used by {@link FetchingEventRelay}. When
 * {@link FetchingEventRelay} calls {@link
 * EventClientDelegateMBean#addClient(String, Object[], String[])} to get a new
 * client identifier, it uses
 * this class name as the first argument to ask {@code EventClientDelegateMBean}
 * to create an object of this class.
 * Then {@code EventClientDelegateMBean} forwards client notifications
 * to this object.
 * When {@link FetchingEventRelay} calls
 * {@link EventClientDelegateMBean#fetchNotifications(String, long, int, long)}
 * to fetch notifications, the {@code EventClientDelegateMBean} will forward
 * the call to this object.
 */
public class FetchingEventForwarder implements EventForwarder {

    /**
     * Construct a new {@code FetchingEventForwarder} with the given
     * buffer size.
     * @param bufferSize the size of the buffer that will store notifications
     * until they have been fetched and acknowledged by the client.
     */
    public FetchingEventForwarder(int bufferSize) {
        if (logger.traceOn()) {
            logger.trace("Constructor", "buffer size is "+bufferSize);
        }

        buffer = new EventBuffer(bufferSize);
        this.bufferSize = bufferSize;
    }

    /**
     * Called by an {@link EventClientDelegateMBean} to forward a user call
     * {@link EventClientDelegateMBean#fetchNotifications(String, long, int, long)}.
     * A call of this method is considered to acknowledge reception of all
     * notifications whose sequence numbers are less the
     * {@code startSequenceNumber}, so all these notifications can be deleted
     * from this object.
     *
     * @param startSequenceNumber The first sequence number to
     * consider.
     * @param timeout The maximum waiting time in milliseconds.
     * If no notifications have arrived after this period of time, the call
     * will return with an empty list of notifications.
     * @param maxNotifs The maximum number of notifications to return.
     */
    public NotificationResult fetchNotifications(long startSequenceNumber,
            int maxNotifs, long timeout) {
        if (logger.traceOn()) {
            logger.trace("fetchNotifications",
                    startSequenceNumber+" "+
                    maxNotifs+" "+
                    timeout);
        }

        return buffer.fetchNotifications(startSequenceNumber,
                    timeout,
                    maxNotifs);
    }

    /**
     * {@inheritDoc}
     * In this implementation, the notification is stored in the local buffer
     * waiting for {@link #fetchNotifications fetchNotifications} to pick
     * it up.
     */
    public void forward(Notification n, Integer listenerId) throws IOException {
        if (logger.traceOn()) {
            logger.trace("forward", n+" "+listenerId);
        }

        buffer.add(new TargetedNotification(n, listenerId));
    }

    public void close() throws IOException {
        if (logger.traceOn()) {
            logger.trace("close", "");
        }

        buffer.close();
    }

    public void setClientId(String clientId) throws IOException {
        if (logger.traceOn()) {
            logger.trace("setClientId", clientId);
        }
        this.clientId = clientId;
    }

    /**
     * Sets a user specific list to save notifications in server side
     * before forwarding to an FetchingEventRelay in client side.
     * <P> This method should be called before any notification is
     * forwarded to this forwader.
     *
     * @param list a user specific list to save notifications
     */
    protected void setList(List<TargetedNotification> list) {
        if (logger.traceOn()) {
            logger.trace("setList", "");
        }

        if (clientId == null) {
            buffer = new EventBuffer(bufferSize, list);
        } else {
            throw new IllegalStateException();
        }
    }

    private EventBuffer buffer;
    private int bufferSize;
    private String clientId;

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event", "FetchingEventForwarder");
}
