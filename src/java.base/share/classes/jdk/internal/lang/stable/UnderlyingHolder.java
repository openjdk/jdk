package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

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

    // Used reflectively. This field can only transition at most once from being set to a
    // non-null reference to being `null`. Once `null`, it is never read. This allows
    // the field to be non-volatile, which is crucial for getting optimum performance.
    private U underlying;

    // Used reflectively
    private int counter;

    public UnderlyingHolder(U underlying, int counter) {
        this.underlying = underlying;
        this.counter = counter;
        // Safe publication
        UNSAFE.storeStoreFence();
    }

    @ForceInline
    public U underlying() {
        return underlying;
    }

    // For testing only
    public int counter() {
        return UNSAFE.getIntVolatile(this, COUNTER_OFFSET);
    }

    public void countDown() {
        if (UNSAFE.getAndAddInt(this, COUNTER_OFFSET, -1) == 1) {
            // Do not reference the underlying function anymore so it can be collected.
            underlying = null;
        }
    }

}
