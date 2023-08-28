package jdk.internal.natives.java.net;

import jdk.internal.natives.include.IfConf;
import jdk.internal.natives.include.IfReq;
import jdk.internal.natives.include.SockAddr;
import jdk.internal.natives.include.UniStdUtil;
import jdk.internal.natives.include.sys.SocketUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static jdk.internal.natives.include.net.If.IFF_BROADCAST;
import static jdk.internal.natives.include.sys.ErrNo.*;
import static jdk.internal.natives.include.sys.IoCtlUtil.ioctl;
import static jdk.internal.natives.include.sys.SockIoUtil.*;
import static jdk.internal.natives.include.sys.SocketUtil.*;

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
            IfReq ifReq = IfReq.MAPPER.of(arena);
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

    public static List<NetworkInterface> enumInterfaces() {
        List<NetworkInterface> result = new ArrayList<>();
        try (var arena = Arena.ofConfined()) {
            var errSeg = arena.allocate(CAPTURE_STATE_LAYOUT);
            var socket = SocketUtil.socket(errSeg, AF_INET, SOCK_DGRAM, 0);

            if (isNoSupport(socket)) {
                return null;
            }

            if (!socket.isError()) {
                result = enumIPv4Interfaces(arena, socket, result);
                UniStdUtil.close(socket);
            }
        }
        return result;
    }

    public static List<NetworkInterface> enumIPv4Interfaces(Arena arena,
                                                            Fd socket,
                                                            List<NetworkInterface> interfaces) {


        var ifc = IfConf.MAPPER.of(arena);
        // Create an alias for ifc_ifcu
        var ifcU = ifc.ifc_ifcu();

        // do a dummy SIOCGIFCONF to determine the buffer size
        // SIOCGIFCOUNT doesn't work
        ifcU.ifcu_buf(MemorySegment.NULL);

        if (ioctl(socket.fd(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
            return interfaces;
        }

        // call SIOCGIFCONF to enumerate the interfaces
        var byteSize = ifc.ifc_len();
        var buffSegment = arena.allocate(byteSize);
        ifcU.ifcu_buf(buffSegment);
        List<IfConf> buffers = IfConf.MAPPER.ofElements(buffSegment); // Nice if we could get the backing segment here. Maybe a record?

        if (ioctl(socket.fd(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
            return interfaces;
        }

        // iterate through each interface
        for (var buff:buffers) {

        }


        // iterate through each interface
        for (int offset = 0; offset < byteSize; offset += IfReq.LAYOUT.byteSize()) {
            var addr = arena.allocate(SockAddr.LAYOUT); // Move outside loop
            var ifreq = ifc.asSlice(offset, IfReq.LAYOUT);
            var ifrequ = IfReq.sliceUnion(ifreq);
            var broadaddr = arena.allocate(SockAddr.LAYOUT);

            // ignore non IPv4 addresses
            if (IfReq.IfrU.saFamily(ifrequ) != AF_INET) {
                continue;
            }

            // save socket address
            addr.copyFrom(ifrequ.asSlice(0, SockAddr.LAYOUT));

            // determine broadcast address, if applicable
            if ((ioctl(socket.result(), SIOCGIFFLAGS, ifrequ) == 0) &&
                    (IfReq.IfrU.flags(ifrequ) & IFF_BROADCAST) != 0) {

                // restore socket address to ifreqP
                ifrequ.copyFrom(addr.asSlice(0, SockAddr.LAYOUT));

                if (ioctl(socket.result(), SIOCGIFBRDADDR, ifrequ) == 0) {
                    broadaddr.copyFrom(ifrequ.asSlice(0, SockAddr.LAYOUT));
                }

            }

            // restore socket address to ifreqP
            ifrequ.copyFrom(addr);

            // determine netmask
            if (ioctl(socket.result(), SIOCGIFNETMASK, ifrequ) == 0) {
                var prefix = translateIPv4AddressToPrefix()
            }


            /*
                // restore socket address to ifreqP
                memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

                // determine netmask
                if (ioctl(sock, SIOCGIFNETMASK, ifreqP) == 0) {
                    prefix = translateIPv4AddressToPrefix(
                            (struct sockaddr_in *)&(ifreqP->ifr_netmask));
                }

                // add interface to the list
                ifs = addif(env, sock, ifreqP->ifr_name, ifs,
                        &addr, broadaddrP, AF_INET, prefix);

                // in case of exception, free interface list and buffer and return NULL
                if ((*env)->ExceptionOccurred(env)) {
                    free(buf);
                    freeif(ifs);
                    return NULL;
                }
            }
            */


        }


    }

    /*
     * Enumerates and returns all IPv4 interfaces on Linux.
     */
    static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
        struct ifconf ifc;
        struct ifreq *ifreqP;
        char *buf = NULL;
        unsigned i;

        // do a dummy SIOCGIFCONF to determine the buffer size
        // SIOCGIFCOUNT doesn't work
        ifc.ifc_buf = NULL;
        if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");
            return ifs;
        }

        // call SIOCGIFCONF to enumerate the interfaces
        CHECKED_MALLOC3(buf, char *, ifc.ifc_len);
        ifc.ifc_buf = buf;
        if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");
            free(buf);
            return ifs;
        }

        // iterate through each interface
        ifreqP = ifc.ifc_req;
        for (i = 0; i < ifc.ifc_len / sizeof(struct ifreq); i++, ifreqP++) {
            struct sockaddr addr, broadaddr, *broadaddrP = NULL;
            short prefix = 0;

            // ignore non IPv4 addresses
            if (ifreqP->ifr_addr.sa_family != AF_INET) {
                continue;
            }

            // save socket address
            memcpy(&addr, &(ifreqP->ifr_addr), sizeof(struct sockaddr));

            // determine broadcast address, if applicable
            if ((ioctl(sock, SIOCGIFFLAGS, ifreqP) == 0) &&
                    ifreqP->ifr_flags & IFF_BROADCAST) {

                // restore socket address to ifreqP
                memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

                if (ioctl(sock, SIOCGIFBRDADDR, ifreqP) == 0) {
                    memcpy(&broadaddr, &(ifreqP->ifr_broadaddr),
                            sizeof(struct sockaddr));
                    broadaddrP = &broadaddr;
                }
            }

            // restore socket address to ifreqP
            memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

            // determine netmask
            if (ioctl(sock, SIOCGIFNETMASK, ifreqP) == 0) {
                prefix = translateIPv4AddressToPrefix(
                        (struct sockaddr_in *)&(ifreqP->ifr_netmask));
            }

            // add interface to the list
            ifs = addif(env, sock, ifreqP->ifr_name, ifs,
                    &addr, broadaddrP, AF_INET, prefix);

            // in case of exception, free interface list and buffer and return NULL
            if ((*env)->ExceptionOccurred(env)) {
                free(buf);
                freeif(ifs);
                return NULL;
            }
        }

        // free buffer
        free(buf);
        return ifs;
    }





    public static List<NetworkInterface> enumIPv4InterfacesRaw(Result socket, List<NetworkInterface> interfaces) {
        try (var arena = Arena.ofConfined()) {
            var ifc = arena.allocate(IfConf.LAYOUT);

            // do a dummy SIOCGIFCONF to determine the buffer size
            // SIOCGIFCOUNT doesn't work
            var ifcU = IfConf.sliceUnion(ifc);
            IfConf.IfcU.setBuff(ifcU, MemorySegment.NULL);

            if (ioctl(socket.result(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
                return interfaces;
            }

            // call SIOCGIFCONF to enumerate the interfaces
            var byteSize = IfConf.getLen(ifc);
            var buf = arena.allocate(byteSize);
            IfConf.IfcU.setBuff(IfConf.sliceUnion(ifc), buf);
            if (ioctl(socket.result(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
                return interfaces;
            }

            // iterate through each interface
            for (int offset = 0; offset < byteSize; offset += IfReq.LAYOUT.byteSize()) {
                var addr = arena.allocate(SockAddr.LAYOUT); // Move outside loop
                var ifreq = ifc.asSlice(offset, IfReq.LAYOUT);
                var ifrequ = IfReq.sliceUnion(ifreq);
                var broadaddr = arena.allocate(SockAddr.LAYOUT);

                // ignore non IPv4 addresses
                if (IfReq.IfrU.saFamily(ifrequ) != AF_INET) {
                    continue;
                }

                // save socket address
                addr.copyFrom(ifrequ.asSlice(0, SockAddr.LAYOUT));

                // determine broadcast address, if applicable
                if ((ioctl(socket.result(), SIOCGIFFLAGS, ifrequ) == 0) &&
                        (IfReq.IfrU.flags(ifrequ) & IFF_BROADCAST) != 0) {

                    // restore socket address to ifreqP
                    ifrequ.copyFrom(addr.asSlice(0, SockAddr.LAYOUT));

                    if (ioctl(socket.result(), SIOCGIFBRDADDR, ifrequ) == 0) {
                        broadaddr.copyFrom(ifrequ.asSlice(0, SockAddr.LAYOUT));
                    }

                }

                // restore socket address to ifreqP
                ifrequ.copyFrom(addr);

                // determine netmask
                if (ioctl(socket.result(),SIOCGIFNETMASK, ifrequ) == 0) {
                    var prefix = translateIPv4AddressToPrefix()
                }


            /*
                // restore socket address to ifreqP
                memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

                // determine netmask
                if (ioctl(sock, SIOCGIFNETMASK, ifreqP) == 0) {
                    prefix = translateIPv4AddressToPrefix(
                            (struct sockaddr_in *)&(ifreqP->ifr_netmask));
                }

                // add interface to the list
                ifs = addif(env, sock, ifreqP->ifr_name, ifs,
                        &addr, broadaddrP, AF_INET, prefix);

                // in case of exception, free interface list and buffer and return NULL
                if ((*env)->ExceptionOccurred(env)) {
                    free(buf);
                    freeif(ifs);
                    return NULL;
                }
            }
            */


            }

        }
    }

    /*
     * Enumerates and returns all IPv4 interfaces on Linux.
     */
    static netif *enumIPv4Interfaces(JNIEnv *env, int sock, netif *ifs) {
        struct ifconf ifc;
        struct ifreq *ifreqP;
        char *buf = NULL;
        unsigned i;

        // do a dummy SIOCGIFCONF to determine the buffer size
        // SIOCGIFCOUNT doesn't work
        ifc.ifc_buf = NULL;
        if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");
            return ifs;
        }

        // call SIOCGIFCONF to enumerate the interfaces
        CHECKED_MALLOC3(buf, char *, ifc.ifc_len);
        ifc.ifc_buf = buf;
        if (ioctl(sock, SIOCGIFCONF, (char *)&ifc) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");
            free(buf);
            return ifs;
        }

        // iterate through each interface
        ifreqP = ifc.ifc_req;
        for (i = 0; i < ifc.ifc_len / sizeof(struct ifreq); i++, ifreqP++) {
            struct sockaddr addr, broadaddr, *broadaddrP = NULL;
            short prefix = 0;

            // ignore non IPv4 addresses
            if (ifreqP->ifr_addr.sa_family != AF_INET) {
                continue;
            }

            // save socket address
            memcpy(&addr, &(ifreqP->ifr_addr), sizeof(struct sockaddr));

            // determine broadcast address, if applicable
            if ((ioctl(sock, SIOCGIFFLAGS, ifreqP) == 0) &&
                    ifreqP->ifr_flags & IFF_BROADCAST) {

                // restore socket address to ifreqP
                memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

                if (ioctl(sock, SIOCGIFBRDADDR, ifreqP) == 0) {
                    memcpy(&broadaddr, &(ifreqP->ifr_broadaddr),
                            sizeof(struct sockaddr));
                    broadaddrP = &broadaddr;
                }
            }

            // restore socket address to ifreqP
            memcpy(&(ifreqP->ifr_addr), &addr, sizeof(struct sockaddr));

            // determine netmask
            if (ioctl(sock, SIOCGIFNETMASK, ifreqP) == 0) {
                prefix = translateIPv4AddressToPrefix(
                        (struct sockaddr_in *)&(ifreqP->ifr_netmask));
            }

            // add interface to the list
            ifs = addif(env, sock, ifreqP->ifr_name, ifs,
                    &addr, broadaddrP, AF_INET, prefix);

            // in case of exception, free interface list and buffer and return NULL
            if ((*env)->ExceptionOccurred(env)) {
                free(buf);
                freeif(ifs);
                return NULL;
            }
        }

        // free buffer
        free(buf);
        return ifs;
    }

    /*
     * Determines the prefix value for an AF_INET subnet address.
     */
    // Todo: We lose type safety here in Java: translateIPv4AddressToPrefix(struct sockaddr_in *addr)
    static short translateIPv4AddressToPrefix(MemorySegment addr) {
        short prefix = 0;
        int mask;
        if (addr.equals(MemorySegment.NULL)) {
            return 0;
        }
        mask = ntohl(addr->sin_addr.s_addr);
        while (mask) {
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
