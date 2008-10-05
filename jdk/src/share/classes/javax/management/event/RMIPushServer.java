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

import java.rmi.Remote;
import java.rmi.RemoteException;
import javax.management.remote.NotificationResult;

/**
 * The {@link RMIPushEventRelay} exports an RMI object of this class and
 * sends a client stub for that object to the associated
 * {@link RMIPushEventForwarder} in a remote MBean server.  The
 * {@code RMIPushEventForwarder} then sends notifications to the
 * RMI object.
 */
public interface RMIPushServer extends Remote {
    /**
     * <p>Dispatch the notifications in {@code nr} to the {@link RMIPushEventRelay}
     * associated with this object.</p>
     * @param nr the notification result to dispatch.
     * @throws java.rmi.RemoteException if the remote invocation of this method
     * failed.
     */
    public void receive(NotificationResult nr) throws RemoteException;
}
