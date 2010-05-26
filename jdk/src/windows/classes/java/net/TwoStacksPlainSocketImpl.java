/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.net;

import java.io.IOException;
import java.io.FileDescriptor;

/*
 * This class defines the plain SocketImpl that is used for all
 * Windows version lower than Vista. It adds support for IPv6 on
 * these platforms where available.
 *
 * For backward compatibility Windows platforms that do not have IPv6
 * support also use this implementation, and fd1 gets set to null
 * during socket creation.
 *
 * @author Chris Hegarty
 */

class TwoStacksPlainSocketImpl extends AbstractPlainSocketImpl
{
    /* second fd, used for ipv6 on windows only.
     * fd1 is used for listeners and for client sockets at initialization
     * until the socket is connected. Up to this point fd always refers
     * to the ipv4 socket and fd1 to the ipv6 socket. After the socket
     * becomes connected, fd always refers to the connected socket
     * (either v4 or v6) and fd1 is closed.
     *
     * For ServerSockets, fd always refers to the v4 listener and
     * fd1 the v6 listener.
     */
    private FileDescriptor fd1;

    /*
     * Needed for ipv6 on windows because we need to know
     * if the socket is bound to ::0 or 0.0.0.0, when a caller
     * asks for it. Otherwise we don't know which socket to ask.
     */
    private InetAddress anyLocalBoundAddr = null;

    /* to prevent starvation when listening on two sockets, this is
     * is used to hold the id of the last socket we accepted on.
     */
    private int lastfd = -1;

    static {
        initProto();
    }

    public TwoStacksPlainSocketImpl() {}

    public TwoStacksPlainSocketImpl(FileDescriptor fd) {
        this.fd = fd;
    }

    /**
     * Creates a socket with a boolean that specifies whether this
     * is a stream socket (true) or an unconnected UDP socket (false).
     */
    protected synchronized void create(boolean stream) throws IOException {
        fd1 = new FileDescriptor();
        super.create(stream);
    }

     /**
     * Binds the socket to the specified address of the specified local port.
     * @param address the address
     * @param port the port
     */
    protected synchronized void bind(InetAddress address, int lport)
        throws IOException
    {
        super.bind(address, lport);
        if (address.isAnyLocalAddress()) {
            anyLocalBoundAddr = address;
        }
    }

    public Object getOption(int opt) throws SocketException {
        if (isClosedOrPending()) {
            throw new SocketException("Socket Closed");
        }
        if (opt == SO_BINDADDR) {
            if (fd != null && fd1 != null ) {
                /* must be unbound or else bound to anyLocal */
                return anyLocalBoundAddr;
            }
            InetAddressContainer in = new InetAddressContainer();
            socketGetOption(opt, in);
            return in.addr;
        } else
            return super.getOption(opt);
    }

    /**
     * Closes the socket.
     */
    protected void close() throws IOException {
        synchronized(fdLock) {
            if (fd != null || fd1 != null) {
                if (fdUseCount == 0) {
                    if (closePending) {
                        return;
                    }
                    closePending = true;
                    socketClose();
                    fd = null;
                    fd1 = null;
                    return;
                } else {
                    /*
                     * If a thread has acquired the fd and a close
                     * isn't pending then use a deferred close.
                     * Also decrement fdUseCount to signal the last
                     * thread that releases the fd to close it.
                     */
                    if (!closePending) {
                        closePending = true;
                        fdUseCount--;
                        socketClose();
                    }
                }
            }
        }
    }

    void reset() throws IOException {
        if (fd != null || fd1 != null) {
            socketClose();
        }
        fd = null;
        fd1 = null;
        super.reset();
    }

    /*
     * Return true if already closed or close is pending
     */
    public boolean isClosedOrPending() {
        /*
         * Lock on fdLock to ensure that we wait if a
         * close is in progress.
         */
        synchronized (fdLock) {
            if (closePending || (fd == null && fd1 == null)) {
                return true;
            } else {
                return false;
            }
        }
    }

    /* Native methods */

    static native void initProto();

    native void socketCreate(boolean isServer) throws IOException;

    native void socketConnect(InetAddress address, int port, int timeout)
        throws IOException;

    native void socketBind(InetAddress address, int port)
        throws IOException;

    native void socketListen(int count) throws IOException;

    native void socketAccept(SocketImpl s) throws IOException;

    native int socketAvailable() throws IOException;

    native void socketClose0(boolean useDeferredClose) throws IOException;

    native void socketShutdown(int howto) throws IOException;

    native void socketSetOption(int cmd, boolean on, Object value)
        throws SocketException;

    native int socketGetOption(int opt, Object iaContainerObj) throws SocketException;

    native void socketSendUrgentData(int data) throws IOException;
}
