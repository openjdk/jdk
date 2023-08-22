package jdk.internal.natives.java.net;

import jdk.internal.natives.include.IfReq;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import static jdk.internal.foreign.support.LookupUtil.CAPTURE_STATE_LAYOUT;
import static jdk.internal.foreign.support.LookupUtil.error;
import static jdk.internal.natives.include.UniStd.close;
import static jdk.internal.natives.include.sys.Errno.EAFNOSUPPORT;
import static jdk.internal.natives.include.sys.Errno.EPROTONOSUPPORT;
import static jdk.internal.natives.include.sys.IoCtl.ioctl;
import static jdk.internal.natives.include.sys.SockIo.SIOCGIFMTU;
import static jdk.internal.natives.include.sys.Socket.*;

public final class NativeNetworkInterface {

    private NativeNetworkInterface() {}

    public static NetworkInterface getByName(String name) throws SocketException {
        return NetworkInterface.getByName0(name);
    }

    public static NetworkInterface getByIndex(int index) throws SocketException {
        return NetworkInterface.getByIndex0(index);
    }

    public static NetworkInterface getByInetAddress(InetAddress address) throws SocketException {
        return NetworkInterface.getByInetAddress0(address);
    }

    public static NetworkInterface[] getAll() throws SocketException {
        return NetworkInterface.getAll();
    }

    public static boolean boundInetAddress(InetAddress address) throws SocketException {
        return NetworkInterface.boundInetAddress0(address);
    }

    public static boolean isUp(String name, int ind) throws SocketException {
        return NetworkInterface.isUp0(name, ind);
    }

    public static boolean isLoopback(String name, int ind) throws SocketException {
        return NetworkInterface.isLoopback0(name, ind);
    }

    public static boolean supportsMulticast(String name, int ind) throws SocketException {
        return NetworkInterface.supportsMulticast0(name, ind);
    }

    public static boolean isP2P(String name, int ind) throws SocketException {
        return NetworkInterface.isP2P0(name, ind);
    }

    public static byte[] getMacAddr(byte[] inAddr, String name, int ind) throws SocketException {
        return NetworkInterface.getMacAddr0(inAddr, name, ind);
    }

    public static int getMTU(String name, int ind) throws SocketException {
        int socket = openSocketWithFallback(name);
        if (socket < 0) {
            return 0;
        }

        try {
            return getMTU(socket, name);
        } finally {
            close(socket);
        }
    }

    private static int getMTU(int socket, String name) throws SocketException {
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(IfReq.LAYOUT);
            IfReq.setName(segment, name);
            if (ioctl(socket, SIOCGIFMTU, segment) < 0) {
                throw new SocketException("ioctl(SIOCGIFMTU) failed");
            }
            return IfReq.Ifru.mtu(IfReq.sliceUnion(segment));
        }


        /*
            struct ifreq if2;
    memset((char *)&if2, 0, sizeof(if2));
    strncpy(if2.ifr_name, ifname, sizeof(if2.ifr_name) - 1);

    if (ioctl(sock, SIOCGIFMTU, (char *)&if2) < 0) {
        JNU_ThrowByNameWithMessageAndLastError
            (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFMTU) failed");
        return -1;
    }

    return if2.ifr_mtu;
         */
    }

    private static int openSocketWithFallback(String ifname) throws SocketException {
        int sock;

        try (var arena = Arena.ofConfined()) {
            var errSeg = arena.allocate(CAPTURE_STATE_LAYOUT);
            if ((sock = socket(errSeg, AF_INET, SOCK_DGRAM, 0)) < 0) {
                var errno = error(errSeg);
                if (errno == EPROTONOSUPPORT || errno == EAFNOSUPPORT) {
                    if ((sock = socket(errSeg, AF_INET6, SOCK_DGRAM, 0)) < 0) {
                        throw new SocketException("IPV6 Socket creation failed");
                    }
                }
            } else { // errno is not NOSUPPORT
                throw new SocketException("IPV4 Socket creation failed");
            }
        }

        // Linux starting from 2.6.? kernel allows ioctl call with either IPv4 or
        // IPv6 socket regardless of type of address of an interface.
        return sock;

        /*
            int sock;

    if ((sock = socket(AF_INET, SOCK_DGRAM, 0)) < 0) {
        if (errno == EPROTONOSUPPORT || errno == EAFNOSUPPORT) {
            if ((sock = socket(AF_INET6, SOCK_DGRAM, 0)) < 0) {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "IPV6 Socket creation failed");
                return -1;
            }
        } else { // errno is not NOSUPPORT
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "IPV4 Socket creation failed");
            return -1;
        }
    }

    // Linux starting from 2.6.? kernel allows ioctl call with either IPv4 or
    // IPv6 socket regardless of type of address of an interface.
    return sock;
         */
        // throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Implement me!");
    }

}
