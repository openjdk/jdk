package jdk.internal.natives.include.netinet6;

/*
  Generated partly via: jextract --source -t jdk.internal.natives.include.netinet \
                        /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h
 */

import jdk.internal.ValueBased;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.StructUtil;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;

@ValueBased
public final class In6AddrImpl
        implements In6Addr {

    private final MemorySegment segment;

    private In6AddrImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public __u6_addr __u6_addr() {
        return null;
    }

    @Override
    public MemorySegment __u6_addrAsSegment() {
        return segment;
    }

    @Override
    public void __u6_addr(__u6_addr value) {

    }

    @Override
    public boolean equals(Object o) {
        return o instanceof In6Addr other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("In6AddrImpl", LAYOUT, segment);
    }

    static StructMapper<In6Addr> mapper() {
        return StructMapper.of(LAYOUT, In6AddrImpl::new);
    }


    static class __u6_addrImpl implements __u6_addr {

        private final MemorySegment segment;

        private __u6_addrImpl(MemorySegment segment) {
            this.segment = Objects.requireNonNull(segment);
        }

        @Override
        public MemorySegment segment() {
            return segment;
        }

        @Override
        public byte[] __u6_addr8() {
            return segment.toArray(JAVA_BYTE);
        }

        @Override
        public void __u6_addr8(byte[] value) {
            if (value.length != 16) {
                throw new IllegalArgumentException("Length must be 16: " + value.length);
            }
            MemorySegment.copy(value, 0, segment, JAVA_BYTE, 0, 16);
        }

        @Override
        public short[] __u6_addr16() {
            return segment.toArray(JAVA_SHORT);
        }

        @Override
        public void __u6_addr16(short[] value) {
            if (value.length != 8) {
                throw new IllegalArgumentException("Length must be 8: " + value.length);
            }
            MemorySegment.copy(value, 0, segment, JAVA_SHORT, 0, 8);
        }

        @Override
        public int[] __u6_addr32() {
            return segment.toArray(JAVA_INT);
        }

        @Override
        public void __u6_addr32(int[] value) {
            if (value.length != 4) {
                throw new IllegalArgumentException("Length must be 4: " + value.length);
            }
            MemorySegment.copy(value, 0, segment, JAVA_INT, 0, 4);
        }

    }

}
