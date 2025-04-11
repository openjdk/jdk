package jdk.internal.lang.stable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StableUtil {

    private StableUtil() {}

    public static <R> String renderElements(Object self,
                                            String selfName,
                                            StableValueImpl<R>[] delegates) {
        return renderElements(self, selfName, delegates, 0, delegates.length);
    }

    public static <R> String renderElements(Object self,
                                            String selfName,
                                            StableValueImpl<R>[] delegates,
                                            int offset,
                                            int length) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (int i = 0; i < length; i++) {
            if (first) { first = false; } else { sb.append(", "); }
            final Object value = delegates[i + offset].wrappedContentAcquire();
            if (value == self) {
                sb.append("(this ").append(selfName).append(")");
            } else {
                sb.append(StableValueImpl.renderWrapped(value));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static <K, V> String renderMappings(Object self,
                                               String selfName,
                                               Iterable<Map.Entry<K, StableValueImpl<V>>> delegates,
                                               boolean curly) {
        final StringBuilder sb = new StringBuilder();
        sb.append(curly ? "{" : "[");
        boolean first = true;
        for (var e : delegates) {
            if (first) { first = false; } else { sb.append(", "); }
            final Object value = e.getValue().wrappedContentAcquire();
            sb.append(e.getKey()).append('=');
            if (value == self) {
                sb.append("(this ").append(selfName).append(")");
            } else {
                sb.append(StableValueImpl.renderWrapped(value));
            }
        }
        sb.append(curly ? "}" : "]");
        return sb.toString();
    }


    public static <T> StableValueImpl<T>[] array(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        @SuppressWarnings("unchecked")
        final var stableValues = (StableValueImpl<T>[]) new StableValueImpl<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValueImpl.of();
        }
        return stableValues;
    }

    public static <K, T> Map<K, StableValueImpl<T>> map(Set<K> keys) {
        Objects.requireNonNull(keys);
        @SuppressWarnings("unchecked")
        final var entries = (Map.Entry<K, StableValueImpl<T>>[]) new Map.Entry<?, ?>[keys.size()];
        int i = 0;
        for (K key : keys) {
            entries[i++] = Map.entry(key, StableValueImpl.of());
        }
        return Map.ofEntries(entries);
    }
}
