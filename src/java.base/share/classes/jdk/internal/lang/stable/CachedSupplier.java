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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementation of a cached supplier.
 * <p>
 * For performance reasons (~10%), we are not delegating to a StableValue but are using
 * the more primitive functions in StableValueUtil that are shared with StableValueImpl.
 *
 * @param <T> the return type
 */
public final class CachedSupplier<T> implements Supplier<T> {

    private static final long VALUE_OFFSET =
            StableValueUtil.UNSAFE.objectFieldOffset(CachedSupplier.class, "value");

    private final Supplier<? extends T> original;
    private final Object mutex = new Object();
    @Stable
    private T value;

    public CachedSupplier(Supplier<? extends T> original) {
        this.original = original;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T get() {
        T t = value;
        if (value != null) {
            return StableValueUtil.unwrap(t);
        }
        synchronized (mutex) {
            t = value;
            if (t != null) {
                return StableValueUtil.unwrap(t);
            }
            t = original.get();
            StableValueUtil.safelyPublish(this, VALUE_OFFSET, t);
        }
        return t;
    }

    public static <T> CachedSupplier<T> of(Supplier<? extends T> original) {
        return new CachedSupplier<>(original);
    }

    @Override
    public String toString() {
        return "CachedSupplier[value=" + StableValueUtil.render(StableValueUtil.UNSAFE.getReferenceVolatile(this, VALUE_OFFSET)) + ", original=" + original + "]";
    }

}
