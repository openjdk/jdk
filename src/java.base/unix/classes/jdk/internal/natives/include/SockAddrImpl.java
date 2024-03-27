package jdk.internal.natives.include;

import jdk.internal.ValueBased;
import jdk.internal.natives.StructUtil;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

@ValueBased
public final class SockAddrImpl implements SockAddr {

    private final MemorySegment segment;

    private SockAddrImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public byte sa_len() {
        return segment.get(ValueLayout.JAVA_BYTE, 0);
    }

    @Override
    public byte sa_family() {
        return segment.get(ValueLayout.JAVA_BYTE, 1);
    }

    @Override
    public MemorySegment sa_data() {
        return segment.asSlice(2);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SockAddr other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("SockAddrImpl", LAYOUT, segment);
    }

    static StructMapper<SockAddr> mapper() {
        return StructMapper.of(LAYOUT, SockAddrImpl::new);
    }

}
