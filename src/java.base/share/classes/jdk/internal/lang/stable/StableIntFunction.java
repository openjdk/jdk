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
import jdk.internal.vm.annotation.Stable;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Implementation of a stable IntFunction.
 * <p>
 * For performance reasons (~10%), we are not delegating to a StableList but are using
 * the more primitive functions in StableValueUtil that are shared with StableList/StableValueImpl.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <R> the return type
 */
public record StableIntFunction<R>(@Stable StableValueImpl<R>[] delegates,
                                   IntFunction<? extends R> original) implements IntFunction<R> {

    @ForceInline
    @Override
    public R apply(int index) {
        final StableValueImpl<R> delegate;
        try {
            delegate =  delegates[index];
        } catch (ArrayIndexOutOfBoundsException ioob) {
            throw new IllegalArgumentException("Input not allowed: " + index, ioob);
        }
        return delegate.orElseSet(new Supplier<R>() {
                    @Override public R get() { return original.apply(index); }});
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
        return StableUtil.renderElements(this, "StableIntFunction", delegates);
    }

    public static <R> StableIntFunction<R> of(int size, IntFunction<? extends R> original) {
        return new StableIntFunction<>(StableUtil.array(size), original);
    }

}
