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

import jdk.internal.vm.annotation.ForceInline;

import java.util.function.Supplier;

/**
 * The implementation of a supplied {@linkplain StableValue}.
 * <p>
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> the type of the contents
 */
// Todo: Consider implement this directly and not via an underlying SV
public record SuppliedStableValue<T>(UnSuppliedStableValue<T> delegate,
                                     FunctionHolder<Supplier<? extends T>> mapperHolder) implements InternalStableValue<T> {

    @ForceInline
    @Override public T       get() { return delegate.orElseSet(null, mapperHolder); }
    @Override public boolean isSet() { return delegate.isSet(); }
    @Override public T       orElse(T other) { return delegate.orElse(other); }

    @Override public boolean trySet(T contents) { throw uoe(); }
    @Override public T       orElseSet(int input, FunctionHolder<?> functionHolder) { throw uoe(); }
    @Override public T       orElseSet(Object key, FunctionHolder<?> functionHolder) { throw uoe(); }
    @Override public T       orElseSet(Supplier<? extends T> supplier) { throw uoe(); }
    @Override public Object  contentsPlain() { throw uoe(); }
    @Override public Object  contentsAcquire() { throw uoe(); }
    @Override public boolean set(T newValue) { throw uoe(); }


    // Object methods
    @Override public int     hashCode() { return System.identityHashCode(this); }
    @Override public boolean equals(Object obj) { return obj == this; }
    @Override public String  toString() {
                   final Object t = delegate.contentsAcquire();
                   return t == this ? "(this ComputedConstant)" : UnSuppliedStableValue.render(t);
              }


    public static <T> SuppliedStableValue<T> of(Supplier<? extends T> original) {
        return new SuppliedStableValue<>(UnSuppliedStableValue.of(), new FunctionHolder<>(original, 1));
    }

    private static UnsupportedOperationException uoe() {
        return new UnsupportedOperationException("A supplied StableValue does not support this operation");
    }

}
