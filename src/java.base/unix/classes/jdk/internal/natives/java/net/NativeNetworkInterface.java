package jdk.internal.natives.java.net;

import jdk.internal.natives.include.IfConf;
import jdk.internal.natives.include.IfReq;
import jdk.internal.natives.include.SockAddr;
import jdk.internal.natives.include.UniStdUtil;
import jdk.internal.natives.include.netinet.SockAddrIn;
import jdk.internal.natives.include.sys.SocketUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.NetworkInterface2;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static jdk.internal.natives.include.net.IfUtil.IFF_BROADCAST;
import static jdk.internal.natives.include.sys.ErrNo.*;
import static jdk.internal.natives.include.sys.IoCtlUtil.ioctl;
import static jdk.internal.natives.include.sys.SockIoUtil.*;
import static jdk.internal.natives.include.sys.SocketUtil.*;
import static jdk.internal.natives.java.net.NativeNetworkInterfaceUtil.translateIPv4AddressToPrefix;

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
        }
        return result;
    }

    public static void enumIPv4Interfaces(Arena arena,
                                          Fd socket,
                                          List<NetworkInterface2> interfaces) {

        IfConf ifc = IfConf.MAPPER.allocate(arena);
        // Create an alias for ifc_ifcu
        IfConf.IfcU ifcU = ifc.ifc_ifcu();

        // do a dummy SIOCGIFCONF to determine the buffer size
        // SIOCGIFCOUNT doesn't work
        ifcU.ifcu_buf(MemorySegment.NULL);


        // TEST

/*        var buff = arena.allocate(4096);
        ifc.ifc_len((int)buff.byteSize());
        ifcU.ifcu_buf(buff);*/

        // TEST

        if (ioctl(socket.fd(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
            return;
        }

        // call SIOCGIFCONF to enumerate the interfaces
        long byteSize = ifc.ifc_len();
        MemorySegment buffSegment = arena.allocate(byteSize);
        ifcU.ifcu_buf(buffSegment);
        List<IfReq> buffers = IfReq.MAPPER.ofElements(buffSegment); // Nice if we could get the backing segment here. Maybe a record?

        if (ioctl(socket.fd(), SIOCGIFCONF, ifc) < 0) {
                /*JNU_ThrowByNameWithMessageAndLastError
                        (env, JNU_JAVANETPKG "SocketException", "ioctl(SIOCGIFCONF) failed");*/
            return;
        }

        // iterate through each interface
        for (IfReq ifreq : buffers) {
            System.out.println("ifreq = " + ifreq);

            SockAddr addr = SockAddr.MAPPER.allocate(arena);
            IfReq.IfrU ifrequ = ifreq.ifr_ifru();
            SockAddr broadaddr = SockAddr.MAPPER.allocate(arena);
            short prefix = 0;

            // ignore non IPv4 addresses
            if (ifrequ.ifru_addr().sa_family() != AF_INET) {
                continue;
            }

            // save socket address
            addr.copyFrom(ifrequ.ifru_addr());

            // determine broadcast address, if applicable
            if ((ioctl(socket.fd(), SIOCGIFFLAGS, ifrequ) == 0) &&
                    (ifrequ.ifru_flags() & IFF_BROADCAST) != 0) {

                // restore socket address to ifreqP
                ifrequ.ifru_addr().copyFrom(addr);

                if (ioctl(socket.fd(), SIOCGIFBRDADDR, ifrequ) == 0) {
                    broadaddr.copyFrom(ifrequ.ifru_addr());
                }

            }

            // restore socket address to ifreqP
            ifrequ.ifru_addr().copyFrom(addr);

            // determine netmask
            if (ioctl(socket.fd(), SIOCGIFNETMASK, ifrequ) == 0) {
                // Suspicious cast
                prefix = translateIPv4AddressToPrefix(SockAddrIn.of(ifrequ.ifru_netmask().segment()));
            }

            // add interface to the list
            interfaces.add(create(socket, ifreq.ifr_name(), addr, broadaddr, AF_INET, prefix, interfaces.size()));
        }
    }

    private static NetworkInterface2 create(Fd socket,
                                           String ifName,
                                           SockAddr addr,
                                           SockAddr broadAddr,
                                           int family,
                                           short prefix,
                                           int index) {

        // Todo: Fix a lot of stuff here...
        try {
            InetAddress address = InetAddress.getByAddress(ifName, addr.sa_data().toArray(ValueLayout.JAVA_BYTE));
            return new NetworkInterface2(ifName, index, new InetAddress[]{address});
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }


    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Implement me!");
    }

}
