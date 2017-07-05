/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerfectHashBuilder<E> {
    private static final int RETRY_LIMIT = 1000;

    private Class<?> entryComponent;
    private Class<?> bucketComponent;

    private final Map<UTF8String, Entry<E>> map = new LinkedHashMap<>();
    private int[] redirect;
    private Entry<E>[] order;
    private int count = 0;

    @SuppressWarnings("EqualsAndHashcode")
    public static class Entry<E> {
        private final UTF8String key;
        private final E value;

        Entry() {
            this("", null);
        }

        Entry(String key, E value) {
            this(new UTF8String(key), value);
        }

        Entry(UTF8String key, E value) {
            this.key = key;
            this.value = value;
        }

        UTF8String getKey() {
            return key;
        }

        E getValue() {
            return value;
        }

        int hashCode(int seed) {
            return key.hashCode(seed);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    static class Bucket<E> implements Comparable<Bucket<E>> {
        final List<Entry<E>> list = new ArrayList<>();

        void add(Entry<E> entry) {
            list.add(entry);
        }

        int getSize() {
            return list.size();
        }

        List<Entry<E>> getList() {
            return list;
        }

        Entry<E> getFirst() {
            assert !list.isEmpty() : "bucket should never be empty";
            return list.get(0);
        }

        @Override
        public int hashCode() {
            return getFirst().hashCode();
        }

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int compareTo(Bucket<E> o) {
            return o.getSize() - getSize();
        }
    }

    public PerfectHashBuilder(Class<?> entryComponent, Class<?> bucketComponent) {
        this.entryComponent = entryComponent;
        this.bucketComponent = bucketComponent;
    }

    public int getCount() {
        return map.size();
    }

    public int[] getRedirect() {
        return redirect;
    }

    public Entry<E>[] getOrder() {
        return order;
    }

    public Entry<E> put(String key, E value) {
        return put(new UTF8String(key), value);
    }

    public Entry<E> put(UTF8String key, E value) {
        return put(new Entry<>(key, value));
    }

    public Entry<E> put(Entry<E> entry) {
        Entry<E> old = map.put(entry.key, entry);

        if (old == null) {
            count++;
        }

        return old;
    }

    @SuppressWarnings("unchecked")
    public void generate() {
        boolean redo = count != 0;
        while (redo) {
            redo = false;
            redirect = new int[count];
            order = (Entry<E>[])Array.newInstance(entryComponent, count);

            Bucket<E>[] sorted = createBuckets();
            int free = 0;

            for (Bucket<E> bucket : sorted) {
                if (bucket.getSize() != 1) {
                    if (!collidedEntries(bucket, count)) {
                        redo = true;
                        break;
                    }
                } else {
                    for ( ; free < count && order[free] != null; free++) {}

                    if (free >= count) {
                        redo = true;
                        break;
                    }

                    order[free] = bucket.getFirst();
                    redirect[bucket.hashCode() % count] = -1 - free;
                    free++;
                }
            }

            if (redo) {
                count = (count + 1) | 1;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Bucket<E>[] createBuckets() {
        Bucket<E>[] buckets = (Bucket<E>[])Array.newInstance(bucketComponent, count);

        map.values().stream().forEach((entry) -> {
            int index = entry.hashCode() % count;
            Bucket<E> bucket = buckets[index];

            if (bucket == null) {
                buckets[index] = bucket = new Bucket<>();
            }

            bucket.add(entry);
        });

        Bucket<E>[] sorted = Arrays.asList(buckets).stream()
                .filter((bucket) -> (bucket != null))
                .sorted()
                .toArray((length) -> {
                    return (Bucket<E>[])Array.newInstance(bucketComponent, length);
                });

        return sorted;
    }

    private boolean collidedEntries(Bucket<E> bucket, int count) {
        List<Integer> undo = new ArrayList<>();
        int seed = UTF8String.HASH_MULTIPLIER + 1;
        int retry = 0;

        redo:
        while (true) {
            for (Entry<E> entry : bucket.getList()) {
                int index = entry.hashCode(seed) % count;
                if (order[index] != null) {
                    if (++retry > RETRY_LIMIT) {
                        return false;
                    }

                    undo.stream().forEach((i) -> {
                        order[i] = null;
                    });

                    undo.clear();
                    seed++;

                    if (seed == 0) {
                        seed = 1;
                    }

                    continue redo;
                }

                order[index] = entry;
                undo.add(index);
            }

            redirect[bucket.hashCode() % count] = seed;

            break;
        }

        return true;
    }
 }
