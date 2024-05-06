package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;

public record StableArrayImpl<V>(
        @Stable V[] elements,
        @Stable int[] states,
        Object[] mutexes,
        boolean[] supplyings
) implements StableArray<V> {

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        this((V[]) new Object[length], new int[length], new Object[length], new boolean[length]);
    }

    @Override
    public StableValue<V> get(int firstIndex) {
        Objects.checkIndex(firstIndex, elements.length);
        return new StableValueElement<>(elements, states, mutexes, supplyings, firstIndex);
    }

    @Override
    public int length() {
        return elements.length;
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
        if (length() == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < length(); i++) {
            if (i != 0) {
                sb.append(',');
            }
            final StableValue<V> stable = get(i);
            if (stable.isSet()) {
                V v = stable.orThrow();
                sb.append(v == this ? "(this StableArray)" : stable);
            } else {
                sb.append(stable);
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static <V> StableArray<V> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
