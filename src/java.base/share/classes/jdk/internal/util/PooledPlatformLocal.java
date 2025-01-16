package jdk.internal.util;

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PooledPlatformLocal<T> {

    @Stable
    private final TerminatingThreadLocal<SingleElementPool<T>> pools = new TerminatingThreadLocal<>() {
        @Override
        protected void threadTerminated(SingleElementPool<T> value) {
            value.close();
        }
    };

    @Stable
    private final Supplier<SingleElementPool<T>> poolSupplier;

    public PooledPlatformLocal(Supplier<T> factory, Consumer<T> recycler) {
        this(new Supplier<>() {
            @Override
            public SingleElementPool<T> get() {
                return SingleElementPool.of(factory, recycler);
            }
        });
    }

    public PooledPlatformLocal(Supplier<SingleElementPool<T>> poolSupplier) {
        this.poolSupplier = Objects.requireNonNull(poolSupplier);
    }

    /**
     * Obtains the thread local value associated with the current thread and perform an
     * action on it.
     *
     * @param action the action to perform on the platform local value.
     */
    @ForceInline
    public void accept(Consumer<? super T> action) {
        final SingleElementPool<T> pool = acquirePlatformLocalPool();
        final T t = pool.take();
        try {
            action.accept(t);
        } finally {
            pool.release(t);
        }
    }

    @ForceInline
    public <R> R map(Function<? super T, ? extends R> mapper) {
        final SingleElementPool<T> pool = acquirePlatformLocalPool();
        final T t = pool.take();
        try {
            return mapper.apply(t);
        } finally {
            pool.release(t);
        }
    }

    @ForceInline
    private SingleElementPool<T> acquirePlatformLocalPool() {
        SingleElementPool<T> pool = pools.get();
        // Todo: Replace with StableValue
        if (pool == null) {
            synchronized (this) {
                pool = pools.get();
                if (pool == null) {
                    pools.set(pool = poolSupplier.get());
                }
            }
        }
        return pool;
    }

}
