/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Objects;

import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.IPAddressUtil;
import sun.security.action.GetPropertyAction;

public class Net {
    private Net() { }

    // unspecified protocol family
    static final ProtocolFamily UNSPEC = new ProtocolFamily() {
        public String name() {
            return "UNSPEC";
        }
    };

    // set to true if exclusive binding is on for Windows
    private static final boolean EXCLUSIVE_BIND;

    // set to true if the fast tcp loopback should be enabled on Windows
    private static final boolean FAST_LOOPBACK;

    // -- Miscellaneous utilities --

    private static final boolean IPV6_AVAILABLE;
    private static final boolean SO_REUSEPORT_AVAILABLE;

    /**
     * Tells whether dual-IPv4/IPv6 sockets should be used.
     */
    static boolean isIPv6Available() {
        return IPV6_AVAILABLE;
    }

    /**
     * Tells whether SO_REUSEPORT is supported.
     */
    static boolean isReusePortAvailable() {
        return SO_REUSEPORT_AVAILABLE;
    }

    /**
     * Returns true if exclusive binding is on
     */
    static boolean useExclusiveBind() {
        return EXCLUSIVE_BIND;
    }

    /**
     * Tells whether both IPV6_XXX and IP_XXX socket options should be set on
     * IPv6 sockets. On some kernels, both IPV6_XXX and IP_XXX socket options
     * need to be set so that the settings are effective for IPv4 connections
     * and datagrams.
     */
    static boolean shouldSetBothIPv4AndIPv6Options() {
        return shouldSetBothIPv4AndIPv6Options0();
    }

    /**
     * Tells whether IPv6 sockets can join IPv4 multicast groups
     */
    static boolean canIPv6SocketJoinIPv4Group() {
        return canIPv6SocketJoinIPv4Group0();
    }

    /**
     * Tells whether {@link #join6} can be used to join an IPv4
     * multicast group (IPv4 group as IPv4-mapped IPv6 address)
     */
    static boolean canJoin6WithIPv4Group() {
        return canJoin6WithIPv4Group0();
    }

    /**
     * Tells whether IPV6_XXX socket options should be used on an IPv6 socket
     * that is bound to an IPv4 address.
     */
    static boolean canUseIPv6OptionsWithIPv4LocalAddress() {
        return canUseIPv6OptionsWithIPv4LocalAddress0();
    }

    public static InetSocketAddress checkAddress(SocketAddress sa) {
        Objects.requireNonNull(sa);
        if (!(sa instanceof InetSocketAddress isa))
            throw new UnsupportedAddressTypeException(); // ## needs arg
        if (isa.isUnresolved())
            throw new UnresolvedAddressException(); // ## needs arg
        InetAddress addr = isa.getAddress();
        if (!(addr instanceof Inet4Address || addr instanceof Inet6Address))
            throw new IllegalArgumentException("Invalid address type: " + addr.getClass().getName());
        return isa;
    }

    static InetSocketAddress checkAddress(SocketAddress sa, ProtocolFamily family) {
        InetSocketAddress isa = checkAddress(sa);
        if (family == StandardProtocolFamily.INET) {
            InetAddress addr = isa.getAddress();
            if (!(addr instanceof Inet4Address))
                throw new UnsupportedAddressTypeException();
        }
        return isa;
    }

    static InetSocketAddress asInetSocketAddress(SocketAddress sa) {
        if (!(sa instanceof InetSocketAddress isa))
            throw new UnsupportedAddressTypeException();
        return isa;
    }

    static void translateToSocketException(Exception x)
        throws SocketException
    {
        if (x instanceof SocketException se)
            throw se;
        Exception nx = x;
        if (x instanceof ClosedChannelException)
            nx = newSocketException("Socket is closed");
        else if (x instanceof NotYetConnectedException)
            nx = newSocketException("Socket is not connected");
        else if (x instanceof AlreadyBoundException)
            nx = newSocketException("Already bound");
        else if (x instanceof NotYetBoundException)
            nx = newSocketException("Socket is not bound yet");
        else if (x instanceof UnsupportedAddressTypeException)
            nx = newSocketException("Unsupported address type");
        else if (x instanceof UnresolvedAddressException)
            nx = newSocketException("Unresolved address");
        else if (x instanceof IOException) {
            nx = newSocketException(x.getMessage());
        }
        if (nx != x)
            nx.initCause(x);

        if (nx instanceof SocketException se)
            throw se;
        else if (nx instanceof RuntimeException re)
            throw re;
        else
            throw new Error("Untranslated exception", nx);
    }

