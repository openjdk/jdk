package jdk.internal.natives.include.netinet;

import jdk.internal.ValueBased;
import jdk.internal.natives.StructUtil;
import jdk.internal.natives.StructMapper;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

@ValueBased
final class InAddrImpl
        implements InAddr {

    private final MemorySegment segment;

    private InAddrImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public int s_addr() {
        return segment.get(ValueLayout.JAVA_INT, 0);
    }

    @Override
    public void s_addr(int value) {
        segment.set(ValueLayout.JAVA_INT, 0, value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InAddr other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("InAddrImpl", LAYOUT, segment);
    }

    static StructMapper<InAddr> mapper() {
        return StructMapper.of(LAYOUT, InAddrImpl::new);
    }

}
