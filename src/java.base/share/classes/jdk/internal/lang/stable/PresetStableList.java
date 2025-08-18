package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.lang.invoke.StableValue;

@ValueBased
public class PresetStableList<E>
        extends AbstractList<StableValue<E>>
        implements List<StableValue<E>>, RandomAccess {

    @Stable
    private final E[] elements;

    private PresetStableList(E[] elements) {
        this.elements = elements;
        super();
    }

    @ForceInline
    @Override
    public StableValue<E> get(int index) {
        final E element = elements[index];
        return new PresetStableValue<>(element);
    }

    @Override
    public int size() {
        return elements.length;
    }

    // Todo: Views

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<StableValue<T>> ofList(T... elements) {
        return new PresetStableList<>(elements);
    }

}
