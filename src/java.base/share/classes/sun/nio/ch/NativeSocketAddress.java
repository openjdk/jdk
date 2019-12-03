/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;

/**
 * A native socket address that is the union of struct sockaddr, struct sockaddr_in,
 * and struct sockaddr_in6.
 *
 * This class is not thread safe.
 */
class NativeSocketAddress {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    private static final int AF_INET  = AFINET();
    private static final int AF_INET6 = AFINET6();

    private static final int SIZEOF_SOCKETADDRESS = sizeofSOCKETADDRESS();
    private static final int SIZEOF_FAMILY        = sizeofFamily();
    private static final int OFFSET_FAMILY        = offsetFamily();
    private static final int OFFSET_SIN4_PORT     = offsetSin4Port();
    private static final int OFFSET_SIN4_ADDR     = offsetSin4Addr();
    private static final int OFFSET_SIN6_PORT     = offsetSin6Port();
    private static final int OFFSET_SIN6_ADDR     = offsetSin6Addr();
    private static final int OFFSET_SIN6_SCOPE_ID = offsetSin6ScopeId();

    // SOCKETADDRESS
    private final long address;

    // cached copy of SOCKETADDRESS and the corresponding InetSocketAddress
    private final long cachedSocketAddress;
    private InetSocketAddress cachedInetSocketAddress;

    NativeSocketAddress() {
        // allocate 2 * SOCKETADDRESS
        int size = SIZEOF_SOCKETADDRESS << 1;
        long base = UNSAFE.allocateMemory(size);
        UNSAFE.setMemory(base, size, (byte) 0);

        this.address = base;
        this.cachedSocketAddress = base + SIZEOF_SOCKETADDRESS;
    }

    long address() {
        return address;
    }

    void free() {
        UNSAFE.freeMemory(address);
    }

    /**
     * Return an InetSocketAddress to represent the socket address in this buffer.
     * @throws SocketException if the socket address is not AF_INET or AF_INET6
     */
    InetSocketAddress toInetSocketAddress() throws SocketException {
        // return cached InetSocketAddress if the SOCKETADDRESS bytes match
        if (cachedInetSocketAddress != null && mismatch() < 0) {
            return cachedInetSocketAddress;
        }

        // decode SOCKETADDRESS to InetSocketAddress
        int family = family();
        if (family != AF_INET && family != AF_INET6)
            throw new SocketException("Socket family not recognized");
        var isa = new InetSocketAddress(address(family), port(family));

        // copy SOCKETADDRESS and InetSocketAddress
        UNSAFE.copyMemory(null, address, null, cachedSocketAddress, SIZEOF_SOCKETADDRESS);
        this.cachedInetSocketAddress = isa;
        return isa;
    }

    /**
     * Find a mismatch between the SOCKETADDRESS structures stored at address
     * and cachedSocketAddress.
     * @return the byte offset of the first mismatch or -1 if no mismatch
     */
    private int mismatch() {
        int i = ArraysSupport.vectorizedMismatch(null,
                address,
                null,
                cachedSocketAddress,
                SIZEOF_SOCKETADDRESS,
                ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
        if (i >= 0)
            return i;
        i = SIZEOF_SOCKETADDRESS - ~i;
        for (; i < SIZEOF_SOCKETADDRESS; i++) {
            if (UNSAFE.getByte(address + i) != UNSAFE.getByte(cachedSocketAddress + i)) {
                return i;
            }
        }
        return -1;
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
        if (SIZEOF_FAMILY == 1) {
            return UNSAFE.getByte(address + OFFSET_FAMILY);
        } else if (SIZEOF_FAMILY == 2) {
            return UNSAFE.getShort(address + OFFSET_FAMILY);
        } else {
            throw new InternalError();
        }
    }

    /**
     * Return the value of the sin4_port or sin6_port field. These fields are
     * stored in network order.
     */
    private int port(int family) {
        byte b1, b2;
        if (family == AF_INET) {
            b1 = UNSAFE.getByte(address + OFFSET_SIN4_PORT);
            b2 = UNSAFE.getByte(address + OFFSET_SIN4_PORT + 1);
        } else {
            b1 = UNSAFE.getByte(address + OFFSET_SIN6_PORT);
            b2 = UNSAFE.getByte(address + OFFSET_SIN6_PORT + 1);
        }
        return (Byte.toUnsignedInt(b1) << 8) + Byte.toUnsignedInt(b2);
    }

    /**
     * Return an InetAddress to represent the value of the address in the
     * sin4_addr or sin6_addr fields.
     */
    private InetAddress address(int family) {
        int len;
        int offset;
        int scope_id;
        if (family == AF_INET) {
            len = 4;
            offset = OFFSET_SIN4_ADDR;
            scope_id = 0;
        } else {
            len = 16;
            offset = OFFSET_SIN6_ADDR;
            scope_id = UNSAFE.getInt(address + OFFSET_SIN6_SCOPE_ID);
        }
        byte[] bytes = new byte[len];
        UNSAFE.copyMemory(null, address + offset, bytes, ARRAY_BASE_OFFSET, len);
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

    private static native int AFINET();
    private static native int AFINET6();
    private static native int sizeofSOCKETADDRESS();
    private static native int sizeofFamily();
    private static native int offsetFamily();
    private static native int offsetSin4Port();
    private static native int offsetSin4Addr();
    private static native int offsetSin6Port();
    private static native int offsetSin6Addr();
    private static native int offsetSin6ScopeId();

    static {
        IOUtil.load();
    }
}
