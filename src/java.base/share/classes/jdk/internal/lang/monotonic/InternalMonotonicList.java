/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.monotonic;

import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static jdk.internal.lang.monotonic.MonotonicUtil.*;

public final class InternalMonotonicList<B>
        extends AbstractList<Monotonic<B>>
        implements Monotonic.List<B> {

    @Stable
    private final Monotonic<B>[] elements;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public InternalMonotonicList(int size) {
        this.elements = (Monotonic<B>[]) new Monotonic[size];
    }

    @Override
    public Monotonic<B> get(int index) {
        Objects.checkIndex(index, size());
        Monotonic<B> m = elements[index];
        if (m != null) {
            return m;
        }
        m = elementVolatile(index);
        if (m != null) {
            return m;
        }
        Monotonic<B> created = Monotonic.of();
        m = caeElement(index, created);
        return m == null ? created : m;
    }

    @Override
    public int size() {
        return elements.length;
    }

    // all mutating methods throw UnsupportedOperationException
    @Override
    public boolean add(Monotonic<B> v) {
        throw uoe();
    }

    @Override
    public boolean addAll(int index, Collection<? extends Monotonic<B>> c) {
        throw uoe();
    }

    @Override
    public void clear() {
        throw uoe();
    }

    @Override
    public boolean remove(Object o) {
        throw uoe();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw uoe();
    }

    @Override
    public boolean removeIf(Predicate<? super Monotonic<B>> filter) {
        throw uoe();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw uoe();
    }

    @Override
    public B computeMonotonicIfAbsent(int index, IntFunction<? extends B> mapper) {
        Objects.checkIndex(index, size());
        Objects.requireNonNull(mapper);
        Monotonic<B> monotonic = get(index);
        if (monotonic.isPresent()) {
            return monotonic.get();
        }
        synchronized (mapper) {
            if (monotonic.isPresent()) {
                return monotonic.get();
            }
            Supplier<B> supplier = () -> mapper.apply(index);
            return monotonic.computeIfAbsent(supplier);
        }
    }

    @SuppressWarnings("unchecked")
    private Monotonic<B> elementVolatile(int index) {
        return (Monotonic<B>) UNSAFE.getReferenceVolatile(elements, MonotonicUtil.objectOffset(index));
    }

    @SuppressWarnings("unchecked")
    private Monotonic<B> caeElement(int index, Monotonic<B> created) {
        // Make sure no reordering of store operations
        freeze();
        return (Monotonic<B>) UNSAFE.compareAndExchangeReference(elements, MonotonicUtil.objectOffset(index), null, created);
    }

    public static <B> Monotonic.List<B> of(int size) {
        return new InternalMonotonicList<>(size);
    }

}
