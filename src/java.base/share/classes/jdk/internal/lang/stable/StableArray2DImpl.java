package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray2D;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;

import static jdk.internal.lang.stable.StableUtil.newGenericArray;

public record StableArray2DImpl<V>(
        @Stable int dim0,
        @Stable int dim1,
        @Stable V[] elements,
        AuxiliaryArrays aux
) implements StableArray2D<V> {

    private StableArray2DImpl(int dim0, int dim1) {
        this(dim0, dim1, Math.multiplyExact(dim0, dim1));
    }

    // Todo: Remove when "statements before super" is a final feature
    private StableArray2DImpl(int dim0, int dim1, int length) {
        this(dim0, dim1, newGenericArray(length), AuxiliaryArrays.create(length));
    }

    @Override
    public StableValue<V> get(int i0, int i1) {
        Objects.checkIndex(i0, dim0);
        Objects.checkIndex(i1, dim1);
        final int index = i0 * dim0 + i1;
        return new StableValueElement<>(elements, index, aux);
    }

    @Override
    public int length(int dimension) {
        return switch (dimension) {
            case  0 -> dim0;
            case  1 -> dim1;
            default -> throw new IllegalArgumentException();
        };
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        if (dim0 == 0 || dim1 == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        final int dim0 = length(0);
        final int dim1 = length(1);
        for (int i = 0; i < dim0; i++) {
            if (i != 0) {
                sb.append(',').append(' ');
            }
            sb.append('[');
            for (int j = 0; j < dim1; j++) {
                if (j != 0) {
                    sb.append(',').append(' ');
                }
                final StableValue<V> stable = get(i, j);
                if (stable.isSet()) {
                    final V v = stable.orThrow();
                    sb.append(v == this ? "(this StableArray)" : stable);
                } else {
                    sb.append(stable);
                }
            }
            sb.append(']');
        }
        sb.append(']');
        return sb.toString();
    }

    public static <V> StableArray2D<V> of(int dim0, int dim1) {
        return new StableArray2DImpl<>(dim0, dim1);
    }

}
