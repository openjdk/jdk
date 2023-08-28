package jdk.internal.natives.include;

/*
   Generated partly via:
   jextract --source -t jdk.internal.natives.include.sys /
   -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h \
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

import static jdk.internal.natives.CLayouts.C_CHAR;

/**
 * {@snippet :
 * struct sockaddr {
 *     __uint8_t sa_len;
 *     sa_family_t sa_family;
 *     char sa_data[14];
 * };
 * }
 */
@ValueBased
public interface SockAddr extends HasSegment {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_CHAR.withName("sa_len"),
            C_CHAR.withName("sa_family"),
            MemoryLayout.sequenceLayout(14, C_CHAR).withName("sa_data")
    ).withName("sockaddr");

    StructMapper<SockAddr> MAPPER = SockAddrImpl.mapper();

    /**
     * {@return a byte value by extracting the {@code sa_len} element}
     */
    byte sa_len();

    /**
     * {@return a byte value by extracting the {@code sa_family} element}
     */
    byte sa_family();

    /**
     * {@return a slice for the {@code sa_data} element
     */
    MemorySegment sa_data();

    static SockAddr of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

}
