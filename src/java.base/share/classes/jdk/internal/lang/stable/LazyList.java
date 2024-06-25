package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.IntFunction;

// Todo:: Move this class to ImmutableCollections
@ValueBased
public final class LazyList<E>
        extends AbstractList<E>
        implements RandomAccess {

    @Stable
    private final IntFunction<? extends E> mapper;
    @Stable
    private final List<StableValueImpl<E>> backing;

    private LazyList(int size, IntFunction<? extends E> mapper) {
        this.mapper = mapper;
        backing = StableValueImpl.ofList(size);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @ForceInline
    @Override
    public E get(int i) {
        final StableValueImpl<E> stable = backing.get(i);
        E e = stable.value();
        if (e != null) {
            return StableValueImpl.unwrap(e);
        }
        synchronized (stable) {
            e = stable.value();
            if (e != null) {
                return StableValueImpl.unwrap(e);
            }
            e = mapper.apply(i);
            stable.setOrThrow(e);
        }
        return e;
    }

    public static <E> List<E> of(int size, IntFunction<? extends E> mapper) {
        return new LazyList<>(size, mapper);
    }

}
