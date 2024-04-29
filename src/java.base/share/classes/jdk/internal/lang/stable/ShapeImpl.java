package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;

import java.util.Arrays;

public record ShapeImpl(int[] shape, @Override int size)
        implements StableArray.Shape {

    static final StableArray.Shape ZERO_SHAPE = of();

    @Override
    public int nDimensions() {
        return shape.length;
    }

    @Override
    public int dimension(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index is negative: " + index);
        }
        if (index >= nDimensions()) {
            throw new IllegalArgumentException("index is not in the range [0, " + nDimensions() + "): " + index);
        }
        return shape[index];
    }

    @Override
    public boolean isZeroDimensional() {
        return shape.length == 0;
    }

    @Override
    public String toString() {
        return "Shape" + Arrays.toString(shape) + " (" + size + ")";
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof ShapeImpl other) &&
                Arrays.equals(shape, other.shape);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(shape);
    }

    public static StableArray.Shape of(int... shape) {
        final int size = (shape.length == 0)
                ? 0
                : Math.toIntExact(Arrays.stream(shape)
                .mapToLong(i -> i)
                .reduce(1L, Math::multiplyExact));
        return new ShapeImpl(shape, size);
    }

}
