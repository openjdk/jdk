/*
 * Copyright (c) 1996, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import sun.net.ResourceManager;
import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.IPAddressUtil;
import sun.security.action.GetPropertyAction;

/**
 * Abstract datagram and multicast socket implementation base class.
 * Note: This is not a public class, so that applets cannot call
 * into the implementation directly and hence cannot bypass the
 * security checks present in the DatagramSocket and MulticastSocket
 * classes.
 *
 * @author Pavani Diwanji
 */

abstract class AbstractPlainDatagramSocketImpl extends DatagramSocketImpl
{
    /* timeout value for receive() */
    int timeout = 0;
    boolean connected = false;
    private int trafficClass = 0;
    protected InetAddress connectedAddress = null;
    private int connectedPort = -1;
    private final boolean isMulticast;

    private static final String os =
            GetPropertyAction.privilegedGetProperty("os.name");

    /**
     * flag set if the native connect() call not to be used
     */
    private static final boolean connectDisabled = os.contains("OS X");

    /**
     * Load net library into runtime.
     */
    static {
        jdk.internal.loader.BootLoader.loadLibrary("net");
    }

    private static volatile boolean checkedReusePort;
    private static volatile boolean isReusePortAvailable;

    /**
     * Tells whether SO_REUSEPORT is supported.
     */
    static boolean isReusePortAvailable() {
        if (!checkedReusePort) {
            isReusePortAvailable = isReusePortAvailable0();
            checkedReusePort = true;
        }
        return isReusePortAvailable;
    }

    AbstractPlainDatagramSocketImpl(boolean isMulticast) {
        this.isMulticast = isMulticast;
    }

    /**
     * Creates a datagram socket
     */
    protected synchronized void create() throws SocketException {
        ResourceManager.beforeUdpCreate();
        fd = new FileDescriptor();
        try {
            datagramSocketCreate();
            SocketCleanable.register(fd, false);
        } catch (SocketException ioe) {
            ResourceManager.afterUdpClose();
            fd = null;
            throw ioe;
        }
    }

    /**
     * Binds a datagram socket to a local port.
     */
    protected synchronized void bind(int lport, InetAddress laddr)
        throws SocketException {
        if (laddr.isLinkLocalAddress()) {
            laddr = IPAddressUtil.toScopedAddress(laddr);
        }
        bind0(lport, laddr);
    }

    protected abstract void bind0(int lport, InetAddress laddr)
        throws SocketException;

    /**
     * Sends a datagram packet. The packet contains the data and the
     * destination address to send the packet to.
     * @param p the packet to be sent.
     */
    protected void send(DatagramPacket p) throws IOException {
        InetAddress orig = p.getAddress();
        if (orig.isLinkLocalAddress()) {
            InetAddress scoped = IPAddressUtil.toScopedAddress(orig);
            if (orig != scoped) {
                p = new DatagramPacket(p.getData(), p.getOffset(),
                                       p.getLength(), scoped, p.getPort());
            }
        }
        send0(p);
    }

    protected abstract void send0(DatagramPacket p) throws IOException;

    /**
     * Connects a datagram socket to a remote destination. This associates the remote
     * address with the local socket so that datagrams may only be sent to this destination
     * and received from this destination.
     * @param address the remote InetAddress to connect to
     * @param port the remote port number
     */
    protected void connect(InetAddress address, int port) throws SocketException {
        if (address.isLinkLocalAddress()) {
            address = IPAddressUtil.toScopedAddress(address);
        }
        connect0(address, port);
        connectedAddress = address;
        connectedPort = port;
        connected = true;
    }

    /**
     * Disconnects a previously connected socket. Does nothing if the socket was
     * not connected already.
     */
    protected void disconnect() {
        disconnect0(connectedAddress.holder().getFamily());
        connected = false;
        connectedAddress = null;
        connectedPort = -1;
    }

