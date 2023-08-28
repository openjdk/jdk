package jdk.internal.natives.include;

import jdk.internal.ValueBased;
import jdk.internal.foreign.Utils;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.StructUtil;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static jdk.internal.natives.CLayouts.*;

@ValueBased
public final class IfReqImpl implements IfReq {

    private static final long SIZEOF_NAME = 16;

    private final MemorySegment segment;

    private IfReqImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    MemorySegment nameSlice() {
        return segment.asSlice(0, SIZEOF_NAME);
    }

    @Override
    public String ifr_name() {
        return nameSlice().getUtf8String(0);
    }

    @Override
    public void ifr_name(String name) {
        Utils.copy(nameSlice(), name.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IfrU ifr_ifru() {
        return IfrU.of(segment.asSlice(16, IfrU.LAYOUT));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IfReq other
                && StructUtil.equals(this.segment, other.segment());
    }

    @Override
    public int hashCode() {
        return StructUtil.hashCode(segment);
    }

    @Override
    public String toString() {
        return StructUtil.toString("IfReqImpl", LAYOUT, segment);
    }

    static StructMapper<IfReq> mapper() {
        return StructMapper.of(LAYOUT, IfReqImpl::new);
    }

    @ValueBased
    public static final class IfrUImpl implements IfrU {

        private final MemorySegment segment;

        private IfrUImpl(MemorySegment segment) {
            this.segment = Objects.requireNonNull(segment);
        }

        @Override
        public MemorySegment segment() {
            return segment;
        }

        @Override
        public SockAddr ifru_addr() {
            return SockAddr.of(segment);
        }

        @Override
        public SockAddr ifru_dstaddr() {
            return SockAddr.of(segment);
        }

        @Override
        public SockAddr ifru_broadaddr() {
            return SockAddr.of(segment);
        }

        @Override
        public short ifru_flags() {
            return (short) C_SHORT.varHandle().get(segment);
        }

        @Override
        public int ifru_mtu() {
            return (int)C_INT.varHandle().get(segment);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IfrU other
                    && StructUtil.equals(this.segment, other.segment());
        }

        @Override
        public int hashCode() {
            return StructUtil.hashCode(segment);
        }

        @Override
        public String toString() {
            return StructUtil.toString("IfrUImpl", LAYOUT, segment);
        }

        static StructMapper<IfrU> mapper() {
            return StructMapper.of(LAYOUT, IfrUImpl::new);
        }

    }

}
