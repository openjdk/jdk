package jdk.internal.lang;

import jdk.internal.lang.stable.StableArrayImpl;
import jdk.internal.lang.stable.TrustedFieldType;

/**
 * An atomic, thread-safe, stable array holder for which components can be set at most once.
 * <p>
 * Stable arrays are eligible for certain optimizations by the JVM.
 * <p>
 * The total number of components ({@linkplain StableArray#length()}) in a stable array
 * can not exceed about 2<sup>31</sup>.
 *
 * @param <V> type of StableValue that this stable array holds
 * @since 23
 */
public sealed interface StableArray<V>
        extends TrustedFieldType
        permits StableArrayImpl {

    /**
     * {@return the {@code [index]} component of this stable array}
     *
     * @param index to use as a component index
     * @throws ArrayIndexOutOfBoundsException if {@code
     *         index < 0 || index >= size()}
     */
    StableValue<V> get(int index);

    /**
     * {@return the length of this stable array}
     */
    int length();

    /**
     * {@return a new StableArray with the provided length}
     *
     * @param <V> type of StableValue the stable array holds
     * @throws IllegalArgumentException if the provided {@code length} is {@code < 0}
     */
    static <V> StableArray<V> of(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        return StableArrayImpl.of(length);
    }

}
