package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * This class is thread safe.
 *
 * @param <U> the underlying type
 */
public final class UnderlyingHolder<U> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final long COUNTER_OFFSET =
            UNSAFE.objectFieldOffset(UnderlyingHolder.class, "counter");

    // Used reflectively
    private volatile U underlying;

    // Used reflectively
    private volatile int counter;

    public UnderlyingHolder(U underlying, int counter) {
        this.underlying = underlying;
        this.counter = counter;
    }

    public U underlying() {
        return underlying;
    }

    // For testing only
    public int counter() {
        return counter;
    }

    public void countDown() {
        if (UNSAFE.getAndAddInt(this, COUNTER_OFFSET, -1) == 1) {
            // Do not reference the underlying function anymore so it can be collected.
            underlying = null;
        }
    }

}
