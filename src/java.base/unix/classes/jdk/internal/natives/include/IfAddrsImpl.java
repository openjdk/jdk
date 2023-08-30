package jdk.internal.natives.include;

import jdk.internal.ValueBased;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.StructUtil;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static java.lang.foreign.MemoryLayout.PathElement.*;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static jdk.internal.natives.StructUtil.getUtf8StringOrNull;

@ValueBased
public final class IfAddrsImpl implements IfAddrs {

    private static final long IFA_NAME_OFFSET = LAYOUT.byteOffset(groupElement("ifa_name"));
    private static final long IFA_FLAGS_OFFSET = LAYOUT.byteOffset(groupElement("ifa_flags"));
    private static final AddressLayout SOCKET_ADDR_PTR = ADDRESS.withTargetLayout(SockAddr.LAYOUT);
    private static final long IFA_ADDR_OFFSET = LAYOUT.byteOffset(groupElement("ifa_addr"));
    private static final long IFA_NETMASK_OFFSET = LAYOUT.byteOffset(groupElement("ifa_netmask"));
    private static final long IFA_DSTADDR_OFFSET = LAYOUT.byteOffset(groupElement("ifa_dstaddr"));

    private final MemorySegment segment;

    private IfAddrsImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public IfAddrs ifa_next() {
        return dereference(segment);
    }

    @Override
    public String ifa_name() {
        return getUtf8StringOrNull(segment, IFA_NAME_OFFSET);
    }

    @Override
    public int ifa_flags() {
        return segment.get(JAVA_INT, IFA_FLAGS_OFFSET);
    }

    @Override
    public SockAddr ifa_addr() {
        return sockAddrOrNull(IFA_ADDR_OFFSET);
    }

    @Override
    public SockAddr ifa_netmask() {
        return sockAddrOrNull(IFA_NETMASK_OFFSET);
    }

    @Override
    public SockAddr ifa_dstaddr() {
        return sockAddrOrNull(IFA_DSTADDR_OFFSET);
    }

    private SockAddr sockAddrOrNull(long offset) {
        var seg = segment.get(SOCKET_ADDR_PTR, IFA_ADDR_OFFSET);
        return seg.equals(MemorySegment.NULL)
                ? null
                : SockAddr.of(seg);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IfConf other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("IfAddrs", LAYOUT, segment);
    }

    static StructMapper<IfAddrs> mapper() {
        return StructMapper.of(LAYOUT, IfAddrsImpl::new);
    }

    static IfAddrs dereference(MemorySegment pointerSegment) {
        if (pointerSegment.equals(MemorySegment.NULL)) {
            return null;
        }
        // Todo: How to reinterpret the segment under the same lifecycle
        // Maybe the mapper must only use an arena and we save this arena?
        // How to free the new segment? Consider AutoCloseable for IfAddrs?

        MemorySegment nextSegment = pointerSegment.get(ADDRESS.withTargetLayout(IfAddrs.LAYOUT), 0);
        return nextSegment.equals(MemorySegment.NULL)
                ? null
                : MAPPER.of(nextSegment);
    }

}
