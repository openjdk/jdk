package jdk.internal.natives.include.netinet6;

import jdk.internal.natives.HasCopyFrom;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.include.netinet.InAddr;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;

import static jdk.internal.natives.CLayouts.*;

/**
 * {@snippet :
 * struct in6_addr {
 *     union  __u6_addr;
 * };
 * }
 */
public interface In6Addr extends HasSegment, HasCopyFrom<In6Addr> {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            __u6_addr.LAYOUT.withName("__u6_addr")
    ).withName("in6_addr");

    __u6_addr __u6_addr();
    void __u6_addr(__u6_addr value);

    interface __u6_addr extends HasSegment, HasCopyFrom<__u6_addr> {

        UnionLayout LAYOUT = MemoryLayout.unionLayout(
                MemoryLayout.sequenceLayout(16, C_CHAR).withName("__u6_addr8"),
                MemoryLayout.sequenceLayout(8, C_SHORT).withName("__u6_addr16"),
                MemoryLayout.sequenceLayout(4, C_INT).withName("__u6_addr32")
        );

    }

}
