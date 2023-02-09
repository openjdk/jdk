package jdk.internal.util;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * An array of object references in which elements may be updated
 * just once (lazily) and atomically. This contrasts to {@link AtomicReferenceArray } where
 * any number of updates can be done and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if it is missing.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows a component is updated just once
 * as described by {@link Stable}.
 *
 * @param <E> The type of elements held in this array
 */
public final class LazyReferenceArray<E> {

    private static final VarHandle ARRAY_VH = MethodHandles.arrayElementVarHandle(Object[].class);

    @Stable
    private final Object[] array;

    /**
     * Creates a new AtomicReferenceArray of the given length, with all
     * elements initially null.
     *
     * @param length the length of the array
     */
    private LazyReferenceArray(int length) {
        this.array = new Object[length];
    }

    /**
     * {@return the length of the array}.
     */
    public int length() {
        return array.length;
    }

    /**
     * {@return the element at index {@code i} or {@code null} if
     * no such element exists }
     *
     * @param index the element index
     * @throws IndexOutOfBoundsException if {@code index < 0 or index >= length() }
     */
    @SuppressWarnings("unchecked")
    public E getOrNull(int index) {
        return getAcquire(index);
    }

    /**
     * {@return the element at index {@code i} or, if no such element exists, throws
     * a NoSuchElementException}.
     *
     * @param index the element index
     * @throws NoSuchElementException    if no such element exists at the provided {@code index}
     * @throws IndexOutOfBoundsException if {@code index < 0 or index >= length() }
     */
    public E getOrThrow(int index) {
        E value = getOrNull(index);
        if (value == null) {
            throw new NoSuchElementException("No element at " + index);
        }
        return value;
    }

    /**
     * If the specified index is not already associated with a value, atomically attempts
     * to compute its value using the given mapping function and enters it into this
     * array.
     *
     * <p>If the mapping function returns {@code null}, an exception is thrown.
     * If the mapping function itself throws an (unchecked) exception, the
     * exception is rethrown, and no mapping is recorded.  The most
     * common usage is to construct a new object serving as an initial
     * mapped value or memoized result, as in:
     *
     * <pre> {@code
     * array.computeIfAbsent(index, i -> new Value(f(i)));
     * }</pre>
     *
     * @param index to use as array index
     * @param mappingFunction to apply if no previous value exists
     * @return the element at the provided index (pre-existing or newly computed)
     * @throws IndexOutOfBoundsException if {@code index < 0 or index >= length() }.
     * @throws NullPointerException      if the provided {@code mappingFunction} is {@code null} or
     *                                   the provided {@code mappingFunction} returns {@code null}.
     */
    public E computeIfAbsent(int index,
                             IntFunction<? extends E> mappingFunction) {
        Objects.checkIndex(index, array.length);
        Objects.requireNonNull(mappingFunction);

        E value = getAcquire(index);
        if (value == null) {
            synchronized (array) {
                value = getAcquire(index);
                if (value == null) {
                    value = Objects.requireNonNull(
                            mappingFunction.apply(index));
                    setRelease(index, value);
                }
            }
        }
        return value;
    }

    // Use acquire/release to ensure happens-before so that newly
    // constructed elements are always observed correctly
    private E getAcquire(int index) {
        return (E) ARRAY_VH.getAcquire(array, index);
    }

    void setRelease(int slot, Object value) {
        ARRAY_VH.setRelease(array, slot, value);
    }

    /**
     * {@return a new AtomicReferenceArray of the given length, with all
     * elements initially null}.
     *
     * @param length the length of the array
     * @throws IllegalArgumentException if the provided {@code length} is negative.
     */
    public static <E> LazyReferenceArray<E> create(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        return new LazyReferenceArray<>(length);
    }

}
