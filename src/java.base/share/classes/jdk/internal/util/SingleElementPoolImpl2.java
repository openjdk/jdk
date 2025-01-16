package jdk.internal.util;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class SingleElementPoolImpl2<T> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final long LOCKED_OFFSET =
            UNSAFE.objectFieldOffset(SingleElementPoolImpl2.class, "locked");
    private static final long CLOSED_OFFSET =
            UNSAFE.objectFieldOffset(SingleElementPoolImpl2.class, "closed");

    @Stable
    private final Supplier<? extends T> factory;
    @Stable
    private final Consumer<? super T> recycler;
    @Stable
    private final T pooledElement;
    // Using an int lock is faster than CASing a reference field
    private int locked;
    private int closed;

    public SingleElementPoolImpl2(T pooledElement,
                                 Supplier<? extends T> factory,
                                 Consumer<? super T> recycler) {
        this.factory = factory;
        this.recycler = recycler;
        this.pooledElement = pooledElement;
    }

    // Used reflectively
    @ForceInline
    public T take() {
        return lock() ? pooledElement : factory.get();
    }

    // Used reflectively
    @ForceInline
    public void release(T element) {
        if (element == pooledElement) {
            unlock();
        } else {
            recycler.accept(element);
        }
    }

    // This method is called by a separate cleanup thread when the associated
    // platform thread is dead.
    public void close() {
        if (!UNSAFE.compareAndSetInt(this, CLOSED_OFFSET, 0, 1)) {
            recycler.accept(pooledElement);
        }
    }

    @ForceInline
    private boolean lock() {
        return UNSAFE.getAndSetInt(this, LOCKED_OFFSET, 1) == 0;
    }

    @ForceInline
    private void unlock() {
        UNSAFE.putIntVolatile(this, LOCKED_OFFSET, 0);
    }

}