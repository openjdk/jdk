package jdk.internal.natives.include.netinet;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.StructUtil;
import jdk.internal.natives.StructMapper;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

@ValueBased
public final class SockAddrInImpl
        implements SockAddrIn {

    private final MemorySegment segment;

    private SockAddrInImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public byte sin_len() {
        return segment.get(ValueLayout.JAVA_BYTE, 0);
    }

    @Override
    public void sin_len(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, 0, value);
    }

    @Override
    public byte sin_family() {
        return segment.get(ValueLayout.JAVA_BYTE, 1);
    }

    @Override
    public void sin_family(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, 1, value);
    }

    @Override
    public short sin_port() {
        return segment().get(ValueLayout.JAVA_SHORT, 2);
    }

    @Override
    public void sin_port(short value) {
        segment.set(ValueLayout.JAVA_SHORT, 2, value);
    }

    @Override
    public InAddr sin_addr() {
        return InAddr.of(segment().asSlice(4, LAYOUT));
    }

    @Override
    public MemorySegment sin_zero() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SockAddrIn other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("SockAddrInImpl", LAYOUT, segment);
    }

    static StructMapper<SockAddrIn> mapper() {
        return StructMapper.of(LAYOUT, SockAddrInImpl::new);
    }

}