    private static SocketException newSocketException(String msg) {
        return new SocketException(msg);
    }

    static void translateException(Exception x,
                                   boolean unknownHostForUnresolved)
        throws IOException
    {
        if (x instanceof IOException ioe)
            throw ioe;
        // Throw UnknownHostException from here since it cannot
        // be thrown as a SocketException
        if (unknownHostForUnresolved &&
            (x instanceof UnresolvedAddressException))
        {
             throw new UnknownHostException();
        }
        translateToSocketException(x);
    }

    static void translateException(Exception x)
        throws IOException
    {
        translateException(x, false);
    }

    /**
     * Returns the local address after performing a SecurityManager#checkConnect.
     */
    static InetSocketAddress getRevealedLocalAddress(SocketAddress sa) {
        InetSocketAddress isa = (InetSocketAddress) sa;
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (isa != null && sm != null) {
            try {
                sm.checkConnect(isa.getAddress().getHostAddress(), -1);
            } catch (SecurityException e) {
                // Return loopback address only if security check fails
                isa = getLoopbackAddress(isa.getPort());
            }
        }
        return isa;
    }

    @SuppressWarnings("removal")
    static String getRevealedLocalAddressAsString(SocketAddress sa) {
        InetSocketAddress isa = (InetSocketAddress) sa;
        if (System.getSecurityManager() == null) {
            return isa.toString();
        } else {
            return getLoopbackAddress(isa.getPort()).toString();
        }
    }

    private static InetSocketAddress getLoopbackAddress(int port) {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }

