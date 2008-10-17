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

import com.sun.jmx.remote.util.ClassLogger;
import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import javax.management.MBeanException;
import javax.management.remote.NotificationResult;

/**
 * This class is an implementation of the {@link EventRelay} interface, using
 * push mode. It exports an RMI object that {@link RMIPushEventForwarder} uses
 * to forward notifications.
 *
 * @since JMX 2.0
 */
public class RMIPushEventRelay implements EventRelay {
    /**
     * Constructs a default {@code RMIPushEventRelay} object
     * and exports its {@linkplain RMIPushServer notification
     * receiver} on any free port. This constructor is equivalent
     * to {@link #RMIPushEventRelay(EventClientDelegateMBean,
     * int, RMIClientSocketFactory, RMIServerSocketFactory, int)
     * RMIPushEventRelay(delegate, 0, null, null, <em>&lt;default buffer
     * size&gt;</em>)}.
     *
     * @param delegate The {@link EventClientDelegateMBean} proxy to work with.
     * @throws IOException if failed to communicate with
     * {@link EventClientDelegateMBean}.
     * @throws MBeanException if the {@link EventClientDelegateMBean} failed
     * to create an {@code EventForwarder} for this object.
     */
    public RMIPushEventRelay(EventClientDelegateMBean delegate)
    throws IOException, MBeanException {
        this(delegate, 0, null,  null, 0);
    }

    /**
     * Constructs a {@code RMIPushEventRelay} object and exports its
     * {@linkplain RMIPushServer notification receiver} on a specified port.
     *
     * @param delegate The {@link EventClientDelegateMBean} proxy to work with.
     * @param port The port used to export an RMI object to receive notifications
     * from a server. If the port is zero, an anonymous port is used.
     * @param csf The client socket factory used to export the RMI object.
     * Can be null.
     * @param ssf The server socket factory used to export the RMI object.
     * Can be null.
     * @param bufferSize The number of notifications held on the server
     * while waiting for the previous transmission to complete.  A value of
     * zero means the default buffer size.
     *
     * @throws IOException if failed to communicate with
     * {@link EventClientDelegateMBean}.
     * @throws MBeanException if the {@link EventClientDelegateMBean} failed
     * to create an {@code EventForwarder} for this object.
     *
     * @see RMIPushEventForwarder#RMIPushEventForwarder(RMIPushServer, int)
     */
    public RMIPushEventRelay(EventClientDelegateMBean delegate,
            int port,
            RMIClientSocketFactory csf,
            RMIServerSocketFactory ssf,
            int bufferSize)
            throws IOException, MBeanException {

        UnicastRemoteObject.exportObject(exportedReceiver, port, csf, ssf);

        clientId = delegate.addClient(
                RMIPushEventForwarder.class.getName(),
                new Object[] {exportedReceiver, bufferSize},
                new String[] {RMIPushServer.class.getName(),
                              int.class.getName()});
    }

    public String getClientId() {
        return clientId;
    }

    public void setEventReceiver(EventReceiver receiver) {
        if (logger.traceOn()) {
            logger.trace("setEventReceiver", ""+receiver);
        }
        synchronized(lock) {
            this.receiver = receiver;
        }
    }

    public void stop() {
        if (logger.traceOn()) {
            logger.trace("stop", "");
        }
        synchronized(lock) {
            if (stopped) {
                return;
            } else {
                stopped = true;
            }

            if (clientId == null) {
                return;
            }

            try {
                UnicastRemoteObject.unexportObject(exportedReceiver, true);
            } catch (NoSuchObjectException nsoe) {
                logger.fine("RMIPushEventRelay.stop", "unexport", nsoe);
                // OK: we wanted it unexported, and apparently it already is
            }
        }
    }

    private volatile String clientId;
    private volatile EventReceiver receiver;

    private RMIPushServer exportedReceiver = new RMIPushServer() {
        public void receive(NotificationResult nr) throws RemoteException {
            if (logger.traceOn()) {
                logger.trace("EventPusherImpl-receive","");
            }
            receiver.receive(nr);
            // Any exception will be sent back to the client.
        }
    };

    private boolean stopped = false;

    private final int[] lock = new int[0];

    private static final ClassLogger logger =
            new ClassLogger("javax.management.event",
            "PushEventRelay");
}
