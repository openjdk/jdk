package jdk.internal.natives.include.netinet;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
*/

import jdk.internal.ValueBased;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

import static jdk.internal.natives.CLayouts.C_INT;

/**
 * {@snippet :
 * struct in_addr {
 *     in_addr_t s_addr;
 * };
 * }
 */
@ValueBased
public interface InAddr extends HasSegment {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_INT.withName("s_addr")
    ).withName("in_addr");

    StructMapper<InAddr> MAPPER = InAddrImpl.mapper();

    /**
     * {@return an int value by extracting the {@code s_addr} element}
     */
    int s_addr();

    /**
     * Sets the {@code s_addr} element in the backing segment to the
     * provided {@code value}.
     */
    void s_addr(int value);

    static InAddr of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

}
