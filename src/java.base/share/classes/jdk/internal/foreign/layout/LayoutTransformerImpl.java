package jdk.internal.foreign.layout;

import jdk.internal.ValueBased;
import jdk.internal.foreign.abi.AbstractLinker;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Function;

@ValueBased
public final class LayoutTransformerImpl<T extends MemoryLayout>
        implements LayoutTransformer<T> {

    private final Class<T> type;
    private final Function<? super T, ? extends MemoryLayout> op;

    private LayoutTransformerImpl(Class<T> type,
                                  Function<? super T, ? extends MemoryLayout> op) {
        this.type = type;
        this.op = op;
    }

    @Override
    public MemoryLayout transform(T layout) {
        Objects.requireNonNull(layout);
        return op.apply(layout);
    }

    @Override
    public MemoryLayout deepTransform(MemoryLayout layout) {
        Objects.requireNonNull(layout);

        // Breadth first
        MemoryLayout outer = transformFlat(this, layout);

        // Handle element transformation
        return switch (outer) {
            case SequenceLayout sl -> MemoryLayout.sequenceLayout(sl.elementCount(), deepTransform(sl.elementLayout()));
            case StructLayout sl   -> MemoryLayout.structLayout(applyRecursively(sl));
            case UnionLayout gl    -> MemoryLayout.unionLayout(applyRecursively(gl));
            default -> outer;
        };
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + op + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, op);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LayoutTransformerImpl<?> other &&
                type.equals(other.type) &&
                op.equals(other.op);
    }

    @SuppressWarnings("unchecked")
    private static <T extends MemoryLayout> MemoryLayout transformFlat(LayoutTransformer<T> transformer, MemoryLayout l) {
        LayoutTransformerImpl<T> t = (LayoutTransformerImpl<T>) transformer;
        return switch (t.type) {
            case Class<?> c when c.equals(MemoryLayout.class) ->
                    ((LayoutTransformerImpl<MemoryLayout>)t).transform(l);
            case Class<?> c when c.equals(SequenceLayout.class) &&
                    l instanceof SequenceLayout sl ->
                    ((LayoutTransformerImpl<SequenceLayout>)t).transform(sl);
            case Class<?> c when c.equals(GroupLayout.class) &&
                    l instanceof GroupLayout gl ->
                    ((LayoutTransformerImpl<GroupLayout>)t).transform(gl);
            case Class<?> c when c.equals(StructLayout.class) &&
                    l instanceof StructLayout se ->
                    ((LayoutTransformerImpl<StructLayout>)t).transform(se);
            case Class<?> c when c.equals(UnionLayout.class) &&
                    l instanceof UnionLayout uel ->
                    ((LayoutTransformerImpl<UnionLayout>)t).transform(uel);
            case Class<?> c when c.equals(PaddingLayout.class) &&
                    l instanceof PaddingLayout pl ->
                    ((LayoutTransformerImpl<PaddingLayout>)t).transform(pl);
            case Class<?> c when c.equals(ValueLayout.class) &&
                    l instanceof ValueLayout vl ->
                    ((LayoutTransformerImpl<ValueLayout>)t).transform(vl);
            case Class<?> c when c.equals(ValueLayout.OfBoolean.class) &&
                    l instanceof ValueLayout.OfBoolean bl ->
                    ((LayoutTransformerImpl<ValueLayout.OfBoolean>)t).transform(bl);
            case Class<?> c when c.equals(ValueLayout.OfByte.class) &&
                    l instanceof ValueLayout.OfByte by ->
                    ((LayoutTransformerImpl<ValueLayout.OfByte>)t).transform(by);
            case Class<?> c when c.equals(ValueLayout.OfChar.class) &&
                    l instanceof ValueLayout.OfChar ch ->
                    ((LayoutTransformerImpl<ValueLayout.OfChar>)t).transform(ch);
            case Class<?> c when c.equals(ValueLayout.OfShort.class) &&
                    l instanceof ValueLayout.OfShort sh ->
                    ((LayoutTransformerImpl<ValueLayout.OfShort>)t).transform(sh);
            case Class<?> c when c.equals(ValueLayout.OfInt.class) &&
                    l instanceof ValueLayout.OfInt in ->
                    ((LayoutTransformerImpl<ValueLayout.OfInt>)t).transform(in);
            case Class<?> c when c.equals(ValueLayout.OfLong.class) &&
                    l instanceof ValueLayout.OfLong lo ->
                    ((LayoutTransformerImpl<ValueLayout.OfLong>)t).transform(lo);
            case Class<?> c when c.equals(ValueLayout.OfFloat.class) &&
                    l instanceof ValueLayout.OfFloat fl ->
                    ((LayoutTransformerImpl<ValueLayout.OfFloat>)t).transform(fl);
            case Class<?> c when c.equals(ValueLayout.OfDouble.class) &&
                    l instanceof ValueLayout.OfDouble db ->
                    ((LayoutTransformerImpl<ValueLayout.OfDouble>)t).transform(db);
            case Class<?> c when c.equals(AddressLayout.class) &&
                    l instanceof AddressLayout ad ->
                    ((LayoutTransformerImpl<AddressLayout>)t).transform(ad);
            // No transformation
            default -> l;
        };
    }

    private MemoryLayout[] applyRecursively(GroupLayout groupLayout) {
        return groupLayout.memberLayouts().stream()
                .map(this::deepTransform)
                .toArray(MemoryLayout[]::new);
    }

    static final LayoutTransformer<MemoryLayout> STRIP_NAMES =
            LayoutTransformer.of(MemoryLayout.class, LayoutTransformerImpl::stripMemberName);

    @SuppressWarnings("restricted")
    private static MemoryLayout stripMemberName(MemoryLayout vl) {
        return switch (vl) {
            case AddressLayout al -> al.targetLayout()
                    .map(tl -> al.withoutName().withTargetLayout(STRIP_NAMES.deepTransform(tl))) // restricted
                    .orElseGet(al::withoutName);
            default -> vl.withoutName(); // ValueLayout and PaddingLayout
        };
    }

    static LayoutTransformer<ValueLayout> setByteOrder(ByteOrder byteOrder) {
        return LayoutTransformer.of(ValueLayout.class, vl -> vl.withOrder(byteOrder));
    }

    static <T extends MemoryLayout> LayoutTransformer<T> of(Class<T> type,
                                                            Function<? super T, ? extends MemoryLayout> op) {
        return new LayoutTransformerImpl<>(type, op);
    }

}
