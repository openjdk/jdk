package jdk.internal.foreign.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Function;

/**
 * A layout transformer that can be used to apply functions on selected types
 * of memory layouts.
 * <p>
 * A layout transformer can be used to convert byte ordering for all sub-members
 * or remove all names, for example.
 *
 * @param <T> the type of MemoryLayout for which transformation is to be made
 */
public sealed interface LayoutTransformer<T extends MemoryLayout>
        permits LayoutTransformerImpl {

    /**
     * {@return a transformed version of the provided {@code layout}}.
     * @param layout to transform
     */
    MemoryLayout transform(T layout);

    /**
     * {@return a transformed version of the provided {@code layout} by recursively
     * applying this transformer (breadth first) on all sub-members}
     * @param layout to transform
     */
    MemoryLayout deepTransform(MemoryLayout layout);

    /**
     * {@return a layout transformer that transforms layouts of the given {@code type}
     * using the provided {@code transformation}}
     * @param type to transform
     * @param transformation to apply
     * @param <T> the type of memory layout to transform
     */
    static <T extends MemoryLayout>
    LayoutTransformer<T> of(Class<T> type,
                            Function<? super T, ? extends MemoryLayout> transformation) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type);
        return LayoutTransformerImpl.of(type, transformation);
    }

    /**
     * {@return a transformer that will remove all member names in memory layouts}
     */
    static LayoutTransformer<MemoryLayout> stripNames() {
        return LayoutTransformerImpl.STRIP_NAMES;
    }

    /**
     * {@return a transformer that will set member byte ordering to the provided
     * {@code byteOrder}}
     */
    static LayoutTransformer<ValueLayout> setByteOrder(ByteOrder byteOrder) {
        return LayoutTransformerImpl.setByteOrder(byteOrder);
    }

}
