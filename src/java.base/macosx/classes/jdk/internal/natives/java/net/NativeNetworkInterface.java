package jdk.internal.natives.java.net;

import jdk.internal.natives.include.IfAddrs;
import jdk.internal.natives.include.IfAddrsUtil;
import jdk.internal.natives.include.IfConf;
import jdk.internal.natives.include.IfReq;
import jdk.internal.natives.include.SockAddr;
import jdk.internal.natives.include.UniStdUtil;
import jdk.internal.natives.include.netinet.SockAddrIn;
import jdk.internal.natives.include.sys.SocketUtil;
import jdk.internal.natives.java.NetUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.NetworkInterface2;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.natives.include.net.IfUtil.IFF_BROADCAST;
import static jdk.internal.natives.include.net.IfUtil.IFF_POINTOPOINT;
import static jdk.internal.natives.include.sys.ErrNo.*;
import static jdk.internal.natives.include.sys.IoCtlUtil.ioctl;
import static jdk.internal.natives.include.sys.SockIoUtil.*;
import static jdk.internal.natives.include.sys.SocketUtil.*;

public final class NativeNetworkInterface {

    private NativeNetworkInterface() {}

    public static NetworkInterface2 getByName(String name) throws SocketException {
        throw new UnsupportedOperationException();
        //return NetworkInterface.getByName0(name);
    }

    public static NetworkInterface2 getByIndex(int index) throws SocketException {
        throw new UnsupportedOperationException();
        // return NetworkInterface.getByIndex0(index);
    }

    public static NetworkInterface2 getByInetAddress(InetAddress address) throws SocketException {
        throw new UnsupportedOperationException();
        //return NetworkInterface.getByInetAddress0(address);
    }

    public static NetworkInterface2[] getAll() throws SocketException {
        return enumInterfaces().toArray(NetworkInterface2[]::new);
    }

    public static boolean boundInetAddress(InetAddress address) throws SocketException {
        throw new UnsupportedOperationException();
        //return NetworkInterface.boundInetAddress0(address);
    }

    public static boolean isUp(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException();
        // return NetworkInterface.isUp0(name, ind);
    }

    public static boolean isLoopback(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException();
        // return NetworkInterface.isLoopback0(name, ind);
    }

    public static boolean supportsMulticast(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException();
        // return NetworkInterface.supportsMulticast0(name, ind);
    }

    public static boolean isP2P(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException();
        // return NetworkInterface.isP2P0(name, ind);
    }

    public static byte[] getMacAddr(byte[] inAddr, String name, int ind) throws SocketException {
        throw new UnsupportedOperationException();
        //return NetworkInterface.getMacAddr0(inAddr, name, ind);
    }

    public static int getMTU(String name, int ind) throws SocketException {
        var socket = openSocketWithFallback(name);

        // Todo: Remove this condition as it will never happen
        if (socket.isError()) {
            return 0;
        }

        try {
            return getMTU(socket, name);
        } finally {
            UniStdUtil.close(socket);
        }
    }

    private static int getMTU(Fd socket, String name) throws SocketException {
        try (var arena = Arena.ofConfined()) {
            IfReq ifReq = IfReq.MAPPER.allocate(arena);
            ifReq.ifr_name(name);
            if (ioctl(socket.fd(), SIOCGIFMTU, ifReq) < 0) {
                throw new SocketException("ioctl(SIOCGIFMTU) failed");
            }
            return ifReq.ifr_ifru().ifru_mtu();
        }
    }

    /*
     * Opens a socket for further ioct calls. proto is one of AF_INET or AF_INET6.
     */
    private static int openSocket(int proto) throws SocketException {
        try (var arena = Arena.ofConfined()) {
            var errSeg = arena.allocate(CAPTURE_STATE_LAYOUT);
            var sock = SocketUtil.socket(errSeg, AF_INET, SOCK_DGRAM, 0);
            if (isNoSupport(sock)) {
                throw new SocketException("IPV4 Socket creation failed");
            }
            return sock.fd();
        }
    }

    private static boolean isNoSupport(Fd socket) {
        return socket.isError() && (
                socket.errno() == EPROTONOSUPPORT ||
                        socket.errno() == EAFNOSUPPORT
        );
    }

