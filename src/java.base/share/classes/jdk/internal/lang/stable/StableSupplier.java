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
 * Implementation of a stable supplier.
 * <p>
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> the return type
 */
public record StableSupplier<T>(StableValueImpl<T> delegate,
                                Supplier<? extends T> original) implements Supplier<T> {

    @ForceInline
    @Override
    public T get() {
        return delegate.orElseSet(original);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public String toString() {
        final Object t = delegate.wrappedContentsAcquire();
        return t == this ? "(this StableSupplier)" : StableValueImpl.renderWrapped(t);
    }

    public static <T> StableSupplier<T> of(Supplier<? extends T> original) {
        return new StableSupplier<>(StableValueImpl.of(), original);
    }

}
