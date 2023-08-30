package jdk.internal.natives.include;

import jdk.internal.ValueBased;
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

jextract --source -t jdk.internal.natives.includeifaddrs \
/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/ifaddrs.h

 */

/**
 * {@snippet :
 * struct ifaddrs {
 *     struct ifaddrs* ifa_next;
 *     char* ifa_name;
 *     unsigned int ifa_flags;
 *     struct sockaddr* ifa_addr;
 *     struct sockaddr* ifa_netmask;
 *     struct sockaddr* ifa_dstaddr;
 *     void* ifa_data;
 * };
 * }
 */
@ValueBased
public interface IfAddrs extends HasSegment, HasCopyFrom<IfAddrs> {

    StructLayout LAYOUT = MemoryLayout.structLayout(
            C_POINTER.withName("ifa_next"),
            C_STRING.withName("ifa_name"),
            C_INT.withName("ifa_flags"),
            MemoryLayout.paddingLayout(4),
            C_POINTER.withTargetLayout(SockAddr.LAYOUT).withName("ifa_addr"),
            C_POINTER.withTargetLayout(SockAddr.LAYOUT).withName("ifa_netmask"),
            C_POINTER.withTargetLayout(SockAddr.LAYOUT).withName("ifa_dstaddr"),
            C_POINTER.withName("ifa_data")
    ).withName("ifaddrs");

    StructMapper<IfAddrs> MAPPER = IfAddrsImpl.mapper();

    IfAddrs ifa_next();

    String ifa_name();

    int ifa_flags();

    SockAddr ifa_addr();

    SockAddr ifa_netmask();

    SockAddr ifa_dstaddr();

    static IfAddrs of(MemorySegment segment) {
        return MAPPER.of(segment);
    }

    static IfAddrs dereference(MemorySegment pointerSegment) {
        return IfAddrsImpl.dereference(pointerSegment);
    }

}
