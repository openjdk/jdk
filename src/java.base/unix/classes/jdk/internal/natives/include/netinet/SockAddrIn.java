package jdk.internal.natives.include.netinet;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.HasCopyFrom;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

import static jdk.internal.natives.CLayouts.*;

/**
 * {@snippet :
 * struct sockaddr_in {
 *     __uint8_t sin_len;
 *     sa_family_t sin_family;
 *     in_port_t sin_port;
 *     struct in_addr sin_addr;
 *     char sin_zero[8];
 * };
 * }
 */
@ValueBased
public interface SockAddrIn extends HasSegment, HasCopyFrom<SockAddrIn> {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_CHAR.withName("sin_len"),
            C_CHAR.withName("sin_family"),
            C_SHORT.withName("sin_port"),
            InAddr.LAYOUT.withName("sin_addr"),
            MemoryLayout.sequenceLayout(8, C_CHAR).withName("sin_zero")
    ).withName("sockaddr_in");

    StructMapper<SockAddrIn> MAPPER = SockAddrInImpl.mapper();

    /**
     * {@return a byte value by extracting the {@code sin_len} element}
     */
    byte sin_len();

    /**
     * Sets the {@code sin_len} element in the backing segment to the
     * provided {@code value}.
     */
    void sin_len(byte value);

    /**
     * {@return a byte value by extracting the {@code sin_family} element}
     */
    byte sin_family();

    /**
     * Sets the {@code sin_family} element in the backing segment to the
     * provided {@code value}.
     */
    void sin_family(byte value);

    /**
     * {@return a short value by extracting the {@code sin_port} element}
     */
    short sin_port();

    /**
     * Sets the {@code sin_port} element in the backing segment to the
     * provided {@code value}.
     */
    void sin_port(short value);

    InAddr sin_addr();

    MemorySegment sin_zero();

    static SockAddrIn of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

}
