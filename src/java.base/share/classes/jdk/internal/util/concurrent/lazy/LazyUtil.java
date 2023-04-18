package jdk.internal.util.concurrent.lazy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public final class LazyUtil {

    // Object that flags the Lazy is being constucted. Any object that is not a Throwable can be used.
    public static final Object CONSTRUCTING = LazyUtil.class;

    private LazyUtil() {
    }

    static <V> V supplyIfEmpty(AbstractBaseLazyReference<V> lazy,
                               Supplier<? extends V> supplier) {
        // implies volatile semantics when entering/leaving the monitor
        synchronized (lazy) {
            // Here, visibility is guaranteed
            V v = lazy.value;
            if (v != null) {
                return v;
            }
            if (lazy.auxilaryObject instanceof Throwable throwable) {
                throw new NoSuchElementException(throwable);
            }
            if (supplier == null) {
                throw new IllegalStateException("No pre-set supplier given");
            }
            try {
                lazy.auxilaryObject = CONSTRUCTING;
                v = supplier.get();
                if (v == null) {
                    throw new NullPointerException("Supplier returned null");
                }
                AbstractBaseLazyReference.VALUE_HANDLE.setVolatile(lazy, v);
                return v;
            } catch (Throwable e) {
                // Record the throwable instead of the value.
                AbstractBaseLazyReference.AUX_HANDLE.setVolatile(lazy, e);
                // Rethrow
                throw e;
            } finally {
                lazy.afterSupplying();
            }
        }
    }
}