    /**
     * Peek at the packet to see who it is from.
     * @param i the address to populate with the sender address
     */
    protected abstract int peek(InetAddress i) throws IOException;
    protected abstract int peekData(DatagramPacket p) throws IOException;
    /**
     * Receive the datagram packet.
     * @param p the packet to receive into
     */
    protected synchronized void receive(DatagramPacket p)
        throws IOException {
        receive0(p);
    }

    protected abstract void receive0(DatagramPacket p)
        throws IOException;

    /**
     * Set the TTL (time-to-live) option.
     * @param ttl TTL to be set.
     */
    protected abstract void setTimeToLive(int ttl) throws IOException;

    /**
     * Get the TTL (time-to-live) option.
     */
    protected abstract int getTimeToLive() throws IOException;

    /**
     * Set the TTL (time-to-live) option.
     * @param ttl TTL to be set.
     */
    @Deprecated
    protected abstract void setTTL(byte ttl) throws IOException;

    /**
     * Get the TTL (time-to-live) option.
     */
    @Deprecated
    protected abstract byte getTTL() throws IOException;

    /**
     * Join the multicast group.
     * @param inetaddr multicast address to join.
     */
    protected void join(InetAddress inetaddr) throws IOException {
        join(inetaddr, null);
    }

    /**
     * Leave the multicast group.
     * @param inetaddr multicast address to leave.
     */
    protected void leave(InetAddress inetaddr) throws IOException {
        leave(inetaddr, null);
    }
    /**
     * Join the multicast group.
     * @param mcastaddr multicast address to join.
     * @param netIf specifies the local interface to receive multicast
     *        datagram packets
     * @throws  IllegalArgumentException if mcastaddr is null or is a
     *          SocketAddress subclass not supported by this socket
     * @since 1.4
     */

