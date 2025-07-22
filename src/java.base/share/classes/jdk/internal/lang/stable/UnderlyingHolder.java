package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

/**
 * This class is not thread safe. However, fields are only accessed within a block that
 * is guarded by the same synchronization object. Still, it can be instantiated from
 * any thread with no synchronization. Hence, the constructor must safely publish fields.
 *
 * @param <U> the underlying type
 */
public final class UnderlyingHolder<U> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // This field can only transition at most once from being set to a
    // non-null reference to being `null`. Once `null`, it is never read.
    private U underlying;
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
        return counter;
    }

    public void countDown() {
        if (--counter == 0) {
            // Do not reference the underlying function anymore so it can be collected.
            underlying = null;
        }
    }

}
