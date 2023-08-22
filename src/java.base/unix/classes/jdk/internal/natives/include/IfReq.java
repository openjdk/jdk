package jdk.internal.natives.include;

import jdk.internal.foreign.Utils;
import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jdk.internal.natives.CLayouts.*;

// Generated partly via: jextract --source -t jdk.internal.natives.include -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/ /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/net/if.h

/**
 * {@snippet :
 * struct ifreq {
 *     char ifr_name[16];
 *     union  ifr_ifru;
 * };
 * }
 */
public final class IfReq {

    private IfReq() {}

    private static final long SIZEOF_NAME = 16;

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(SIZEOF_NAME, C_CHAR).withName("ifr_name"),
            Ifru.LAYOUT
    ).withName("ifreq");

    public static String getName(MemorySegment segment) {
        return sliceName(segment).getUtf8String(0);
    }

    public static void setName(MemorySegment segment, String name) {
        Utils.copy(sliceName(segment), name.getBytes(StandardCharsets.UTF_8));
    }

    // Consider dropping this
    public static MemorySegment sliceName(MemorySegment seg) {
        return seg.asSlice(0, SIZEOF_NAME);
    }

    public static MemorySegment sliceUnion(MemorySegment seg) {
        return seg.asSlice(SIZEOF_NAME, Ifru.LAYOUT);
    }

    /**
     * {@snippet :
     * union {
     *     struct sockaddr ifru_addr;
     *     struct sockaddr ifru_dstaddr;
     *     struct sockaddr ifru_broadaddr;
     *     short ifru_flags;
     *     int ifru_metric;
     *     int ifru_mtu;
     *     int ifru_phys;
     *     int ifru_media;
     *     int ifru_intval;
     *     caddr_t ifru_data;
     *     struct ifdevmtu ifru_devmtu;
     *     struct ifkpi ifru_kpi;
     *     u_int32_t ifru_wake_flags;
     *     u_int32_t ifru_route_refcnt;
     *     int ifru_cap[2];
     *     u_int32_t ifru_functional_type;
     * };
     * }
     */
    public static final class Ifru {

        private Ifru() {}

        public static final UnionLayout LAYOUT = MemoryLayout.unionLayout(
                MemoryLayout.structLayout(
                        C_CHAR.withName("sa_len"),
                        C_CHAR.withName("sa_family"),
                        MemoryLayout.sequenceLayout(14, C_CHAR).withName("sa_data")
                ).withName("ifru_addr"),
                MemoryLayout.structLayout(
                        C_CHAR.withName("sa_len"),
                        C_CHAR.withName("sa_family"),
                        MemoryLayout.sequenceLayout(14, C_CHAR).withName("sa_data")
                ).withName("ifru_dstaddr"),
                MemoryLayout.structLayout(
                        C_CHAR.withName("sa_len"),
                        C_CHAR.withName("sa_family"),
                        MemoryLayout.sequenceLayout(14, C_CHAR).withName("sa_data")
                ).withName("ifru_broadaddr"),
                C_SHORT.withName("ifru_flags"),
                C_INT.withName("ifru_metric"),
                C_INT.withName("ifru_mtu"),
                C_INT.withName("ifru_phys"),
                C_INT.withName("ifru_media"),
                C_INT.withName("ifru_intval"),
                C_POINTER.withName("ifru_data"),
                MemoryLayout.structLayout(
                        C_INT.withName("ifdm_current"),
                        C_INT.withName("ifdm_min"),
                        C_INT.withName("ifdm_max")
                ).withName("ifru_devmtu"),
                MemoryLayout.structLayout(
                        C_INT.withName("ifk_module_id"),
                        C_INT.withName("ifk_type"),
                        MemoryLayout.unionLayout(
                                C_POINTER.withName("ifk_ptr"),
                                C_INT.withName("ifk_value")
                        ).withName("ifk_data")
                ).withName("ifru_kpi"),
                C_INT.withName("ifru_wake_flags"),
                C_INT.withName("ifru_route_refcnt"),
                MemoryLayout.sequenceLayout(2, C_INT).withName("ifru_cap"),
                C_INT.withName("ifru_functional_type")
        ).withName("ifr_ifru");

        private static final VarHandle INT_ACCESS = ((ValueLayouts.OfIntImpl) JAVA_INT).accessHandle();

        public static int mtu(MemorySegment segment) {
            return (int)INT_ACCESS.get(segment);
        }


    }





    public record ifruAddr(byte sa_len, byte sa_family, byte[] sa_data) {
        private static final StructLayout LAYOUT = MemoryLayout.structLayout(
                C_CHAR.withName("sa_len"),
                C_CHAR.withName("sa_family"),
                MemoryLayout.sequenceLayout(14, C_CHAR).withName("sa_data")
        ).withName("ifru_addr");

        private static final long SA_FAMILY_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("sa_family"));
        private static final long SA_DATA_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("sa_data"));

        public ifruAddr(MemorySegment segment) {
            this(segment.get(C_CHAR, 0),
                    segment.get(C_CHAR, SA_FAMILY_OFFSET),
                    segment.asSlice(SA_DATA_OFFSET).toArray(C_CHAR));
        }
    }

}
