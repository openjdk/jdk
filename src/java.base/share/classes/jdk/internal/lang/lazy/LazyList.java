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

package jdk.internal.lang.lazy;

import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static jdk.internal.lang.lazy.LazyUtil.*;

public final class LazyList<V>
        extends AbstractList<Lazy<V>> // AbstractImmutableList ... -> @ValueBased
        implements List<Lazy<V>> {

    @Stable
    private int size;
    @Stable
    private final V[] elements;
    @Stable
    private final byte[] sets;
    private final Object[] mutexes;

    @SuppressWarnings("unchecked")
    public LazyList(int size) {
        this.elements = (V[]) new Object[size];
        this.size = size;
        this.sets = new byte[size];
        this.mutexes = new Object[size];
    }

    @Override
    public Lazy<V> get(int index) {
        Objects.checkIndex(index, size);
        return new LazyListElement<>(elements, sets, mutexes, index);
        //return LazyListElement.lazyListElement(elements, sets, mutexes, index);
    }

    @Override
    public int size() {
        return size;
    }

    // all mutating methods throw UnsupportedOperationException
    @Override public boolean add(Lazy<V> v) {throw uoe();}
    @Override public boolean addAll(Collection<? extends Lazy<V>> c) {throw uoe();}
    @Override public boolean addAll(int index, Collection<? extends Lazy<V>> c) {throw uoe();}
    @Override public void    clear() {throw uoe();}
    @Override public boolean remove(Object o) {throw uoe();}
    @Override public boolean removeAll(Collection<?> c) {throw uoe();}
    @Override public boolean removeIf(Predicate<? super Lazy<V>> filter) {throw uoe();}
    @Override public boolean retainAll(Collection<?> c) {throw uoe();}
    @Override public void    sort(Comparator<? super Lazy<V>> c) {throw uoe();}

    @Override
    public int indexOf(Object o) {
        Objects.requireNonNull(o);
        return super.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        Objects.requireNonNull(o);
        return super.lastIndexOf(o);
    }

    @Override
    public List<Lazy<V>> reversed() {
        // Todo: Fix this
        throw new UnsupportedOperationException();
        //return ReverseOrderListView.of(this, true); // we must assume it's modifiable
    }

    // Factories

    public static <V> List<Lazy<V>> of(int size) {
        return new LazyList<>(size);
    }

    public static <V> V computeIfUnbound(List<Lazy<V>> list,
                                         int index,
                                         IntFunction<? extends V> mapper) {
        Lazy<V> lazy = list.get(index);
        if (lazy.isSet()) {
            return lazy.orThrow();
        }
        V newValue = mapper.apply(index);
        return lazy.setIfUnset(newValue);
    }


}
