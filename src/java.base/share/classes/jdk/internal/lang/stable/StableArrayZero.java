package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

import static jdk.internal.lang.stable.StableUtil.illegalShape;

public final class StableArrayZero<V> implements StableArray<V> {

    private static final MethodHandle GETTER = getter0();

    @Stable
    private final StableValue<V> stable;

    public StableArrayZero() {
        this.stable = StableValue.of();
    }

    @Override
    public Shape shape() {
        return ShapeImpl.ZERO_SHAPE;
    }

    @Override
    public StableValue<V> get() {
        return stable;
    }

    @Override
    public StableValue<V> get(int firstIndex) {
        throw illegalShape(1, shape());
    }

    @Override
    public StableValue<V> get(int firstIndex, int secondIndex) {
        throw illegalShape(2, shape());
    }

    @Override
    public StableValue<V> get(int firstIndex, int secondIndex, int thirdIndex) {
        throw illegalShape(3, shape());
    }

    @Override
    public StableValue<V> get(int... indices) {
        Objects.requireNonNull(indices);
        if (indices.length == 0) {
            return stable;
        }
        throw illegalShape(indices.length, shape());
    }

    @Override
    public String toString() {
        return stable.toString();
    }

    @Override
    public MethodHandle getter() {
        return GETTER;
    }

    static MethodHandle getter0() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            return lookup.findGetter(StableArrayZero.class, "stable", StableValue.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
