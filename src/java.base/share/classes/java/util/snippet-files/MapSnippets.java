/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Map.entry;

/**
 * Snippets used in java.util.Map.
 */
final class MapSnippets {

    private MapSnippets() { }

    private static <K, V> void forEach(
            Map<K, V> map,
            BiConsumer<? super K, ? super V> action) {
        // @start region=forEach :
        for (Map.Entry<K, V> entry : map.entrySet())
            action.accept(entry.getKey(), entry.getValue());
        // @end
    }

    private static <K, V> void replaceAll(
            Map<K, V> map,
            BiFunction<? super K, ? super V, ? extends V> function) {
        // @start region=replaceAll :
        for (Map.Entry<K, V> entry : map.entrySet())
            entry.setValue(function.apply(entry.getKey(), entry.getValue()));
        // @end replaceAll
    }

    private static <K, V> V putIfAbsent(Map<K, V> map, K key, V value) {
        // @start region=putIfAbsent :
        V v = map.get(key);
        if (v == null)
            v = map.put(key, value);

        return v;
        // @end
    }

    private static <K, V> boolean remove(Map<K, V> map, K key, V value) {
        // @start region=remove :
        if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
            map.remove(key);
            return true;
        } else
            return false;
        // @end
    }

    private static <K, V> boolean replaceKVV(Map<K, V> map,
                                             K key,
                                             V oldValue,
                                             V newValue) {
        // @start region=replaceKVV :
        if (map.containsKey(key) && Objects.equals(map.get(key), oldValue)) {
            map.put(key, newValue);
            return true;
        } else
            return false;
        // @end
    }

    private static <K, V> V replaceKV(Map<K, V> map, K key, V value) {
        // @start region=replaceKV :
        if (map.containsKey(key)) {
            return map.put(key, value);
        } else
            return null;
        // @end
    }

    private static <K, V> V computeIfAbsent(
            Map<K, V> map,
            K key,
            Function<? super K, ? extends V> mappingFunction) {
        // @start region=computeIfAbsent :
        if (map.get(key) == null) {
            V newValue = mappingFunction.apply(key);
            if (newValue != null)
                map.put(key, newValue);
        }
        // @end
        return null; // FIXME
    }

    private static <K, V> V computeIfPresent(
            Map<K, V> map,
            K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        // @start region=computeIfPresent :
        if (map.get(key) != null) {
            V oldValue = map.get(key);
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null)
                map.put(key, newValue);
            else
                map.remove(key);
        }
        // @end
        return null; // FIXME
    }

    private static <K, V> V compute(
            Map<K, V> map,
            K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        // @start region=compute :
        V oldValue = map.get(key);
        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue != null) {
            map.put(key, newValue);
        } else if (oldValue != null || map.containsKey(key)) {
            map.remove(key);
        }
        return newValue;
        // @end
    }

    private static <K, V> V merge(
            Map<K, V> map,
            K key,
            V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        // @start region=merge :
        V oldValue = map.get(key);
        V newValue = (oldValue == null) ? value :
                remappingFunction.apply(oldValue, value);
        if (newValue == null)
            map.remove(key);
        else
            map.put(key, newValue);
        // @end
        return null;
    }

    // This is (almost) a JShell script
    private static void ofEntries() {// @start region=ofEntries :
        // // @replace substring="//" replacement="import static java.util.Map.entry;"

        Map<Integer, String> map = Map.ofEntries(
            entry(1, "a"),
            entry(2, "b"),
            entry(3, "c"),
            // // @replace substring="//" replacement="..."
            entry(26, "z"));// @end
    }

    private static boolean equals(Map.Entry<?, ?> e1, Map.Entry<?, ?> e2) {
        return // @start region=equals :
                   (e1.getKey()==null ?
                    e2.getKey()==null : e1.getKey().equals(e2.getKey()))  &&
                   (e1.getValue()==null ?
                    e2.getValue()==null : e1.getValue().equals(e2.getValue()))
               // @end
                ;
    }

    private static int hashCode(Map.Entry<?, ?> e) {
        return // @start region=hashCode :
                   (e.getKey()==null   ? 0 : e.getKey().hashCode()) ^
                   (e.getValue()==null ? 0 : e.getValue().hashCode())
               // @end
               ;
    }
}
