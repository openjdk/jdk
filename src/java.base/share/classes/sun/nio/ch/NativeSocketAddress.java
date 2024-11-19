/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemorySegment;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

import jdk.internal.access.JavaNetInetAddressAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.ffi.generated.socket.in_addr;
import jdk.internal.ffi.util.FFMUtils;
import jdk.internal.ffi.generated.socket.sockaddr;
import jdk.internal.ffi.generated.socket.sockaddr_in;
import jdk.internal.ffi.generated.socket.sockaddr_in6;
import jdk.internal.ffi.generated.socket.socket_address_h;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * A native socket address that is the union of struct sockaddr, struct sockaddr_in,
 * and struct sockaddr_in6.
 *
 * This class is not thread safe.
 */
class NativeSocketAddress {

    private static final JavaNetInetAddressAccess JNINA = SharedSecrets.getJavaNetInetAddressAccess();
    private static final int AF_INET = socket_address_h.AF_INET();
    private static final int AF_INET6 = socket_address_h.AF_INET6();

    private final MemorySegment memory;

    long address() {
        return memory.address();
    }

    private NativeSocketAddress(MemorySegment memory) {
        if (memory.byteSize() != sockaddr_in6.layout().byteSize()) {
            throw new IllegalArgumentException();
        }
        this.memory = memory;
    }

    /**
     * Allocate an array of native socket addresses.
     */
    static NativeSocketAddress[] allocate(int count) {
        NativeSocketAddress[] array = new NativeSocketAddress[count];
        for (int i = 0; i < count; i++) {
            try {
                MemorySegment addressMemory = sockaddr_in6.allocate(FFMUtils.SEGMENTS_ALLOCATOR);
                addressMemory.fill((byte)0);
                array[i] = new NativeSocketAddress(addressMemory);
            } catch (OutOfMemoryError e) {
                freeAll(array);
                throw e;
            }
        }
        return array;
    }

    /**
     * Free all non-null native socket addresses in the given array.
     */
    static void freeAll(NativeSocketAddress[] array) {
        for (int i = 0; i < array.length; i++) {
            NativeSocketAddress sa = array[i];
            if (sa != null) {
                FFMUtils.free(sa.memory);
            }
        }
    }

    /**
     * Encodes the given InetSocketAddress into this socket address.
     * @param protocolFamily protocol family
     * @param isa the InetSocketAddress to encode
     * @return the size of the socket address (sizeof sockaddr or sockaddr6)
     * @throws UnsupportedAddressTypeException if the address type is not supported
     */
    int encode(ProtocolFamily protocolFamily, InetSocketAddress isa) {
        if (protocolFamily == StandardProtocolFamily.INET) {
            // struct sockaddr
            InetAddress ia = isa.getAddress();
            if (!(ia instanceof Inet4Address))
                throw new UnsupportedAddressTypeException();
            putFamily(AF_INET);
            putAddress(AF_INET, ia);
            putPort(AF_INET, isa.getPort());
            return (int) sockaddr_in.sizeof();
        } else {
            // struct sockaddr6
            putFamily(AF_INET6);
            putAddress(AF_INET6, isa.getAddress());
            putPort(AF_INET6, isa.getPort());
            sockaddr_in6.sin6_flowinfo(memory, 0);
            return (int) sockaddr_in6.sizeof();
        }
    }

