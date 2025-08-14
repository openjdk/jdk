package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
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
        super();
    }

    @ForceInline
    @Override
    public ElementStableValue<E> get(int index) {
        Objects.checkIndex(index, elements.length);
        return new ElementStableValue<>(elements, mutexes, offsetFor(index));
    }

    @Override
    public int size() {
        return elements.length;
    }

    // Todo: Views
    // Todo: Consider having just a StandardStableList field and an offset
    public record ElementStableValue<T>(@Stable Object[] elements,
                                        Object[] mutexes,
                                        long offset)
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
        public Optional<T> toOptional() {
            return Optional.ofNullable((T) contentsAcquire());
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T get() {
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
            final Object mutex = acquireMutex();
            if (mutex == TOMB_STONE) {
                return (T) contentsAcquire();
            }
            synchronized (mutex) {
                final Object t = UNSAFE.getReference(elements, offset);  // Plain semantics suffice here
                if (t == null) {
                     final T newValue = supplier.get();
                    Objects.requireNonNull(newValue);
                    // The mutex is not reentrant so we know newValue should be returned
                    set(newValue);
                    return newValue;
                }
                return (T) t;
            }
        }

        // Object methods
        @Override public boolean equals(Object obj) { return this == obj; }
        @Override public int     hashCode() { return System.identityHashCode(this); }
        @Override public String toString() {
            final Object t = contentsAcquire();
            return t == this
                    ? "(this StableValue)"
                    : StandardStableValue.render(t);
        }

        @ForceInline
        private Object contentsAcquire() {
            return UNSAFE.getReferenceAcquire(elements, offset);
        }

        @ForceInline
        private Object acquireMutex() {
            final Object candidate = new Object();
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offset, null, candidate);
            return witness == null ? candidate : witness;
        }

        @ForceInline
        private void disposeOfMutex() {
            UNSAFE.putReferenceVolatile(mutexes, offset, TOMB_STONE);
        }

        @ForceInline
        private Object mutexVolatile() {
            // Can be plain semantics?
            return UNSAFE.getReferenceVolatile(mutexes, offset);
        }

        // This method is not annotated with @ForceInline as it is always called
        // in a slow path.
        private void preventReentry() {
            final Object mutex = mutexVolatile();
            if (mutex != null && Thread.holdsLock(mutexVolatile())) {
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + indexFor(offset));
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
            if (UNSAFE.getReference(elements, offset) == null) {
                UNSAFE.putReferenceRelease(elements, offset, newValue);
                return true;
            }
            return false;
        }

        private long indexFor(long offset) {
            return (offset - Unsafe.ARRAY_OBJECT_BASE_OFFSET) / Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }

    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    public static <T> List<StableValue<T>> ofList(int size) {
        return new StandardStableList<>(size);
    }

}
