package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;

public record StableArrayImpl<V>(
        @Stable StableValueImpl<V>[] elements
) implements StableArray<V> {

    private StableArrayImpl(int length) {
        this(StableUtil.newStableValueArray(length));
    }

    @ForceInline
    @Override
    public StableValue<V> get(int firstIndex) {
        Objects.checkIndex(firstIndex, elements.length);
        StableValueImpl<V> stable = elements[firstIndex];
        return stable == null
                ? StableUtil.getOrSetVolatile(elements, firstIndex)
                : stable;
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
                sb.append(',').append(' ');
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
