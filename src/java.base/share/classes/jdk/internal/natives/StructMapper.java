package jdk.internal.natives;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public interface StructMapper<T> {

    /**
     * {@return a new instance of T that is backed by the provided {@code segment}}
     * <p>
     * The method is free to return {@link jdk.internal.ValueBased} instances.
     *
     * @param segment to use as a backing store
     */
    T of(MemorySegment segment);

    /**
     * {@return a new instance of T that is backed by a segment allocated from
     * the provided {@code arena}}
     * <p>
     * The method is free to return {@link jdk.internal.ValueBased} instances.
     *
     * @param arena from which to allocate segments to use as a backing store
     * @see #of(MemorySegment)
     */
    T of(Arena arena);

    /**
     * {@return a new List of T elements that is backed by the provided {@code segment}}
     * <p>
     * The method is free to return a list with {@link jdk.internal.ValueBased} element instances.
     *
     * @param segment to use as a backing store
     */
     List<T> ofElements(int size, MemorySegment segment);


    /**
     * {@return a new List of T elements that is backed by the provided {@code segment}}
     * <p>
     * The method is free to return a list with {@link jdk.internal.ValueBased} element instances.
     *
     * @param segment to use as a backing store
     */
    List<T> ofElements(MemorySegment segment);


    /**
     * {@return a new struct mapper}
     *
     * @param layout for which mapping shall occur
     * @param constructor to use when creating objects
     * @param <T> type to map
     */
     static <T> StructMapper<T> of(MemoryLayout layout,
                                   Function<? super MemorySegment, ? extends T> constructor) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(constructor);
        return new StructUtil.StructMapperImpl<>(layout, constructor);
    }


}
