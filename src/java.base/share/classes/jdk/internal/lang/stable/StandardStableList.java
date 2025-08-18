/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.invoke.StableValue;
import java.util.function.Supplier;

@ValueBased
public class StandardStableList<E>
        extends AbstractList<StableValue<E>>
        implements List<StableValue<E>>, RandomAccess {

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final Object TOMB_STONE = new Object();

    @Stable
    private final Object[] elements;
    @Stable
    private final Mutexes mutexes;

    private StandardStableList(int length) {
        this.elements = new Object[length];
        super();
        this.mutexes = new Mutexes(length);
    }

    @ForceInline
    @Override
    public ElementStableValue<E> get(int index) {
        Objects.checkIndex(index, elements.length);
        return new ElementStableValue<>(elements, this, offsetFor(index));
    }

    @Override
    public int size() {
        return elements.length;
    }

    // Todo: Views
    public record ElementStableValue<T>(@Stable Object[] elements, // Fast track this one
                                        StandardStableList<T> list,
                                        long offset)  implements StableValue<T> {

        @ForceInline
        @Override
        public boolean trySet(T contents) {
            Objects.requireNonNull(contents);
            if (contentsAcquire() != null) {
                return false;
            }
            // Prevent reentry via orElseSet(supplier)
            preventReentry();
            // Mutual exclusion is required here as `orElseSet` might also
            // attempt to modify `this.elements`
            final Object mutex = acquireMutex();
            if (mutex == TOMB_STONE) {
                return false;
            }
            synchronized (mutex) {
                final boolean outcome = set(contents);
                disposeOfMutex();
                return outcome;
            }
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElse(T other) {
            final Object t = contentsAcquire();
            return t == null ? other:(T)t;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T get() {
            final Object t = contentsAcquire();
            if (t == null) {
                throw new NoSuchElementException("No contents set");
            }
            return (T) t;
        }

        @ForceInline
        @Override
        public boolean isSet() {
            return contentsAcquire() != null;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElseSet(Supplier<? extends T> supplier) {
            Objects.requireNonNull(supplier);
            final Object t = contentsAcquire();
            return (t == null) ? orElseSetSlowPath(supplier) : (T) t;
        }

        @SuppressWarnings("unchecked")
        private T orElseSetSlowPath(Supplier<? extends T> supplier) {
            preventReentry();
            final Object mutex = acquireMutex();
            if (mutex == TOMB_STONE) {
                return (T) contentsAcquire();
            }
            synchronized (mutex) {
                final Object t = UNSAFE.getReference(elements, offset);  // Plain semantics suffice here
                if (t == null) {
                     final T newValue = supplier.get();
                    Objects.requireNonNull(newValue);
                    // The mutex is not reentrant so we know newValue should be returned
                    set(newValue);
                    return newValue;
                }
                return (T) t;
            }
        }

        // Object methods
        @Override public boolean equals(Object obj) { return this == obj; }
        @Override public int     hashCode() { return System.identityHashCode(this); }
        @Override public String toString() {
            final Object t = contentsAcquire();
            return t == this
                    ? "(this StableValue)"
                    : StandardStableValue.render(t);
        }

        @ForceInline
        private Object contentsAcquire() {
            return UNSAFE.getReferenceAcquire(elements, offset);
        }

        @ForceInline
        private Object acquireMutex() {
            return list.mutexes.acquireMutex(offset);
        }

        @ForceInline
        private void disposeOfMutex() {
            list.mutexes.disposeOfMutex(offset);
        }

        @ForceInline
        private Object mutexVolatile() {
            return list.mutexes.mutexVolatile(offset);
        }

        // This method is not annotated with @ForceInline as it is always called
        // in a slow path.
        private void preventReentry() {
            final Object mutex = mutexVolatile();
            if (mutex != null && Thread.holdsLock(mutexVolatile())) {
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + indexFor(offset));
            }
        }

        /**
         * Tries to set the contents at the provided {@code index} to {@code newValue}.
         * <p>
         * This method ensures the {@link Stable} element is written to at most once.
         *
         * @param newValue to set
         * @return if the contents was set
         */
        @ForceInline
        private boolean set(T newValue) {
            Object mutex;
            assert Thread.holdsLock(mutex = mutexVolatile()) : indexFor(offset) + "(@" + offset + ") didn't hold " + mutex;
            // We know we hold the monitor here so plain semantic is enough
            if (UNSAFE.getReference(elements, offset) == null) {
                UNSAFE.putReferenceRelease(elements, offset, newValue);
                return true;
            }
            return false;
        }

        private long indexFor(long offset) {
            return (offset - Unsafe.ARRAY_OBJECT_BASE_OFFSET) / Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }

    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    private static class Mutexes {

        // Inflated on demand
        private volatile Object[] mutexes;
        // Used to detect we have computed all elements and no longer need the `mutexes` array
        private volatile AtomicInteger counter;

        private Mutexes(int length) {
            this.mutexes = new Object[length];
            this.counter = new AtomicInteger(length);
        }

        @ForceInline
        private Object acquireMutex(long offset) {
            final Object candidate = new Object();
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offset, null, candidate);
            return witness == null ? candidate : witness;
        }

        @ForceInline
        private void disposeOfMutex(long offset) {
            UNSAFE.putReferenceVolatile(mutexes, offset, TOMB_STONE);
            // Todo: the null check is redundant as this method is invoked at most
            //       `size()` times.
            if (counter != null && counter.decrementAndGet() == 0) {
                // We don't need these anymore
                counter = null;
                mutexes = null;
            }
        }

        @ForceInline
        private Object mutexVolatile(long offset) {
            // Can be plain semantics?
            return UNSAFE.getReferenceVolatile(mutexes, offset);
        }

    }

    public static <T> List<StableValue<T>> ofList(int size) {
        return new StandardStableList<>(size);
    }

}