    private static final InetAddress ANY_LOCAL_INET4ADDRESS;
    private static final InetAddress ANY_LOCAL_INET6ADDRESS;
    private static final InetAddress INET4_LOOPBACK_ADDRESS;
    private static final InetAddress INET6_LOOPBACK_ADDRESS;
    static {
        try {
            ANY_LOCAL_INET4ADDRESS = inet4FromInt(0);
            assert ANY_LOCAL_INET4ADDRESS instanceof Inet4Address
                    && ANY_LOCAL_INET4ADDRESS.isAnyLocalAddress();

            ANY_LOCAL_INET6ADDRESS = InetAddress.getByAddress(new byte[16]);
            assert ANY_LOCAL_INET6ADDRESS instanceof Inet6Address
                    && ANY_LOCAL_INET6ADDRESS.isAnyLocalAddress();

            INET4_LOOPBACK_ADDRESS = inet4FromInt(0x7f000001);
            assert INET4_LOOPBACK_ADDRESS instanceof Inet4Address
                    && INET4_LOOPBACK_ADDRESS.isLoopbackAddress();

            byte[] bytes = new byte[16];
            bytes[15] = 0x01;
            INET6_LOOPBACK_ADDRESS = InetAddress.getByAddress(bytes);
            assert INET6_LOOPBACK_ADDRESS instanceof Inet6Address
                    && INET6_LOOPBACK_ADDRESS.isLoopbackAddress();
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    static InetAddress inet4LoopbackAddress() {
        return INET4_LOOPBACK_ADDRESS;
    }

    static InetAddress inet6LoopbackAddress() {
        return INET6_LOOPBACK_ADDRESS;
    }

    /**
     * Returns the wildcard address that corresponds to the given protocol family.
     *
     * @see InetAddress#isAnyLocalAddress()
     */
    static InetAddress anyLocalAddress(ProtocolFamily family) {
        if (family == StandardProtocolFamily.INET) {
            return ANY_LOCAL_INET4ADDRESS;
        } else if (family == StandardProtocolFamily.INET6) {
            return ANY_LOCAL_INET6ADDRESS;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns any IPv4 address of the given network interface, or
     * null if the interface does not have any IPv4 addresses.
     */
    @SuppressWarnings("removal")
    static Inet4Address anyInet4Address(final NetworkInterface interf) {
        return AccessController.doPrivileged(new PrivilegedAction<Inet4Address>() {
            public Inet4Address run() {
                Enumeration<InetAddress> addrs = interf.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address inet4Address) {
                        return inet4Address;
                    }
                }
                return null;
            }
        });
    }

    /**
     * Returns an IPv4 address as an int.
     */
    static int inet4AsInt(InetAddress ia) {
        if (ia instanceof Inet4Address) {
            byte[] addr = ia.getAddress();
            int address  = addr[3] & 0xFF;
            address |= ((addr[2] << 8) & 0xFF00);
            address |= ((addr[1] << 16) & 0xFF0000);
            address |= ((addr[0] << 24) & 0xFF000000);
            return address;
        }
        throw shouldNotReachHere();
    }

    /**
     * Returns an InetAddress from the given IPv4 address
     * represented as an int.
     */
    static InetAddress inet4FromInt(int address) {
        byte[] addr = new byte[4];
        addr[0] = (byte) ((address >>> 24) & 0xFF);
        addr[1] = (byte) ((address >>> 16) & 0xFF);
        addr[2] = (byte) ((address >>> 8) & 0xFF);
        addr[3] = (byte) (address & 0xFF);
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException uhe) {
            throw shouldNotReachHere();
        }
    }

    /**
     * Returns an IPv6 address as a byte array
     */
    static byte[] inet6AsByteArray(InetAddress ia) {
        if (ia instanceof Inet6Address) {
            return ia.getAddress();
        }

        // need to construct IPv4-mapped address
        if (ia instanceof Inet4Address) {
            byte[] ip4address = ia.getAddress();
            byte[] address = new byte[16];
            address[10] = (byte)0xff;
            address[11] = (byte)0xff;
            address[12] = ip4address[0];
            address[13] = ip4address[1];
            address[14] = ip4address[2];
            address[15] = ip4address[3];
            return address;
        }

        throw shouldNotReachHere();
    }

    // -- Socket options

    static final ExtendedSocketOptions EXTENDED_OPTIONS =
            ExtendedSocketOptions.getInstance();

    static void setSocketOption(FileDescriptor fd, SocketOption<?> name, Object value)
        throws IOException
    {
        setSocketOption(fd, Net.UNSPEC, name, value);
    }

    static void setSocketOption(FileDescriptor fd, ProtocolFamily family,
                                SocketOption<?> name, Object value)
        throws IOException
    {
        if (value == null)
            throw new IllegalArgumentException("Invalid option value");

        // only simple values supported by this method
        Class<?> type = name.type();
        boolean isIPv6 = (family == StandardProtocolFamily.INET6);

        if (EXTENDED_OPTIONS.isOptionSupported(name)) {
            EXTENDED_OPTIONS.setOption(fd, name, value, isIPv6);
            return;
        }

        if (type != Integer.class && type != Boolean.class)
            throw shouldNotReachHere();

        // special handling
        if (name == StandardSocketOptions.SO_RCVBUF ||
            name == StandardSocketOptions.SO_SNDBUF)
        {
            int i = ((Integer)value).intValue();
            if (i < 0)
                throw new IllegalArgumentException("Invalid send/receive buffer size");
        }
        if (name == StandardSocketOptions.SO_LINGER) {
            int i = ((Integer)value).intValue();
            if (i < 0)
                value = Integer.valueOf(-1);
            if (i > 65535)
                value = Integer.valueOf(65535);
        }
        if (name == StandardSocketOptions.IP_TOS) {
            int i = ((Integer)value).intValue();
            if (i < 0 || i > 255)
                throw new IllegalArgumentException("Invalid IP_TOS value");
        }
        if (name == StandardSocketOptions.IP_MULTICAST_TTL) {
            int i = ((Integer)value).intValue();
            if (i < 0 || i > 255)
                throw new IllegalArgumentException("Invalid TTL/hop value");
        }

        // map option name to platform level/name
        OptionKey key = SocketOptionRegistry.findOption(name, family);
        if (key == null)
            throw new AssertionError("Option not found");

        int arg;
        if (type == Integer.class) {
            arg = ((Integer)value).intValue();
        } else {
            boolean b = ((Boolean)value).booleanValue();
            arg = (b) ? 1 : 0;
        }

        boolean mayNeedConversion = (family == UNSPEC);
        setIntOption0(fd, mayNeedConversion, key.level(), key.name(), arg, isIPv6);
    }

    /**
     * Sets a IPPROTO_IPV6/IPPROTO level socket. Some platforms require both
     * IPPROTO_IPV6 and IPPROTO socket options to be set when the socket is IPv6.
     * In that case, the IPPROTO socket option is set on a best effort basis.
     */
    static <T> void setIpSocketOption(FileDescriptor fd, ProtocolFamily family,
                                      SocketOption<T> opt, T value)
        throws IOException
    {
        setSocketOption(fd, family, opt, value);
        if (family == StandardProtocolFamily.INET6 && shouldSetBothIPv4AndIPv6Options()) {
            try {
                setSocketOption(fd, StandardProtocolFamily.INET, opt, value);
            } catch (IOException ignore) { }
        }
    }

    static Object getSocketOption(FileDescriptor fd, SocketOption<?> name)
        throws IOException
    {
        return getSocketOption(fd, Net.UNSPEC, name);
    }

    static Object getSocketOption(FileDescriptor fd, ProtocolFamily family, SocketOption<?> name)
        throws IOException
    {
        Class<?> type = name.type();

        if (EXTENDED_OPTIONS.isOptionSupported(name)) {
            boolean isIPv6 = (family == StandardProtocolFamily.INET6);
            return EXTENDED_OPTIONS.getOption(fd, name, isIPv6);
        }

        // only simple values supported by this method
        if (type != Integer.class && type != Boolean.class)
            throw shouldNotReachHere();

        // map option name to platform level/name
        OptionKey key = SocketOptionRegistry.findOption(name, family);
        if (key == null)
            throw new AssertionError("Option not found");

        boolean mayNeedConversion = (family == UNSPEC);
        int value = getIntOption0(fd, mayNeedConversion, key.level(), key.name());

        if (type == Integer.class) {
            return Integer.valueOf(value);
        } else {
            return (value == 0) ? Boolean.FALSE : Boolean.TRUE;
        }
    }

    private static boolean isFastTcpLoopbackRequested() {
        String loopbackProp = GetPropertyAction
                .privilegedGetProperty("jdk.net.useFastTcpLoopback", "false");
        return loopbackProp.isEmpty() || Boolean.parseBoolean(loopbackProp);
    }

    // -- Socket operations --

    private static native boolean isIPv6Available0();

    private static native boolean isReusePortAvailable0();

    /*
     * Returns 1 for Windows and -1 for Linux/Mac OS
     */
    private static native int isExclusiveBindAvailable();

    private static native boolean shouldSetBothIPv4AndIPv6Options0();

    private static native boolean canIPv6SocketJoinIPv4Group0();

    private static native boolean canJoin6WithIPv4Group0();

    private static native boolean canUseIPv6OptionsWithIPv4LocalAddress0();

    static FileDescriptor socket(boolean stream) throws IOException {
        return socket(UNSPEC, stream);
    }

    static FileDescriptor socket(ProtocolFamily family, boolean stream) throws IOException {
        boolean preferIPv6 = isIPv6Available() &&
            (family != StandardProtocolFamily.INET);
        return IOUtil.newFD(socket0(preferIPv6, stream, false, FAST_LOOPBACK));
    }

    static FileDescriptor serverSocket(boolean stream) {
        return serverSocket(UNSPEC, stream);
    }

    static FileDescriptor serverSocket(ProtocolFamily family, boolean stream) {
        boolean preferIPv6 = isIPv6Available() &&
            (family != StandardProtocolFamily.INET);
        return IOUtil.newFD(socket0(preferIPv6, stream, true, FAST_LOOPBACK));
    }

    // Due to oddities SO_REUSEADDR on Windows reuse is ignored
    private static native int socket0(boolean preferIPv6, boolean stream, boolean reuse,
                                      boolean fastLoopback);

    public static void bind(FileDescriptor fd, InetAddress addr, int port)
        throws IOException
    {
        bind(UNSPEC, fd, addr, port);
    }

    static void bind(ProtocolFamily family, FileDescriptor fd,
                     InetAddress addr, int port) throws IOException
    {
        boolean preferIPv6 = isIPv6Available() &&
            (family != StandardProtocolFamily.INET);
        if (addr.isLinkLocalAddress()) {
            addr = IPAddressUtil.toScopedAddress(addr);
        }
        bind0(fd, preferIPv6, EXCLUSIVE_BIND, addr, port);
    }

    private static native void bind0(FileDescriptor fd, boolean preferIPv6,
                                     boolean useExclBind, InetAddress addr,
                                     int port)
        throws IOException;

    static native void listen(FileDescriptor fd, int backlog) throws IOException;

    static int connect(FileDescriptor fd, InetAddress remote, int remotePort)
        throws IOException
    {
        return connect(UNSPEC, fd, remote, remotePort);
    }

    static int connect(ProtocolFamily family, FileDescriptor fd, InetAddress remote, int remotePort)
        throws IOException
    {
        if (remote.isLinkLocalAddress()) {
            remote = IPAddressUtil.toScopedAddress(remote);
        }
        boolean preferIPv6 = isIPv6Available() &&
            (family != StandardProtocolFamily.INET);
        return connect0(preferIPv6, fd, remote, remotePort);
    }

    static int connect(ProtocolFamily family, FileDescriptor fd, SocketAddress remote)
        throws IOException
    {
        InetSocketAddress isa = (InetSocketAddress) remote;
        return connect(family, fd, isa.getAddress(), isa.getPort());
    }

    private static native int connect0(boolean preferIPv6,
                                       FileDescriptor fd,
                                       InetAddress remote,
                                       int remotePort)
        throws IOException;

    public static native int accept(FileDescriptor fd,
                                    FileDescriptor newfd,
                                    InetSocketAddress[] isaa)
        throws IOException;

    public static final int SHUT_RD = 0;
    public static final int SHUT_WR = 1;
    public static final int SHUT_RDWR = 2;

    static native void shutdown(FileDescriptor fd, int how) throws IOException;

    private static native int localPort(FileDescriptor fd)
        throws IOException;

    private static native InetAddress localInetAddress(FileDescriptor fd)
        throws IOException;

    public static InetSocketAddress localAddress(FileDescriptor fd)
        throws IOException
    {
        return new InetSocketAddress(localInetAddress(fd), localPort(fd));
    }

    private static native int remotePort(FileDescriptor fd)
        throws IOException;

    private static native InetAddress remoteInetAddress(FileDescriptor fd)
        throws IOException;

    static InetSocketAddress remoteAddress(FileDescriptor fd)
        throws IOException
    {
        return new InetSocketAddress(remoteInetAddress(fd), remotePort(fd));
    }

    private static native int getIntOption0(FileDescriptor fd, boolean mayNeedConversion,
                                            int level, int opt)
        throws IOException;

    private static native void setIntOption0(FileDescriptor fd, boolean mayNeedConversion,
                                             int level, int opt, int arg, boolean isIPv6)
        throws IOException;

    /**
     * Polls a file descriptor for events.
     * @param timeout the timeout to wait; 0 to not wait, -1 to wait indefinitely
     * @return the polled events or 0 if no events are polled
     */
    static native int poll(FileDescriptor fd, int events, long timeout)
        throws IOException;

    /**
     * Performs a non-blocking poll of a file descriptor.
     * @return the polled events or 0 if no events are polled
     */
    static int pollNow(FileDescriptor fd, int events) throws IOException {
        return poll(fd, events, 0);
    }

    /**
     * Polls a connecting socket to test if the connection has been established.
     *
     * @apiNote This method is public to allow it to be used by code in jdk.sctp.
     *
     * @param timeout the timeout to wait; 0 to not wait, -1 to wait indefinitely
     * @return true if connected
     */
    public static native boolean pollConnect(FileDescriptor fd, long timeout)
        throws IOException;

    /**
     * Performs a non-blocking poll of a connecting socket to test if the
     * connection has been established.
     *
     * @return true if connected
     */
    static boolean pollConnectNow(FileDescriptor fd) throws IOException {
        return pollConnect(fd, 0);
    }

    /**
     * Return the number of bytes in the socket input buffer.
     */
    static native int available(FileDescriptor fd) throws IOException;

    /**
     * Send one byte of urgent data (MSG_OOB) on the socket.
     */
    static native int sendOOB(FileDescriptor fd, byte data) throws IOException;

    /**
     * Read and discard urgent data (MSG_OOB) on the socket.
     */
    static native boolean discardOOB(FileDescriptor fd) throws IOException;

    // -- Multicast support --

    /**
     * Join IPv4 multicast group
     */
    static int join4(FileDescriptor fd, int group, int interf, int source)
        throws IOException
    {
        return joinOrDrop4(true, fd, group, interf, source);
    }

    /**
     * Drop membership of IPv4 multicast group
     */
    static void drop4(FileDescriptor fd, int group, int interf, int source)
        throws IOException
    {
        joinOrDrop4(false, fd, group, interf, source);
    }

    private static native int joinOrDrop4(boolean join, FileDescriptor fd, int group, int interf, int source)
        throws IOException;

    /**
     * Block IPv4 source
     */
    static int block4(FileDescriptor fd, int group, int interf, int source)
        throws IOException
    {
        return blockOrUnblock4(true, fd, group, interf, source);
    }

    /**
     * Unblock IPv4 source
     */
    static void unblock4(FileDescriptor fd, int group, int interf, int source)
        throws IOException
    {
        blockOrUnblock4(false, fd, group, interf, source);
    }

    private static native int blockOrUnblock4(boolean block, FileDescriptor fd, int group,
                                              int interf, int source)
        throws IOException;

    /**
     * Join IPv6 multicast group
     */
    static int join6(FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException
    {
        return joinOrDrop6(true, fd, group, index, source);
    }

    /**
     * Drop membership of IPv6 multicast group
     */
    static void drop6(FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException
    {
        joinOrDrop6(false, fd, group, index, source);
    }

    private static native int joinOrDrop6(boolean join, FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException;

    /**
     * Block IPv6 source
     */
    static int block6(FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException
    {
        return blockOrUnblock6(true, fd, group, index, source);
    }

    /**
     * Unblock IPv6 source
     */
    static void unblock6(FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException
    {
        blockOrUnblock6(false, fd, group, index, source);
    }

    static native int blockOrUnblock6(boolean block, FileDescriptor fd, byte[] group, int index, byte[] source)
        throws IOException;

    static native void setInterface4(FileDescriptor fd, int interf) throws IOException;

    static native int getInterface4(FileDescriptor fd) throws IOException;

    static native void setInterface6(FileDescriptor fd, int index) throws IOException;

    static native int getInterface6(FileDescriptor fd) throws IOException;

    private static native void initIDs();

    /**
     * Event masks for the various poll system calls.
     * They will be set platform dependent in the static initializer below.
     */
    public static final short POLLIN;
    public static final short POLLOUT;
    public static final short POLLERR;
    public static final short POLLHUP;
    public static final short POLLNVAL;
    public static final short POLLCONN;

    static native short pollinValue();
    static native short polloutValue();
    static native short pollerrValue();
    static native short pollhupValue();
    static native short pollnvalValue();
    static native short pollconnValue();

    static {
        IOUtil.load();
        initIDs();

        POLLIN     = pollinValue();
        POLLOUT    = polloutValue();
        POLLERR    = pollerrValue();
        POLLHUP    = pollhupValue();
        POLLNVAL   = pollnvalValue();
        POLLCONN   = pollconnValue();
    }

    static {
        int availLevel = isExclusiveBindAvailable();
        if (availLevel >= 0) {
            String exclBindProp = GetPropertyAction
                    .privilegedGetProperty("sun.net.useExclusiveBind");
            if (exclBindProp != null) {
                EXCLUSIVE_BIND = exclBindProp.isEmpty() || Boolean.parseBoolean(exclBindProp);
            } else {
                EXCLUSIVE_BIND = (availLevel == 1);
            }
        } else {
            EXCLUSIVE_BIND = false;
        }

        FAST_LOOPBACK = isFastTcpLoopbackRequested();

        IPV6_AVAILABLE = isIPv6Available0();
        SO_REUSEPORT_AVAILABLE = isReusePortAvailable0();
    }

    private static AssertionError shouldNotReachHere() {
        return new AssertionError("Should not reach here");
    }

}
