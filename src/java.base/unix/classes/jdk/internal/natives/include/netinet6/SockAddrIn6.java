package jdk.internal.natives.include.netinet6;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.HasCopyFrom;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.include.netinet.InAddr;
import jdk.internal.natives.include.netinet.SockAddrInImpl;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

import static jdk.internal.natives.CLayouts.*;

/**
 * {@snippet :
 * struct sockaddr_in6 {
 *     __uint8_t sin6_len;
 *     sa_family_t sin6_family;
 *     in_port_t sin6_port;
 *     __uint32_t sin6_flowinfo;
 *     struct in6_addr sin6_addr;
 *     __uint32_t sin6_scope_id;
 * };
 * }
 */
@ValueBased
public interface SockAddrIn6 extends HasSegment, HasCopyFrom<SockAddrIn6> {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_CHAR.withName("sin6_len"),
            C_CHAR.withName("sin6_family"),
            C_SHORT.withName("sin6_port"),
            C_INT.withName("sin6_flowinfo"),
            In6Addr.LAYOUT.withName("sin6_addr"),
            C_INT.withName("sin6_scope_id")
    ).withName("sockaddr_in6");

    StructMapper<SockAddrIn6> MAPPER = SockAddrIn6Impl.mapper();

    /**
     * {@return a byte value by extracting the {@code sin_len} element}
     */
    byte sin6_len();

    /**
     * Sets the {@code sin_len} element in the backing segment to the
     * provided {@code value}.
     */
    void sin6_len(byte value);

    /**
     * {@return a byte value by extracting the {@code sin_family} element}
     */
    byte sin6_family();

    /**
     * Sets the {@code sin_family} element in the backing segment to the
     * provided {@code value}.
     */
    void sin6_family(byte value);

    /**
     * {@return a short value by extracting the {@code sin_port} element}
     */
    short sin6_port();

    /**
     * Sets the {@code sin_port} element in the backing segment to the
     * provided {@code value}.
     */
    void sin6_port(short value);

    int sin6_flowinfo();

    void sin6_flowinfo(int value);

    In6Addr sin6_addr();

    // void sin6_addr(In6Addr value);

    int sin6_scope_id();

    void sin6_scope_id(int value);

    static SockAddrIn6 of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

}
