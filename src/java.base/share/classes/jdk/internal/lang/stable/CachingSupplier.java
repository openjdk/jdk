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

package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.function.Supplier;

/**
 * Implementation of a cached supplier.
 * <p>
 * For performance reasons (~10%), we are not delegating to a StableValue but are using
 * the more primitive functions in StableValueUtil that are shared with StableValueImpl.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> the return type
 */
final class CachingSupplier<T> implements Supplier<T> {

    private static final long VALUE_OFFSET =
            StableValueUtil.UNSAFE.objectFieldOffset(CachingSupplier.class, "wrappedValue");

    @Stable
    private final Supplier<? extends T> original;
    @Stable
    private final Object mutex = new Object();
    @Stable
    private volatile Object wrappedValue;

    private CachingSupplier(Supplier<? extends T> original) {
        this.original = original;
    }

    @ForceInline
    @Override
    public T get() {
        Object t = wrappedValue;
        if (t != null) {
            return StableValueUtil.unwrap(t);
        }
        synchronized (mutex) {
            t = wrappedValue;
            if (t != null) {
                return StableValueUtil.unwrap(t);
            }
            final T newValue = original.get();
            StableValueUtil.wrapAndCas(this, VALUE_OFFSET, newValue);
            return newValue;
        }
    }

    @Override
    public String toString() {
        final Object t = wrappedValue;
        return "CachingSupplier[value=" + (t == this ? "(this CachingSupplier)" : StableValueUtil.renderWrapped(t)) + ", original=" + original + "]";
    }

    static <T> CachingSupplier<T> of(Supplier<? extends T> original) {
        return new CachingSupplier<>(original);
    }

}
