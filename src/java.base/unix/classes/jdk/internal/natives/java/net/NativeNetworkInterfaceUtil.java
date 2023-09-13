package jdk.internal.natives.java.net;

import jdk.internal.natives.include.netinet.SockAddrIn;
import jdk.internal.natives.include.netinet6.In6Addr;
import jdk.internal.natives.include.netinet6.SockAddrIn6;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

final class NativeNetworkInterfaceUtil {

    private NativeNetworkInterfaceUtil() {
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

    /*
     * Determines the prefix value for an AF_INET6 subnet address.
     */
    static short translateIPv6AddressToPrefix(SockAddrIn6 addr) {
        short prefix = 0;
        if (addr.segment().equals(MemorySegment.NULL)) {
            return 0;
        }

        byte[] addrBytes = addr
                .sin6_addrAsSegment().toArray(ValueLayout.JAVA_BYTE);

        int b, bit;
        for (b = 0; b < In6Addr.LAYOUT.byteSize(); b++, prefix += 8) {
            if (addrBytes[b] != (byte) 0xff) {
                break;
            }
        }

        if (b != In6Addr.LAYOUT.byteSize()) {
            for (bit = 7; bit != 0; bit--, prefix++) {
                if ((addrBytes[b] & (1 << bit)) == 0) {
                    break;
                }
            }
            for (; bit != 0; bit--) {
                if ((addrBytes[b] & (1 << bit)) == 0) {
                    prefix = 0;
                    break;
                }
            }
            if (prefix > 0) {
                b++;
                for (; b < In6Addr.LAYOUT.byteSize(); b++) {
                    if (addrBytes[b] != 0) {
                        prefix = 0;
                    }
                }
            }
        }
        return prefix;
    }

}
