package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

public final class UnderlyingHolder<U> {

    public interface Has {
        UnderlyingHolder<?> underlyingHolder();
    }

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
