/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package sun.net.httpclient.hpack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.lang.String.format;

//
// Header Table combined from two tables: static and dynamic.
//
// There is a single address space for index values. Index-aware methods
// correspond to the table as a whole. Size-aware methods only to the dynamic
// part of it.
//
final class HeaderTable {

    private static final HeaderField[] staticTable = {
            null, // To make index 1-based, instead of 0-based
            new HeaderField(":authority"),
            new HeaderField(":method", "GET"),
            new HeaderField(":method", "POST"),
            new HeaderField(":path", "/"),
            new HeaderField(":path", "/index.html"),
            new HeaderField(":scheme", "http"),
            new HeaderField(":scheme", "https"),
            new HeaderField(":status", "200"),
            new HeaderField(":status", "204"),
            new HeaderField(":status", "206"),
            new HeaderField(":status", "304"),
            new HeaderField(":status", "400"),
            new HeaderField(":status", "404"),
            new HeaderField(":status", "500"),
            new HeaderField("accept-charset"),
            new HeaderField("accept-encoding", "gzip, deflate"),
            new HeaderField("accept-language"),
            new HeaderField("accept-ranges"),
            new HeaderField("accept"),
            new HeaderField("access-control-allow-origin"),
            new HeaderField("age"),
            new HeaderField("allow"),
            new HeaderField("authorization"),
            new HeaderField("cache-control"),
            new HeaderField("content-disposition"),
            new HeaderField("content-encoding"),
            new HeaderField("content-language"),
            new HeaderField("content-length"),
            new HeaderField("content-location"),
            new HeaderField("content-range"),
            new HeaderField("content-type"),
            new HeaderField("cookie"),
            new HeaderField("date"),
            new HeaderField("etag"),
            new HeaderField("expect"),
            new HeaderField("expires"),
            new HeaderField("from"),
            new HeaderField("host"),
            new HeaderField("if-match"),
            new HeaderField("if-modified-since"),
            new HeaderField("if-none-match"),
            new HeaderField("if-range"),
            new HeaderField("if-unmodified-since"),
            new HeaderField("last-modified"),
            new HeaderField("link"),
            new HeaderField("location"),
            new HeaderField("max-forwards"),
            new HeaderField("proxy-authenticate"),
            new HeaderField("proxy-authorization"),
            new HeaderField("range"),
            new HeaderField("referer"),
            new HeaderField("refresh"),
            new HeaderField("retry-after"),
            new HeaderField("server"),
            new HeaderField("set-cookie"),
            new HeaderField("strict-transport-security"),
            new HeaderField("transfer-encoding"),
            new HeaderField("user-agent"),
            new HeaderField("vary"),
            new HeaderField("via"),
            new HeaderField("www-authenticate")
    };

    private static final int STATIC_TABLE_LENGTH = staticTable.length - 1;
    private static final int ENTRY_SIZE = 32;
    private static final Map<String, LinkedHashMap<String, Integer>> staticIndexes;

    static {
        staticIndexes = new HashMap<>(STATIC_TABLE_LENGTH);
        for (int i = 1; i <= STATIC_TABLE_LENGTH; i++) {
            HeaderField f = staticTable[i];
            Map<String, Integer> values = staticIndexes
                    .computeIfAbsent(f.name, k -> new LinkedHashMap<>());
            values.put(f.value, i);
        }
    }

    private final Table dynamicTable = new Table(0);
    private int maxSize;
    private int size;

    public HeaderTable(int maxSize) {
        setMaxSize(maxSize);
    }

    //
    // The method returns:
    //
    // * a positive integer i where i (i = [1..Integer.MAX_VALUE]) is an
    // index of an entry with a header (n, v), where n.equals(name) &&
    // v.equals(value)
    //
    // * a negative integer j where j (j = [-Integer.MAX_VALUE..-1]) is an
    // index of an entry with a header (n, v), where n.equals(name)
    //
    // * 0 if there's no entry e such that e.getName().equals(name)
    //
    // The rationale behind this design is to allow to pack more useful data
    // into a single invocation, facilitating a single pass where possible
    // (the idea is the same as in java.util.Arrays.binarySearch(int[], int)).
    //
    public int indexOf(CharSequence name, CharSequence value) {
        // Invoking toString() will possibly allocate Strings for the sake of
        // the search, which doesn't feel right.
        String n = name.toString();
        String v = value.toString();

        // 1. Try exact match in the static region
        Map<String, Integer> values = staticIndexes.get(n);
        if (values != null) {
            Integer idx = values.get(v);
            if (idx != null) {
                return idx;
            }
        }
        // 2. Try exact match in the dynamic region
        int didx = dynamicTable.indexOf(n, v);
        if (didx > 0) {
            return STATIC_TABLE_LENGTH + didx;
        } else if (didx < 0) {
            if (values != null) {
                // 3. Return name match from the static region
                return -values.values().iterator().next(); // Iterator allocation
            } else {
                // 4. Return name match from the dynamic region
                return -STATIC_TABLE_LENGTH + didx;
            }
        } else {
            if (values != null) {
                // 3. Return name match from the static region
                return -values.values().iterator().next(); // Iterator allocation
            } else {
                return 0;
            }
        }
    }

