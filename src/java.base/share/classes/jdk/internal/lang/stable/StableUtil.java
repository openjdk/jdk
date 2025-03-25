package jdk.internal.lang.stable;

import java.util.Collection;
import java.util.Map;

public final class StableUtil {

    private StableUtil() {}

    public static <R> String renderElements(Object self,
                                            String selfName,
                                            StableValueImpl<R>[] delegates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (int i = 0; i < delegates.length; i++) {
            if (first) { first = false; } else { sb.append(", "); }
            final Object value = delegates[i].wrappedContentAcquire();
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
                                               Iterable<Map.Entry<K, StableValueImpl<V>>> delegates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
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
        sb.append("}");
        return sb.toString();
    }


}
