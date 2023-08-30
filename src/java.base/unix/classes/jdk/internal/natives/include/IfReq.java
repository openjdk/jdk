package jdk.internal.natives.include;

import jdk.internal.natives.HasCopyFrom;
import jdk.internal.natives.HasSegment;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;

import static jdk.internal.natives.CLayouts.*;

/*

Generated partly via:
jextract --source -t jdk.internal.natives.include -I \
/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/ \
/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/net/if.h

*/

/**
 * {@snippet :
 * struct ifreq {
 *     char ifr_name[16];
 *     union  ifr_ifru;
 * };
 * }
 */
public interface IfReq extends HasSegment, HasCopyFrom<IfReq> {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(16, C_CHAR).withName("ifr_name"),
            IfrU.LAYOUT.withName("ifr_ifru")
    ).withName("ifreq");

    StructMapper<IfReq> MAPPER = IfReqImpl.mapper();

    String ifr_name();

    void ifr_name(String name);

    IfrU ifr_ifru();

    static IfReq of(MemorySegment segment) {
        return MAPPER.of(segment);
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
    interface IfrU extends HasSegment, HasCopyFrom<IfrU> {

        UnionLayout LAYOUT = MemoryLayout.unionLayout(
                SockAddr.LAYOUT.withName("ifru_addr"),
                SockAddr.LAYOUT.withName("ifru_dstaddr"),
                SockAddr.LAYOUT.withName("ifru_broadaddr"),
                SockAddr.LAYOUT.withName("ifru_netmask"), // Added manually
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

        StructMapper<IfrU> MAPPER = IfReqImpl.IfrUImpl.mapper();

        SockAddr ifru_addr();

        SockAddr ifru_dstaddr();

        SockAddr ifru_broadaddr();

        SockAddr ifru_netmask();

        short ifru_flags();

        int ifru_mtu();

        static IfrU of(MemorySegment segment) {
            return MAPPER.of(segment);
        }

    }

}