    /**
     * Return an InetSocketAddress to represent the socket address in this buffer.
     * @throws SocketException if the socket address is not AF_INET or AF_INET6
     */
    InetSocketAddress decode() throws SocketException {
        int family = family();
        if (family != AF_INET && family != AF_INET6)
            throw new SocketException("Socket family not recognized: " + family);
        var address = new InetSocketAddress(address(family), port(family));
        return address;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof NativeSocketAddress otherNSA) &&
                memory.mismatch(otherNSA.memory) < 0;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (long offset = 0; offset < memory.byteSize(); offset++) {
            h = 31 * h + memory.get(JAVA_BYTE, offset);
        }
        return h;
    }

    @Override
    public String toString() {
        int family = family();
        if (family == AF_INET || family == AF_INET6) {
            return ((family == AF_INET) ? "AF_INET" : "AF_INET6")
                    + ", address=" + address(family) + ", port=" + port(family);
        } else {
            return "<unknown>";
        }
    }

    /**
     * Return the value of the sa_family field.
     */
    private int family() {
        return sockaddr.sa_family(memory);
    }

    /**
     * Stores the given family in the sa_family field.
     */
    private void putFamily(int family) {
        sockaddr_in.sin_family(memory, family);
    }

    /**
     * Return the value of the sin_port or sin6_port field. These fields are
     * stored in network order.
     */
    private int port(int family) {
        short port;
        if (family == AF_INET) {
            port = sockaddr_in.sin_port(memory);
        } else {
            port = sockaddr_in6.sin6_port(memory);
        }
        int res = Short.toUnsignedInt(port);
        return ((res & 0xFF) << 8) + ((res & 0xFF00) >> 8);
    }

    /**
     * Stores the given port number in the sin_port or sin6_port field. The
     * port is stored in network order.
     */
    private void putPort(int family, int port) {
        if (family == AF_INET) {
            sockaddr_in.sin_port(memory, Short.reverseBytes((short)port));
        } else {
            sockaddr_in6.sin6_port(memory, Short.reverseBytes((short)port));
        }
    }

    /**
     * Return an InetAddress to represent the value of the address in the
     * sin4_addr or sin6_addr fields. For IPv6 addresses, the Inet6Address is
     * created with the sin6_scope_id in the sockaddr_in6 structure.
     */
    private InetAddress address(int family) {
        int scope_id;
        MemorySegment sin_addr_ms;
        if (family == AF_INET) {
            scope_id = 0;
            sin_addr_ms = sockaddr_in.sin_addr(memory);
        } else {
            scope_id = sockaddr_in6.sin6_scope_id(memory);
            sin_addr_ms = sockaddr_in6.sin6_addr(memory);
        }
        byte[] bytes = sin_addr_ms.toArray(JAVA_BYTE);
        try {
            if (scope_id == 0) {
                return InetAddress.getByAddress(bytes);
            } else {
                return Inet6Address.getByAddress(null, bytes, scope_id);
            }
        } catch (UnknownHostException e) {
            throw new InternalError(e);
        }
    }
    // IPv4 portion offset inside an IPv4-mapped IPv6 address
    private static final long IPV4_MAPPED_IPV6_OFFSET = 12L;

    /**
     * Stores the given InetAddress in the sin_addr or sin6_addr/sin6_scope_id
     * fields. For IPv6 addresses, the sin6_addr will be populated with an
     * IPv4-mapped IPv6 address when the given InetAddress is an IPv4 address.
     */
    private void putAddress(int family, InetAddress ia) {
        if (family == AF_INET) {
            // IPv4 address
            putAddress(memory, (Inet4Address) ia);
        } else {
            int scope_id;
            if (ia instanceof Inet4Address) {
                // IPv4-mapped IPv6 address
                var sin6addrMs = sockaddr_in6.sin6_addr(memory);
                sin6addrMs.asSlice(0, IPV4_MAPPED_IPV6_OFFSET - 2).fill((byte) 0);
                byte[] ipv4address = ia.getAddress();
                sin6addrMs.asSlice(IPV4_MAPPED_IPV6_OFFSET - 2, 2).fill((byte) 0xff);
                MemorySegment.copy(ipv4address, 0, sin6addrMs,
                                   JAVA_BYTE, IPV4_MAPPED_IPV6_OFFSET, ipv4address.length);
                scope_id = 0;
            } else {
                // IPv6 address
                var inet6Address = (Inet6Address) ia;
                putAddress(memory, inet6Address);
                scope_id = inet6Address.getScopeId();
            }
            sockaddr_in6.sin6_scope_id(memory, scope_id);
        }
    }

    private static void putAddress(MemorySegment sockaddr, Inet4Address ia) {
        int ipAddress = JNINA.addressValue(ia);
        int saddr = Integer.reverseBytes(ipAddress);
        var sin_addrMemory = sockaddr_in.sin_addr(sockaddr);
        in_addr.s_addr(sin_addrMemory, saddr);
    }

    private static void putAddress(MemorySegment address, Inet6Address ia) {
        byte[] bytes = JNINA.addressBytes(ia); // network byte order
        var sin6addrMs = sockaddr_in6.sin6_addr(address);
        assert bytes.length == sin6addrMs.byteSize();
        MemorySegment.copy(bytes, 0, sin6addrMs, JAVA_BYTE, 0, bytes.length);
    }
}
