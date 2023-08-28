package jdk.internal.natives.include;

import jdk.internal.ValueBased;
import jdk.internal.natives.StructMapper;
import jdk.internal.natives.StructUtil;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.natives.CLayouts.C_INT;
import static jdk.internal.natives.CLayouts.C_POINTER;

@ValueBased
public final class IfConfImpl implements IfConf {

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            C_INT.withName("ifc_len"),
            IfcU.LAYOUT
    ).withName("ifconf");

    private final MemorySegment segment;

    private IfConfImpl(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public int ifc_len() {
        return segment.get(C_INT, 0);
    }

    @Override
    public void ifc_len(int value) {
        segment.set(C_INT, 0, value);
    }

    @Override
    public IfConf.IfcU ifc_ifcu() {
        return IfConf.IfcU.MAPPER.of(segment.asSlice(C_INT.byteSize(), IfConf.IfcU.LAYOUT));
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
        return StructUtil.toString("IfConfImpl", LAYOUT, segment);
    }

    static StructMapper<IfConf> mapper() {
        return StructMapper.of(LAYOUT, IfConfImpl::new);
    }

    /**
     * {@snippet :
     * union {
     *     caddr_t ifcu_buf;
     *     struct ifreq* ifcu_req;
     * };
     * }
     */
    @ValueBased
    public static final class IfcUImpl implements IfcU {

        public static final UnionLayout LAYOUT = MemoryLayout.unionLayout(
                C_POINTER.withName("ifcu_buf"),
                C_POINTER.withName("ifcu_req")
        );

        static final VarHandle IFCU_BUF = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("ifcu_buf"));

        private final MemorySegment segment;

        private IfcUImpl(MemorySegment segment) {
            this.segment = Objects.requireNonNull(segment);
        }

        @Override
        public MemorySegment segment() {
            return segment;
        }

        @Override
        public void ifcu_buf(MemorySegment value) {
            IFCU_BUF.set(segment, value);
        }

        @Override
        public IfReq ifcu_req() {
            var seg = ((MemorySegment)ADDRESS.varHandle().get(segment))
                    .reinterpret(IfReq.LAYOUT.byteSize());
            return IfReq.of(seg);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IfcU other
                    && StructUtil.equals(this.segment, other.segment());
        }

        @Override
        public int hashCode() {
            return StructUtil.hashCode(segment);
        }

        @Override
        public String toString() {
            return StructUtil.toString("IfConfImpl", LAYOUT, segment);
        }


        static StructMapper<IfcU> mapper() {
            return StructMapper.of(LAYOUT, IfcUImpl::new);
        }

    }

}
