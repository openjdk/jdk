package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static jdk.internal.lang.stable.StableUtil.illegalShape;

public final class StableArrayOne<V> implements StableArray<V> {

    private static final MethodHandle GETTER = getter0();

    @Stable
    private int size;
    @Stable
    private final Shape shape;
    @Stable
    private final V[] elements;
    @Stable
    private final int[] states;
    private final Object[] mutexes;
    private final boolean[] supplyings;

    @SuppressWarnings("unchecked")
    public StableArrayOne(Shape shape) {
        this.shape = shape;
        this.size = shape.size();
        this.elements = (V[]) new Object[size];
        this.states = new int[size];
        this.mutexes = new Object[size];
        this.supplyings = new boolean[size];
    }

    @Override
    public Shape shape() {
        return shape;
    }

    @Override
    public StableValue<V> get() {
        throw illegalShape(0, shape());
    }

    @Override
    public StableValue<V> get(int firstIndex) {
        Objects.checkIndex(firstIndex, size);
        return new StableValueElement<>(elements, states, mutexes, supplyings, firstIndex);
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
        if (indices.length == 1) {
            return get(indices[0]);
        }
        throw illegalShape(indices.length, shape());
    }

    @Override
    public MethodHandle getter() {
        return GETTER;
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    static MethodHandle getter0() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            return lookup.findVirtual(StableArrayOne.class, "get", MethodType.methodType(StableValue.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
