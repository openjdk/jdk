/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.util.ArraysSupport;

/**
 * Provides a reversed-ordered view of a SortedSet. Not serializable.
 */
class ReverseOrderSortedSetView<E> implements SortedSet<E> {
    final SortedSet<E> base;
    final Comparator<? super E> comp;

    private ReverseOrderSortedSetView(SortedSet<E> set) {
        base = set;
        comp = Collections.reverseOrder(set.comparator());
    }

    public static <T> SortedSet<T> of(SortedSet<T> set) {
        if (set instanceof ReverseOrderSortedSetView<T> rossv) {
            return rossv.base;
        } else {
            return new ReverseOrderSortedSetView<>(set);
        }
    }

    // ========== Object ==========

    // copied from AbstractSet
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Set))
            return false;
        Collection<?> c = (Collection<?>) o;
        if (c.size() != size())
            return false;
        try {
            return containsAll(c);
        } catch (ClassCastException | NullPointerException unused) {
            return false;
        }
    }

    // copied from AbstractSet
    public int hashCode() {
        int h = 0;
        Iterator<E> i = iterator();
        while (i.hasNext()) {
            E obj = i.next();
            if (obj != null)
                h += obj.hashCode();
        }
        return h;
    }

    // copied from AbstractCollection
    public String toString() {
        Iterator<E> it = iterator();
        if (! it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            E e = it.next();
            sb.append(e == this ? "(this Collection)" : e);
            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }

    // ========== Iterable ==========

    public void forEach(Consumer<? super E> action) {
        for (E e : this)
            action.accept(e);
    }

    public Iterator<E> iterator() {
        return descendingIterator(base);
    }

    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.ORDERED);
    }

    // ========== Collection ==========

    public boolean add(E e) {
        base.add(e);
        return true;
    }

    public boolean addAll(Collection<? extends E> c) {
        return base.addAll(c);
    }

    public void clear() {
        base.clear();
    }

    public boolean contains(Object o) {
        return base.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return base.containsAll(c);
    }

    public boolean isEmpty() {
        return base.isEmpty();
    }

    public Stream<E> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    public boolean remove(Object o) {
        return base.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        return base.removeAll(c);
    }

    // copied from AbstractCollection
    public boolean retainAll(Collection<?> c) {
        return base.retainAll(c);
    }

    public int size() {
        return base.size();
    }

    public Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Object[] toArray() {
        return ArraysSupport.reverse(base.toArray());
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        return ArraysSupport.toArrayReversed(base, a);
    }

    public <T> T[] toArray(IntFunction<T[]> generator) {
        return ArraysSupport.reverse(base.toArray(generator));
    }

    // ========== SortedSet ==========

    public Comparator<? super E> comparator() {
        return comp;
    }

    public E first() { return base.last(); }

    public E last() { return base.first(); }

    public SortedSet<E> headSet(E to) {
        return new Subset(null, to);
    }

    public SortedSet<E> subSet(E from, E to) {
        return new Subset(from, to);
    }

    public SortedSet<E> tailSet(E from) {
        return new Subset(from, null);
    }

    // ========== Infrastructure ==========

    static <T> Iterator<T> descendingIterator(SortedSet<T> set) {
        return new Iterator<>() {
            SortedSet<T> root = set;
            SortedSet<T> view = set;
            T prev = null;

            public boolean hasNext() {
                return ! view.isEmpty();
            }

            public T next() {
                if (view.isEmpty())
                    throw new NoSuchElementException();
                T t = prev = view.last();
                view = root.headSet(t);
                return t;
            }

            public void remove() {
                if (prev == null) {
                    throw new IllegalStateException();
                } else {
                    root.remove(prev);
                    prev = null;
                }
            }
        };
    }

    /**
     * Used for various subset views. We can't use the base SortedSet's subset,
     * because of the asymmetry between from-inclusive and to-exclusive.
     */
    class Subset extends AbstractSet<E> implements SortedSet<E> {
        final E head; // head element, or negative infinity if null
        final E tail; // tail element, or positive infinity if null
        final Comparator<E> cmp;

        @SuppressWarnings("unchecked")
        Subset(E head, E tail) {
            this.head = head;
            this.tail = tail;
            Comparator<E> c = (Comparator<E>) ReverseOrderSortedSetView.this.comparator();
            if (c == null)
                c = (Comparator<E>) Comparator.naturalOrder();
            cmp = c;
        }

        // returns whether e is above the head, inclusive
        boolean aboveHead(E e) {
            return head == null || cmp.compare(e, head) >= 0;
        }

        // returns whether e is below the tail, exclusive
        boolean belowTail(E e) {
            return tail == null || cmp.compare(e, tail) < 0;
        }

        public Iterator<E> iterator() {
            return new Iterator<>() {
                E cache = null;
                boolean dead = false;
                Iterator<E> it = descendingIterator(base);

                public boolean hasNext() {
                    if (dead)
                        return false;

                    if (cache != null)
                        return true;

                    while (it.hasNext()) {
                        E e = it.next();

                        if (! aboveHead(e))
                            continue;

                        if (! belowTail(e)) {
                            dead = true;
                            return false;
                        }

                        cache = e;
                        return true;
                    }

                    return false;
                }

                public E next() {
                    if (hasNext()) {
                        E e = cache;
                        cache = null;
                        return e;
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }

        public boolean add(E e) {
            if (aboveHead(e) && belowTail(e))
                return base.add(e);
            else
                throw new IllegalArgumentException();
        }

        public boolean remove(Object o) {
            @SuppressWarnings("unchecked")
            E e = (E) o;
            if (aboveHead(e) && belowTail(e))
                return base.remove(o);
            else
                return false;
        }

        public int size() {
            int sz = 0;
            for (E e : this)
                sz++;
            return sz;
        }

        public Comparator<? super E> comparator() {
            return ReverseOrderSortedSetView.this.comparator();
        }

        public E first() {
            return this.iterator().next();
        }

        public E last() {
            var it = this.iterator();
            if (! it.hasNext())
                throw new NoSuchElementException();
            E last = it.next();
            while (it.hasNext())
                last = it.next();
            return last;
        }

        public SortedSet<E> subSet(E from, E to) {
            if (aboveHead(from) && belowTail(from) &&
                aboveHead(to) && belowTail(to) &&
                cmp.compare(from, to) <= 0) {
                return ReverseOrderSortedSetView.this.new Subset(from, to);
            } else {
                throw new IllegalArgumentException();
            }
        }

        public SortedSet<E> headSet(E to) {
            if (aboveHead(to) && belowTail(to))
                return ReverseOrderSortedSetView.this.new Subset(head, to);
            else
                throw new IllegalArgumentException();
        }

        public SortedSet<E> tailSet(E from) {
            if (aboveHead(from) && belowTail(from))
                return ReverseOrderSortedSetView.this.new Subset(null, tail);
            else
                throw new IllegalArgumentException();
        }

    }
}