    public int size() {
        return size;
    }

    public int maxSize() {
        return maxSize;
    }

    public int length() {
        return STATIC_TABLE_LENGTH + dynamicTable.size();
    }

    HeaderField get(int index) {
        checkIndex(index);
        if (index <= STATIC_TABLE_LENGTH) {
            return staticTable[index];
        } else {
            return dynamicTable.get(index - STATIC_TABLE_LENGTH);
        }
    }

    void put(CharSequence name, CharSequence value) {
        // Invoking toString() will possibly allocate Strings. But that's
        // unavoidable at this stage. If a CharSequence is going to be stored in
        // the table, it must not be mutable (e.g. for the sake of hashing).
        put(new HeaderField(name.toString(), value.toString()));
    }

    private void put(HeaderField h) {
        int entrySize = sizeOf(h);
        while (entrySize > maxSize - size && size != 0) {
            evictEntry();
        }
        if (entrySize > maxSize - size) {
            return;
        }
        size += entrySize;
        dynamicTable.add(h);
    }

    void setMaxSize(int maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException
                    ("maxSize >= 0: maxSize=" + maxSize);
        }
        while (maxSize < size && size != 0) {
            evictEntry();
        }
        this.maxSize = maxSize;
        int upperBound = (maxSize / ENTRY_SIZE) + 1;
        this.dynamicTable.setCapacity(upperBound);
    }

    HeaderField evictEntry() {
        HeaderField f = dynamicTable.remove();
        size -= sizeOf(f);
        return f;
    }

    @Override
    public String toString() {
        double used = maxSize == 0 ? 0 : 100 * (((double) size) / maxSize);
        return format("entries: %d; used %s/%s (%.1f%%)", dynamicTable.size(),
                size, maxSize, used);
    }

    int checkIndex(int index) {
        if (index < 1 || index > STATIC_TABLE_LENGTH + dynamicTable.size()) {
            throw new IllegalArgumentException(
                    format("1 <= index <= length(): index=%s, length()=%s",
                            index, length()));
        }
        return index;
    }

    int sizeOf(HeaderField f) {
        return f.name.length() + f.value.length() + ENTRY_SIZE;
    }

    //
    // Diagnostic information in the form used in the RFC 7541
    //
    String getStateString() {
        if (size == 0) {
            return "empty.";
        }

        StringBuilder b = new StringBuilder();
        for (int i = 1, size = dynamicTable.size(); i <= size; i++) {
            HeaderField e = dynamicTable.get(i);
            b.append(format("[%3d] (s = %3d) %s: %s%n", i,
                    sizeOf(e), e.name, e.value));
        }
        b.append(format("      Table size:%4s", this.size));
        return b.toString();
    }

    // Convert to a Value Object (JDK-8046159)?
    static final class HeaderField {

        final String name;
        final String value;

        public HeaderField(String name) {
            this(name, "");
        }

        public HeaderField(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return value.isEmpty() ? name : name + ": " + value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HeaderField that = (HeaderField) o;
            return name.equals(that.name) && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return 31 * (name.hashCode()) + value.hashCode();
        }
    }

    //
    // In order to be able to find an index of an entry with the given contents
    // in the dynamic table an effective inverse mapping is needed. Here's a
    // simple idea behind such a mapping.
    //
    // # The problem:
    //
    // We have a queue with an O(1) lookup by index:
    //
    //     get: index -> x
    //
    // What we also want is an O(1) reverse lookup:
    //
    //     indexOf: x -> index
    //
    // # Solution:
    //
    // Let's store an inverse mapping as a Map<X, Integer>. This have a problem
    // that when a new element is added to the queue all indexes in the map
    // becomes invalid. Namely, each i becomes shifted by 1 to the right:
    //
    //     i -> i + 1
    //
    // And the new element is assigned with an index of 1. This would seem to
    // require a pass through the map incrementing all indexes (map values) by
    // 1, which is O(n).
    //
    // The good news is we can do much better then this!
    //
    // Let's create a single field of type long, called 'counter'. Then each
    // time a new element 'x' is added to the queue, a value of this field gets
    // incremented. Then the resulting value of the 'counter_x' is then put as a
    // value under key 'x' to the map:
    //
    //    map.put(x, counter_x)
    //
    // It gives us a map that maps an element to a value the counter had at the
    // time the element had been added.
    //
    // In order to retrieve an index of any element 'x' in the queue (at any
    // given time) we simply need to subtract the value (the snapshot of the
    // counter at the time when the 'x' was added) from the current value of the
    // counter. This operation basically answers the question:
    //
    //     How many elements ago 'x' was the tail of the queue?
    //
    // Which is the same as its index in the queue now. Given, of course, it's
    // still in the queue.
    //
    // I'm pretty sure in a real life long overflow will never happen, so it's
    // not too practical to add recalibrating code, but a pedantic person might
    // want to do so:
    //
    //     if (counter == Long.MAX_VALUE) {
    //         recalibrate();
    //     }
    //
    // Where 'recalibrate()' goes through the table doing this:
    //
    //  value -= counter
    //
    // That's given, of course, the size of the table itself is less than
    // Long.MAX_VALUE :-)
    //
    private static final class Table {

        private final Map<String, Map<String, Long>> map;
        private final CircularBuffer<HeaderField> buffer;
        private long counter = 1;

        Table(int capacity) {
            buffer = new CircularBuffer<>(capacity);
            map = new HashMap<>(capacity);
        }

        void add(HeaderField f) {
            buffer.add(f);
            Map<String, Long> values = map.computeIfAbsent(f.name, k -> new HashMap<>());
            values.put(f.value, counter++);
        }

        HeaderField get(int index) {
            return buffer.get(index - 1);
        }

        int indexOf(String name, String value) {
            Map<String, Long> values = map.get(name);
            if (values == null) {
                return 0;
            }
            Long index = values.get(value);
            if (index != null) {
                return (int) (counter - index);
            } else {
                assert !values.isEmpty();
                Long any = values.values().iterator().next(); // Iterator allocation
                return -(int) (counter - any);
            }
        }

        HeaderField remove() {
            HeaderField f = buffer.remove();
            Map<String, Long> values = map.get(f.name);
            Long index = values.remove(f.value);
            assert index != null;
            if (values.isEmpty()) {
                map.remove(f.name);
            }
            return f;
        }

        int size() {
            return buffer.size;
        }

        public void setCapacity(int capacity) {
            buffer.resize(capacity);
        }
    }

    //                    head
    //                    v
    // [ ][ ][A][B][C][D][ ][ ][ ]
    //        ^
    //        tail
    //
    //       |<- size ->| (4)
    // |<------ capacity ------->| (9)
    //
    static final class CircularBuffer<E> {

        int tail, head, size, capacity;
        Object[] elements;

        CircularBuffer(int capacity) {
            this.capacity = capacity;
            elements = new Object[capacity];
        }

        void add(E elem) {
            if (size == capacity) {
                throw new IllegalStateException(
                        format("No room for '%s': capacity=%s", elem, capacity));
            }
            elements[head] = elem;
            head = (head + 1) % capacity;
            size++;
        }

        @SuppressWarnings("unchecked")
        E remove() {
            if (size == 0) {
                throw new NoSuchElementException("Empty");
            }
            E elem = (E) elements[tail];
            elements[tail] = null;
            tail = (tail + 1) % capacity;
            size--;
            return elem;
        }

        @SuppressWarnings("unchecked")
        E get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(
                        format("0 <= index <= capacity: index=%s, capacity=%s",
                                index, capacity));
            }
            int idx = (tail + (size - index - 1)) % capacity;
            return (E) elements[idx];
        }

        public void resize(int newCapacity) {
            if (newCapacity < size) {
                throw new IllegalStateException(
                        format("newCapacity >= size: newCapacity=%s, size=%s",
                                newCapacity, size));
            }

            Object[] newElements = new Object[newCapacity];

            if (tail < head || size == 0) {
                System.arraycopy(elements, tail, newElements, 0, size);
            } else {
                System.arraycopy(elements, tail, newElements, 0, elements.length - tail);
                System.arraycopy(elements, 0, newElements, elements.length - tail, head);
            }

            elements = newElements;
            tail = 0;
            head = size;
            this.capacity = newCapacity;
        }
    }
}