    /*
     * Opens a socket for further ioctl calls. Tries AF_INET socket first and
     * if it fails return AF_INET6 socket.
     */
    private static Fd openSocketWithFallback(String ifname) throws SocketException {
        try (var arena = Arena.ofConfined()) {
            var errSeg = arena.allocate(CAPTURE_STATE_LAYOUT);
            var sock = SocketUtil.socket(errSeg, AF_INET, SOCK_DGRAM, 0);

            if (sock.isError()) {
                if (isNoSupport(sock)) {
                    sock = SocketUtil.socket(errSeg, AF_INET, SOCK_DGRAM, 0);
                    if (sock.isError()) {
                        throw new SocketException("IPV6 Socket creation failed");
                    }
                } else { // errno is not NOSUPPORT
                    throw new SocketException("IPV4 Socket creation failed");
                }
            }

            // Linux starting from 2.6.? kernel allows ioctl call with either IPv4 or
            // IPv6 socket regardless of type of address of an interface.
            return sock;
        }
    }

    public static List<NetworkInterface2> enumInterfaces() {
        List<NetworkInterface2> result = new ArrayList<>();
        try (var arena = Arena.ofConfined()) {
            var errSeg = arena.allocate(CAPTURE_STATE_LAYOUT);
            var socket = SocketUtil.socket(errSeg, AF_INET, SOCK_DGRAM, 0);

            if (isNoSupport(socket)) {
                return null;
            }

            if (!socket.isError()) {
                try {
                    enumIPv4Interfaces(arena, socket, result);
                } finally {
                    UniStdUtil.close(socket);
                }
            }

            // If IPv6 is available then enumerate IPv6 addresses.
            // User can disable ipv6 explicitly by -Djava.net.preferIPv4Stack=true,
            // so we have to call ipv6_available()
            if (NetUtil.ipv6_available()) {
                socket = SocketUtil.socket(errSeg, AF_INET6, SOCK_DGRAM, 0);
                if (!socket.isError()) {
                    try {
                        enumIPv6Interfaces(arena, socket, result);
                    } finally {
                        UniStdUtil.close(socket);
                    }
                }
            }
        }
        return result;
    }

    public static void enumIPv4Interfaces(Arena arena,
                                          Fd socket,
                                          List<NetworkInterface2> interfaces) {

        var ptr = arena.allocate(ADDRESS.withTargetLayout(IfAddrs.LAYOUT));
        if (IfAddrsUtil.getifaddrs(ptr) != 0) {
            return;
        }

        IfAddrs head = IfAddrs.dereference(ptr);
        try {
            for (var ifa = head; ifa != null; ifa = ifa.ifa_next()) {
                SockAddr broadaddr = null;
                // ignore non IPv4 addresses
                SockAddr ifa_addr = ifa.ifa_addr();
                if (ifa_addr == null || ifa_addr.sa_family() != AF_INET) {
                    continue;
                }

                // set ifa_broadaddr, if there is one
                if ((ifa.ifa_flags() & IFF_POINTOPOINT) == 0 &&
                        (ifa.ifa_flags() & IFF_BROADCAST) != 0) {
                    broadaddr = ifa.ifa_dstaddr();
                }
                interfaces.add(create(socket, ifa.ifa_name(), ifa_addr, broadaddr, AF_INET, (short) 0, interfaces.size()));
            }
        } finally {
            IfAddrsUtil.freeifaddrs(head);
        }
    }

    private static NetworkInterface2 create(Fd socket,
                                           String ifName,
                                           SockAddr addr,
                                           SockAddr broadAddr,
                                           int family,
                                           short prefix,
                                           int index) {

        // Todo: Fix a lot of stuff...
        try {
            byte[] a = addr.sa_data().asSlice(0, 4).toArray(ValueLayout.JAVA_BYTE);
            InetAddress address = InetAddress.getByAddress(ifName, a);
            System.out.println(ifName + " ,address = " + address);
            return new NetworkInterface2(ifName, index, new InetAddress[]{address});
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * Determines the prefix value for an AF_INET subnet address.
     */
    static short translateIPv4AddressToPrefix(SockAddrIn addr) {
        short prefix = 0;
        int mask;
        if (addr.segment().equals(MemorySegment.NULL)) {
            return 0;
        }
        mask = ntohl(addr.sin_addr().s_addr());
        while (mask != 0) {
            mask <<= 1;
            prefix++;
        }
        return prefix;
    }

    private static int ntohl(int value) {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                ? value
                : Integer.reverseBytes(value);
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Implement me!");
    }

}
