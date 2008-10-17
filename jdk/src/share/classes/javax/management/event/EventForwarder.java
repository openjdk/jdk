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

import java.io.IOException;
import javax.management.Notification;

/**
 * This interface can be used to specify a custom forwarding mechanism for
 * {@code EventClientDelegateMBean} to forward events to the client.
 *
 * @see <a href="package-summary.html#transports">Custom notification
 * transports</a>
 */
public interface EventForwarder {
    /**
     * Forwards a notification.
     * @param n The notification to be forwarded to a remote listener.
     * @param listenerId The identifier of the listener to receive the notification.
     * @throws IOException If it is closed or an I/O error occurs.
     */
    public void forward(Notification n, Integer listenerId)
        throws IOException;

    /**
     * Informs the {@code EventForwarder} to shut down.
     * <p> After this method is called, any call to the method
     * {@link #forward forward(Notification, Integer)} may get an {@code IOException}.
     * @throws IOException If an I/O error occurs.
     */
    public void close() throws IOException;

    /**
     * Sets an event client identifier created by {@link EventClientDelegateMBean}.
     * <P> This method will be called just after this {@code EventForwarder}
     * is constructed and before calling the {@code forward} method to forward any
     * notifications.
     */
    public void setClientId(String clientId) throws IOException;
}
