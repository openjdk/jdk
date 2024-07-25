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

import java.util.List;
import java.util.function.IntFunction;

// Note: It would be possible to just use `LazyList::get` instead of this
// class but explicitly providing a class like this provides better
// debug capability, exception handling, and may provide better performance.
public record CachedIntFunction<R>(List<StableValueImpl<R>> stables,
                                   IntFunction<? extends R> original) implements IntFunction<R> {
    @ForceInline
    @Override
    public R apply(int value) {
        final StableValueImpl<R> stable;
        try {
            // Todo: Will the exception handling here impair performance?
            stable = stables.get(value);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
        R r = stable.value();
        if (r != null) {
            return StableValueImpl.unwrap(r);
        }
        synchronized (stable) {
            r = stable.value();
            if (r != null) {
                return StableValueImpl.unwrap(r);
            }
            r = original.apply(value);
            stable.setOrThrow(r);
        }
        return r;
    }

    public static <R> CachedIntFunction<R> of(int size, IntFunction<? extends R> original) {
        return new CachedIntFunction<>(StableValueImpl.ofList(size), original);
    }

}
