package jdk.internal.natives.include.netinet6;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.StructUtil;
import jdk.internal.natives.include.SockAddr;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;

@ValueBased
public final class SockAddrIn6Impl
        implements SockAddrIn6 {

    private static final long SIN_6_SCOPE_ID_OFFSET =
            SockAddrIn6.LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("sin6_scope_id"));
    private static final AddressLayout SIN_6_ADDR_PTR = ADDRESS.withTargetLayout(In6Addr.LAYOUT);
    private static final long SIN_6_ADDR_OFFSET = LAYOUT.byteOffset(groupElement("sin6_addr"));

    private final MemorySegment segment;

    private SockAddrIn6Impl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public byte sin6_len() {
        return segment.get(ValueLayout.JAVA_BYTE, 0);
    }

    @Override
    public void sin6_len(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, 0, value);
    }

    @Override
    public byte sin6_family() {
        return segment.get(ValueLayout.JAVA_BYTE, 1);
    }

    @Override
    public void sin6_family(byte value) {
        segment.set(ValueLayout.JAVA_BYTE, 1, value);
    }

    @Override
    public short sin6_port() {
        return segment.get(ValueLayout.JAVA_SHORT, 2);
    }

    @Override
    public void sin6_port(short value) {
        segment.set(ValueLayout.JAVA_SHORT, 2, value);
    }

    @Override
    public int sin6_flowinfo() {
        return segment.get(ValueLayout.JAVA_INT, 4);
    }

    @Override
    public void sin6_flowinfo(int value) {
        segment.set(ValueLayout.JAVA_INT, 4, value);
    }

    @Override
    public In6Addr sin6_addr() {
        System.out.println("segment.address() = " + segment.address());
        System.out.println("SIN_6_ADDR_OFFSET = " + SIN_6_ADDR_OFFSET);
        var lu = segment.get(JAVA_LONG_UNALIGNED, SIN_6_ADDR_OFFSET);
        System.out.println("lu = " + lu);
        var l = segment.get(JAVA_LONG, SIN_6_ADDR_OFFSET);
        System.out.println("l = " + l);
        var seg2 = segment.get(ADDRESS, SIN_6_ADDR_OFFSET);
        System.out.println("seg2 = " + seg2);
        var seg = segment.get(SIN_6_ADDR_PTR, SIN_6_ADDR_OFFSET);
        return seg.equals(MemorySegment.NULL)
                ? null
                : In6Addr.of(seg);
    }

    @Override
    public int sin6_scope_id() {
        return segment.get(ValueLayout.JAVA_INT, SIN_6_SCOPE_ID_OFFSET);
    }

    @Override
    public void sin6_scope_id(int value) {
        segment.set(ValueLayout.JAVA_INT, SIN_6_SCOPE_ID_OFFSET, value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SockAddrIn6 other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("SockAddrIn6Impl", LAYOUT, segment);
    }

    static StructMapper<SockAddrIn6> mapper() {
        return StructMapper.of(LAYOUT, SockAddrIn6Impl::new);
    }

}
