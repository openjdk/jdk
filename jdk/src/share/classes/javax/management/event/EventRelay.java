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
import java.util.concurrent.Executors;  // for javadoc
import java.util.concurrent.ScheduledFuture;

/**
 * This interface is used to specify a way to receive
 * notifications from a remote MBean server and then to forward the notifications
 * to an {@link EventClient}.
 *
 * @see <a href="package-summary.html#transports">Custom notification
 * transports</a>
 */
public interface EventRelay {
    /**
     * Returns an identifier that is used by this {@code EventRelay} to identify
     * the client when communicating with the {@link EventClientDelegateMBean}.
     * <P> This identifier is obtained by calling
     * {@link EventClientDelegateMBean#addClient(String, Object[], String[])
     * EventClientDelegateMBean.addClient}.
     * <P> It is the {@code EventRelay} that calls {@code EventClientDelegateMBean} to obtain
     * the client identifier because it is the {@code EventRelay} that decides
     * how to get notifications from the {@code EventClientDelegateMBean},
     * by creating the appropriate {@link EventForwarder}.
     *
     * @return A client identifier.
     * @throws IOException If an I/O error occurs when communicating with
     * the {@code EventClientDelegateMBean}.
     */
    public String getClientId() throws IOException;

    /**
     * This method is called by {@link EventClient} to register a callback
     * to receive notifications from an {@link EventClientDelegateMBean} object.
     * A {@code null} value is allowed, which means that the {@code EventClient} suspends
     * reception of notifications, so that the {@code EventRelay} can decide to stop receiving
     * notifications from its {@code EventForwarder}.
     *
     * @param eventReceiver An {@link EventClient} callback to receive
     * events.
     */
    public void setEventReceiver(EventReceiver eventReceiver);

    /**
     * Stops receiving and forwarding notifications and performs any necessary
     * cleanup.  After calling this method, the {@link EventClient} will never
     * call any other methods of this object.
     *
     * @throws IOException If an I/O exception appears.
     *
     * @see EventClient#close
     */
    public void stop() throws IOException;
}
