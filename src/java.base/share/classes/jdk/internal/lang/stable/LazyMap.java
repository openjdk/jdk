package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

// Todo:: Move this class to ImmutableCollections
@ValueBased
public final class LazyMap<K, V>
        extends AbstractMap<K, V> {

    @Stable
    private final Function<? super K, ? extends V> mapper;
    @Stable
    private final Map<K, StableValueImpl<V>> backing;

    private LazyMap(Set<K> keys, Function<? super K, ? extends V> mapper) {
        this.mapper = mapper;
        backing = StableValueImpl.ofMap(keys);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return Set.of();
    }

    @Override
    public V get(Object key) {
        final StableValueImpl<V> stable = backing.get(key);
        if (stable == null) {
            return null;
        }
        V v = stable.value();
        if (v != null) {
            return StableValueImpl.unwrap(v);
        }
        synchronized (stable) {
            v = stable.value();
            if (v != null) {
                return StableValueImpl.unwrap(v);
            }
            @SuppressWarnings("unchecked")
            K k = (K) key;
            v = mapper.apply(k);
            stable.setOrThrow(v);
        }
        return v;
    }

    @ValueBased
    private final class EntrySet extends AbstractSet<Entry<K, V>> {
        @Stable
        private final Set<Entry<K, StableValueImpl<V>>> backingEntrySet;

        public EntrySet() {
            this.backingEntrySet = backing.entrySet();
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return null;
        }

        @Override
        public int size() {
            return backingEntrySet.size();
        }

        private final class LazyIterator implements It{

        }

    }


    public static <K, V> Map<K, V> of(Set<K> keys, Function<? super K, ? extends V> mapper) {
        return new LazyMap<>(keys, mapper);
    }

}
