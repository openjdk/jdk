/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.net;

import java.io.IOException;
import java.io.FileDescriptor;
import sun.misc.SharedSecrets;
import sun.misc.JavaIOFileDescriptorAccess;

/**
 * This class defines the plain SocketImpl that is used on Windows platforms
 * greater or equal to Windows Vista. These platforms have a dual
 * layer TCP/IP stack and can handle both IPv4 and IPV6 through a
 * single file descriptor.
 *
 * @author Chris Hegarty
 */

class DualStackPlainSocketImpl extends AbstractPlainSocketImpl
{
    static JavaIOFileDescriptorAccess fdAccess = SharedSecrets.getJavaIOFileDescriptorAccess();

    public DualStackPlainSocketImpl() {}

    public DualStackPlainSocketImpl(FileDescriptor fd) {
        this.fd = fd;
    }

    void socketCreate(boolean stream) throws IOException {
        if (fd == null)
            throw new SocketException("Socket closed");

        int newfd = socket0(stream, false /*v6 Only*/);

        fdAccess.set(fd, newfd);
    }

    void socketConnect(InetAddress address, int port, int timeout)
        throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (address == null)
            throw new NullPointerException("inet address argument is null.");

        int connectResult;
        if (timeout <= 0) {
            connectResult = connect0(nativefd, address, port);
        } else {
            configureBlocking(nativefd, false);
            try {
                connectResult = connect0(nativefd, address, port);
                if (connectResult == WOULDBLOCK) {
                    waitForConnect(nativefd, timeout);
                }
            } finally {
                configureBlocking(nativefd, true);
            }
        }
        /*
         * We need to set the local port field. If bind was called
         * previous to the connect (by the client) then localport field
         * will already be set.
         */
        if (localport == 0)
            localport = localPort0(nativefd);
    }

    void socketBind(InetAddress address, int port) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (address == null)
            throw new NullPointerException("inet address argument is null.");

        bind0(nativefd, address, port);
        if (port == 0) {
            localport = localPort0(nativefd);
        } else {
            localport = port;
        }

        this.address = address;
    }

    void socketListen(int backlog) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        listen0(nativefd, backlog);
    }

    void socketAccept(SocketImpl s) throws IOException {
        int nativefd = checkAndReturnNativeFD();

        if (s == null)
            throw new NullPointerException("socket is null");

        int newfd = -1;
        InetSocketAddress[] isaa = new InetSocketAddress[1];
        if (timeout <= 0) {
            newfd = accept0(nativefd, isaa);
        } else {
            configureBlocking(nativefd, false);
            try {
                waitForNewConnection(nativefd, timeout);
                newfd = accept0(nativefd, isaa);
                if (newfd != -1) {
                    configureBlocking(newfd, true);
                }
            } finally {
                configureBlocking(nativefd, true);
            }
        }
        /* Update (SocketImpl)s' fd */
        fdAccess.set(s.fd, newfd);
        /* Update socketImpls remote port, address and localport */
        InetSocketAddress isa = isaa[0];
        s.port = isa.getPort();
        s.address = isa.getAddress();
        s.localport = localport;
    }

    int socketAvailable() throws IOException {
        int nativefd = checkAndReturnNativeFD();
        return available0(nativefd);
    }

    void socketClose0(boolean useDeferredClose/*unused*/) throws IOException {
        if (fd == null)
            throw new SocketException("Socket closed");

        if (!fd.valid())
            return;

        close0(fdAccess.get(fd));
        fdAccess.set(fd, -1);
    }

    void socketShutdown(int howto) throws IOException {
        int nativefd = checkAndReturnNativeFD();
        shutdown0(nativefd, howto);
    }

    void socketSetOption(int opt, boolean on, Object value)
        throws SocketException {
        int nativefd = checkAndReturnNativeFD();

        if (opt == SO_TIMEOUT) {  // timeout implemented through select.
            return;
        }

        int optionValue = 0;

        switch(opt) {
            case TCP_NODELAY :
            case SO_OOBINLINE :
            case SO_KEEPALIVE :
            case SO_REUSEADDR :
                optionValue = on ? 1 : 0;
                break;
            case SO_SNDBUF :
            case SO_RCVBUF :
            case IP_TOS :
                optionValue = ((Integer)value).intValue();
                break;
            case SO_LINGER :
                if (on) {
                    optionValue =  ((Integer)value).intValue();
                } else {
                    optionValue = -1;
                }
                break;
            default :/* shouldn't get here */
                throw new SocketException("Option not supported");
        }

        setIntOption(nativefd, opt, optionValue);
    }

    int socketGetOption(int opt, Object iaContainerObj) throws SocketException {
        int nativefd = checkAndReturnNativeFD();

        // SO_BINDADDR is not a socket option.
        if (opt == SO_BINDADDR) {
            localAddress(nativefd, (InetAddressContainer)iaContainerObj);
            return 0;  // return value doesn't matter.
        }

        int value = getIntOption(nativefd, opt);

        switch (opt) {
            case TCP_NODELAY :
            case SO_OOBINLINE :
            case SO_KEEPALIVE :
            case SO_REUSEADDR :
                return (value == 0) ? -1 : 1;
        }
        return value;
    }

    int socketGetOption1(int opt, Object iaContainerObj, FileDescriptor fd)
        throws SocketException {return 0;}   // un-implemented REMOVE

    void socketSendUrgentData(int data) throws IOException {
        int nativefd = checkAndReturnNativeFD();
        sendOOB(nativefd, data);
    }

    private int checkAndReturnNativeFD() throws SocketException {
        if (fd == null || !fd.valid())
            throw new SocketException("Socket closed");

        return fdAccess.get(fd);
    }

    static final int WOULDBLOCK = -2;       // Nothing available (non-blocking)

    static {
        initIDs();
    }

    /* Native methods */

    static native void initIDs();

    static native int socket0(boolean stream, boolean v6Only) throws IOException;

    static native void bind0(int fd, InetAddress localAddress, int localport)
        throws IOException;

    static native int connect0(int fd, InetAddress remote, int remotePort)
        throws IOException;

    static native void waitForConnect(int fd, int timeout) throws IOException;

    static native int localPort0(int fd) throws IOException;

    static native void localAddress(int fd, InetAddressContainer in) throws SocketException;

    static native void listen0(int fd, int backlog) throws IOException;

    static native int accept0(int fd, InetSocketAddress[] isaa) throws IOException;

    static native void waitForNewConnection(int fd, int timeout) throws IOException;

    static native int available0(int fd) throws IOException;

    static native void close0(int fd) throws IOException;

    static native void shutdown0(int fd, int howto) throws IOException;

    static native void setIntOption(int fd, int cmd, int optionValue) throws SocketException;

    static native int getIntOption(int fd, int cmd) throws SocketException;

    static native void sendOOB(int fd, int data) throws IOException;

    static native void configureBlocking(int fd, boolean blocking) throws IOException;
}
