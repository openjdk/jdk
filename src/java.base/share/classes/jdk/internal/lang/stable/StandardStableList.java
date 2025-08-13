package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.concurrent.atomic.StableValue;
import java.util.function.Supplier;

@ValueBased
public class StandardStableList<E>
        extends AbstractList<StableValue<E>>
        implements List<StableValue<E>>, RandomAccess {

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @Stable
    private final Object[] elements;
    private final Object[] mutexes;

    private StandardStableList(int length) {
        this.elements = new Object[length];
        this.mutexes = new Object[length];
    }

    @ForceInline
    @Override
    public StableValue<E> get(int index) {
        Objects.checkIndex(index, elements.length);
        return new ElementStableValue<>(elements, mutexes, index);
    }

    @Override
    public int size() {
        return elements.length;
    }

    // Todo: Views

    public record ElementStableValue<T>(@Stable Object[] elements,
                                        Object[] mutexes,
                                        int index)
            implements StableValue<T> {

        private static final Object TOMB_STONE = new Object();

        @ForceInline
        @Override
        public boolean trySet(T contents) {
            Objects.requireNonNull(contents);
            if (contentsAcquire() != null) {
                return false;
            }
            // Prevent reentry via an orElseSet(supplier)
            preventReentry();
            // Mutual exclusion is required here as `orElseSet` might also
            // attempt to modify `this.contents`
            final Object mutex = acquireMutex();
            if (mutex == TOMB_STONE) {
                return false;
            }
            synchronized (mutex) {
                final boolean outcome = set(contents);
                disposeOfMutex();
                return outcome;
            }
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElse(T other) {
            final Object t = contentsAcquire();
            return (t == null) ? other : (T) t;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElseThrow() {
            final Object t = contentsAcquire();
            if (t == null) {
                throw new NoSuchElementException("No contents set");
            }
            return (T) t;
        }

        @ForceInline
        @Override
        public boolean isSet() {
            return contentsAcquire() != null;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElseSet(Supplier<? extends T> supplier) {
            Objects.requireNonNull(supplier);
            final Object t = contentsAcquire();
            return (t == null) ? orElseSetSlowPath(supplier) : (T) t;
        }

        @SuppressWarnings("unchecked")
        private T orElseSetSlowPath(Supplier<? extends T> supplier) {
            preventReentry();
            synchronized (this) {
                final Object t = elements[index];  // Plain semantics suffice here
                if (t == null) {
                    final T newValue = supplier.get();
                    // The mutex is not reentrant so we know newValue should be returned
                    set(newValue);
                    return newValue;
                }
                return (T) t;
            }
        }

        @ForceInline
        private Object contentsAcquire() {
            return UNSAFE.getReferenceAcquire(elements, offsetFor(index));
        }

        @ForceInline
        private Object acquireMutex() {
            final Object candidate = new Object();
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offsetFor(index), null, candidate);
            return witness == null ? candidate : witness;
        }

        @ForceInline
        private void disposeOfMutex() {
            UNSAFE.putReferenceVolatile(mutexes, offsetFor(index), TOMB_STONE);
        }

        @ForceInline
        private Object mutexVolatile() {
            // Can be plain semantics
            return UNSAFE.getReferenceVolatile(mutexes, offsetFor(index));
        }

        // This method is not annotated with @ForceInline as it is always called
        // in a slow path.
        private void preventReentry() {
            if (Thread.holdsLock(mutexVolatile())) {
                throw new IllegalStateException("Recursive initialization of a stable value is illegal: " + index);
            }
        }

        /**
         * Tries to set the contents at the provided {@code index} to {@code newValue}.
         * <p>
         * This method ensures the {@link Stable} element is written to at most once.
         *
         * @param newValue to set
         * @return if the contents was set
         */
        @ForceInline
        private boolean set(T newValue) {
            assert Thread.holdsLock(mutexVolatile());
            // We know we hold the monitor here so plain semantic is enough
            if (elements[index] == null) {
                UNSAFE.putReferenceRelease(elements, offsetFor(index), newValue);
                return true;
            }
            return false;
        }

        @ForceInline
        private long offsetFor(long index) {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
        }

    }

    public static <T> List<StableValue<T>> ofList(int size) {
        return new StandardStableList<>(size);
    }

}