    protected void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf)
        throws IOException {
        if (!(mcastaddr instanceof InetSocketAddress addr))
            throw new IllegalArgumentException("Unsupported address type");
        join(addr.getAddress(), netIf);
    }

    protected abstract void join(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    /**
     * Leave the multicast group.
     * @param mcastaddr  multicast address to leave.
     * @param netIf specified the local interface to leave the group at
     * @throws  IllegalArgumentException if mcastaddr is null or is a
     *          SocketAddress subclass not supported by this socket
     * @since 1.4
     */
    protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf)
        throws IOException {
        if (!(mcastaddr instanceof InetSocketAddress addr))
            throw new IllegalArgumentException("Unsupported address type");
        leave(addr.getAddress(), netIf);
    }

    protected abstract void leave(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    /**
     * Close the socket.
     */
    protected void close() {
        if (fd != null) {
            SocketCleanable.unregister(fd);
            datagramSocketClose();
            ResourceManager.afterUdpClose();
            fd = null;
        }
    }

    protected boolean isClosed() {
        return (fd == null) ? true : false;
    }

    /**
     * set a value - since we only support (setting) binary options
     * here, o must be a Boolean
     */

     public void setOption(int optID, Object o) throws SocketException {
         if (isClosed()) {
             throw new SocketException("Socket Closed");
         }
         switch (optID) {
            /* check type safety b4 going native.  These should never
             * fail, since only java.Socket* has access to
             * PlainSocketImpl.setOption().
             */
         case SO_TIMEOUT:
             if (!(o instanceof Integer)) {
                 throw new SocketException("bad argument for SO_TIMEOUT");
             }
             int tmp = ((Integer) o).intValue();
             if (tmp < 0)
                 throw new IllegalArgumentException("timeout < 0");
             timeout = tmp;
             return;
         case IP_TOS:
             if (!(o instanceof Integer)) {
                 throw new SocketException("bad argument for IP_TOS");
             }
             trafficClass = ((Integer)o).intValue();
             break;
         case SO_REUSEADDR:
             if (!(o instanceof Boolean)) {
                 throw new SocketException("bad argument for SO_REUSEADDR");
             }
             break;
         case SO_BROADCAST:
             if (!(o instanceof Boolean)) {
                 throw new SocketException("bad argument for SO_BROADCAST");
             }
             break;
         case SO_BINDADDR:
             throw new SocketException("Cannot re-bind Socket");
         case SO_RCVBUF:
         case SO_SNDBUF:
             if (!(o instanceof Integer) ||
                 ((Integer)o).intValue() < 0) {
                 throw new SocketException("bad argument for SO_SNDBUF or " +
                                           "SO_RCVBUF");
             }
             break;
         case IP_MULTICAST_IF:
             if (!(o instanceof InetAddress))
                 throw new SocketException("bad argument for IP_MULTICAST_IF");
             break;
         case IP_MULTICAST_IF2:
             if (!(o instanceof NetworkInterface))
                 throw new SocketException("bad argument for IP_MULTICAST_IF2");
             break;
         case IP_MULTICAST_LOOP:
             if (!(o instanceof Boolean))
                 throw new SocketException("bad argument for IP_MULTICAST_LOOP");
             break;
         case SO_REUSEPORT:
             if (!(o instanceof Boolean)) {
                 throw new SocketException("bad argument for SO_REUSEPORT");
             }
             if (!supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
                 throw new UnsupportedOperationException("unsupported option");
             }
             break;
         default:
             throw new SocketException("invalid option: " + optID);
         }
         socketSetOption(optID, o);
     }

    /*
     * get option's state - set or not
     */

    public Object getOption(int optID) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket Closed");
        }

        Object result;

        switch (optID) {
            case SO_TIMEOUT:
                result = timeout;
                break;

            case IP_TOS:
                result = socketGetOption(optID);
                if ( ((Integer)result).intValue() == -1) {
                    result = trafficClass;
                }
                break;

            case SO_BINDADDR:
            case IP_MULTICAST_IF:
            case IP_MULTICAST_IF2:
            case SO_RCVBUF:
            case SO_SNDBUF:
            case IP_MULTICAST_LOOP:
            case SO_REUSEADDR:
            case SO_BROADCAST:
                result = socketGetOption(optID);
                break;

            case SO_REUSEPORT:
                if (!supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
                    throw new UnsupportedOperationException("unsupported option");
                }
                result = socketGetOption(optID);
                break;

            default:
                throw new SocketException("invalid option: " + optID);
        }

        return result;
    }

    static final ExtendedSocketOptions extendedOptions =
            ExtendedSocketOptions.getInstance();

    private static final Set<SocketOption<?>> datagramSocketOptions = datagramSocketOptions();

    private static Set<SocketOption<?>> datagramSocketOptions() {
        HashSet<SocketOption<?>> options = new HashSet<>();
        options.add(StandardSocketOptions.SO_SNDBUF);
        options.add(StandardSocketOptions.SO_RCVBUF);
        options.add(StandardSocketOptions.SO_REUSEADDR);
        options.add(StandardSocketOptions.SO_BROADCAST);
        options.add(StandardSocketOptions.IP_TOS);
        options.add(StandardSocketOptions.IP_MULTICAST_IF);
        options.add(StandardSocketOptions.IP_MULTICAST_TTL);
        options.add(StandardSocketOptions.IP_MULTICAST_LOOP);
        if (isReusePortAvailable())
            options.add(StandardSocketOptions.SO_REUSEPORT);
        options.addAll(ExtendedSocketOptions.datagramSocketOptions());
        return Collections.unmodifiableSet(options);
    }

    @Override
    protected Set<SocketOption<?>> supportedOptions() {
            return datagramSocketOptions;
    }

    @Override
    protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        if (!name.type().isInstance(value))
            throw new IllegalArgumentException("Invalid value '" + value + "'");

        if (isClosed())
            throw new SocketException("Socket closed");

        if (name == StandardSocketOptions.SO_SNDBUF) {
            if (((Integer)value).intValue() < 0)
                throw new IllegalArgumentException("Invalid send buffer size:" + value);
            setOption(SocketOptions.SO_SNDBUF, value);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            if (((Integer)value).intValue() < 0)
                throw new IllegalArgumentException("Invalid recv buffer size:" + value);
            setOption(SocketOptions.SO_RCVBUF, value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            setOption(SocketOptions.SO_REUSEADDR, value);
        } else if (name == StandardSocketOptions.SO_REUSEPORT) {
            setOption(SocketOptions.SO_REUSEPORT, value);
        } else if (name == StandardSocketOptions.SO_BROADCAST) {
            setOption(SocketOptions.SO_BROADCAST, value);
        } else if (name == StandardSocketOptions.IP_TOS) {
            int i = ((Integer)value).intValue();
            if (i < 0 || i > 255)
                throw new IllegalArgumentException("Invalid IP_TOS value: " + value);
            setOption(SocketOptions.IP_TOS, value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_IF ) {
            setOption(SocketOptions.IP_MULTICAST_IF2, value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_TTL) {
            int i = ((Integer)value).intValue();
            if (i < 0 || i > 255)
                throw new IllegalArgumentException("Invalid TTL/hop value: " + value);
            setTimeToLive((Integer)value);
        } else if (name == StandardSocketOptions.IP_MULTICAST_LOOP) {
            boolean enable = (boolean) value;
            // Legacy setOption expects true to mean 'disabled'
            setOption(SocketOptions.IP_MULTICAST_LOOP, !enable);
        } else if (extendedOptions.isOptionSupported(name)) {
            extendedOptions.setOption(fd, name, value);
        } else {
            throw new AssertionError("unknown option :" + name);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T getOption(SocketOption<T> name) throws IOException {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        if (isClosed())
            throw new SocketException("Socket closed");

        if (name == StandardSocketOptions.SO_SNDBUF) {
            return (T) getOption(SocketOptions.SO_SNDBUF);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            return (T) getOption(SocketOptions.SO_RCVBUF);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            return (T) getOption(SocketOptions.SO_REUSEADDR);
        } else if (name == StandardSocketOptions.SO_REUSEPORT) {
            return (T) getOption(SocketOptions.SO_REUSEPORT);
        } else if (name == StandardSocketOptions.SO_BROADCAST) {
            return (T) getOption(SocketOptions.SO_BROADCAST);
        } else if (name == StandardSocketOptions.IP_TOS) {
            return (T) getOption(SocketOptions.IP_TOS);
        } else if (name == StandardSocketOptions.IP_MULTICAST_IF) {
            return (T) getOption(SocketOptions.IP_MULTICAST_IF2);
        } else if (name == StandardSocketOptions.IP_MULTICAST_TTL) {
            return (T) ((Integer) getTimeToLive());
        } else if (name == StandardSocketOptions.IP_MULTICAST_LOOP) {
            boolean disabled = (boolean) getOption(SocketOptions.IP_MULTICAST_LOOP);
            // Legacy getOption returns true when disabled
            return (T) Boolean.valueOf(!disabled);
        } else if (extendedOptions.isOptionSupported(name)) {
            return (T) extendedOptions.getOption(fd, name);
        } else {
            throw new AssertionError("unknown option: " + name);
        }
    }

    protected abstract void datagramSocketCreate() throws SocketException;
    protected abstract void datagramSocketClose();
    protected abstract void socketSetOption(int opt, Object val)
        throws SocketException;
    protected abstract Object socketGetOption(int opt) throws SocketException;

    protected abstract void connect0(InetAddress address, int port) throws SocketException;
    protected abstract void disconnect0(int family);

    protected boolean nativeConnectDisabled() {
        return connectDisabled;
    }

    abstract int dataAvailable();
    private static native boolean isReusePortAvailable0();
}
