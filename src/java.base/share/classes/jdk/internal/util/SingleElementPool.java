package jdk.internal.util;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A high-performance single element pool where the pooled element is eagerly created
 * upfront.
 * <p>
 * Elements are created using a {@code factory} and released elements not in the pool are
 * disposed of via a {@code recycler}. The single element in a pool can be created either
 * by the {@linkplain SingleElementPool#of(Supplier, Consumer) factory} or
 * {@linkplain SingleElementPool#of(Object, Supplier, Consumer) explicitly}.
 *
 * @param <T> the pool element type
 */
public sealed interface SingleElementPool<T> extends AutoCloseable {

    /**
     * {@return the single pooled element if not taken, otherwise invokes the pool's
     *          factory to create a new element}
     */
    T take();

    /**
     * Releases the provided {@code element}. If the element is <em>identical</em> to the
     * single pooled element, returns it to the pool; otherwise invokes the pool's
     * recycler to dispose of the provided {@code element}.
     */
    void release(T element);

    /**
     * Recycles the single pooled element if it is released. This method is idempotent.
     */
    @Override
    void close();

    /**
     * {@return a new SingleElementPool with the single pooled element created via the
     *          provided {@code factory}}.
     * @param factory  used to create new elements
     * @param recycler used to dispose of elements
     * @param <T> the pool element type
     * @throws NullPointerException if either of {@code factory} or {@code recycler}
     *         is {@code null}
     */
    static <T> SingleElementPool<T> of(Supplier<? extends T> factory,
                                       Consumer<? super T> recycler) {
        return new SingleElementPoolImpl<>(factory.get(), factory, recycler);
    }

    /**
     * {@return a new SingleElementPool with the given single {@code pooledElement}}
     * @param factory  used to create new elements
     * @param recycler used to dispose of elements
     * @param <T> the pool element type
     * @throws NullPointerException if either of {@code factory} or {@code recycler}
     *         is {@code null}
     */
    static <T> SingleElementPool<T> of(T pooledElement,
                                       Supplier<? extends T> factory,
                                       Consumer<? super T> recycler) {
        return new SingleElementPoolImpl<>(pooledElement, factory, recycler);
    }

    // The class is not final to allow subclassing for low-level use.
    non-sealed class SingleElementPoolImpl<T> implements SingleElementPool<T> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private static final long POOLED_ELEMENT_TAKEN_OFFSET =
                UNSAFE.objectFieldOffset(SingleElementPoolImpl.class, "pooledElementTaken");
        private static final long CLOSED_OFFSET =
                UNSAFE.objectFieldOffset(SingleElementPoolImpl.class, "closed");

        private static final int FALSE = 0;
        private static final int TRUE = 1;

        @Stable
        private final Supplier<? extends T> factory;
        @Stable
        private final Consumer<? super T> recycler;
        @Stable
        private final T pooledElement;
        // Using an int lock is faster than CASing a reference field
        private int pooledElementTaken;
        private int closed;

        public SingleElementPoolImpl(T pooledElement,
                                     Supplier<? extends T> factory,
                                     Consumer<? super T> recycler) {
            this.factory = Objects.requireNonNull(factory);
            this.recycler = Objects.requireNonNull(recycler);
            this.pooledElement = pooledElement;
        }

        // Used reflectively
        @ForceInline
        public T take() {
            return takePooledElement() ? pooledElement : factory.get();
        }

        // Used reflectively
        @ForceInline
        public void release(T element) {
            if (element == pooledElement) {
                releasePooledElement();
            } else {
                recycler.accept(element);
            }
        }

        // This method is called by a separate cleanup thread when the associated
        // platform thread is dead.
        public void close() {
            if (UNSAFE.compareAndSetInt(this, CLOSED_OFFSET, FALSE, TRUE)) {
                if (UNSAFE.getIntVolatile(this, POOLED_ELEMENT_TAKEN_OFFSET) == FALSE) {
                    recycler.accept(pooledElement);
                }
            }
        }

        @ForceInline
        private boolean takePooledElement() {
            return UNSAFE.getAndSetInt(this, POOLED_ELEMENT_TAKEN_OFFSET, TRUE) == FALSE;
        }

        @ForceInline
        private void releasePooledElement() {
            UNSAFE.putIntVolatile(this, POOLED_ELEMENT_TAKEN_OFFSET, FALSE);
        }

    }

}
