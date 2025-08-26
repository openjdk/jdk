package jdk.internal.invoke.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;


/**
 * This class is thread safe. Any thread can create and use an instance of this class
 * at any time. The `function` field is only accessed if `counter` is positive so
 * the setting of function to `null` is safe.
 *
 * @param <U> the underlying function type
 */
public final class FunctionHolder<U> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long COUNTER_OFFSET = UNSAFE.objectFieldOffset(FunctionHolder.class, "counter");

    // This field can only transition at most once from being set to a
    // non-null reference to being `null`. Once `null`, it is never read.
    private U function;
    // Used reflectively via UNSAFE
    private int counter;

    public FunctionHolder(U function, int counter) {
        this.function = function;
        this.counter = counter;
        // Safe publication
        UNSAFE.storeStoreFence();
    }

    @ForceInline
    public U function() {
        return function;
    }

    // For testing only
    public int counter() {
        return counter;
    }

    public void countDown() {
        if (UNSAFE.getAndAddInt(this, COUNTER_OFFSET, -1) == 1) {
            // Do not reference the underlying function anymore so it can be collected.
            function = null;
        }
    }
}
