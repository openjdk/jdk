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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Note: It would be possible to just use `LazyMap::get` with some additional logic
// instead of this class but explicitly providing a class like this provides better
// debug capability, exception handling, and may provide better performance.
public record CachedFunction<T, R>(Map<T, StableValueImpl<R>> values,
                                   Function<? super T, ? extends R> original) implements Function<T, R> {
    @ForceInline
    @Override
    public R apply(T value) {
        final StableValueImpl<R> stable = values.get(value);
        if (stable == null) {
            throw new IllegalArgumentException("Input not allowed: " + value);
        }
        R r = stable.value();
        if (r != null) {
            return StableValueUtil.unwrap(r);
        }
        synchronized (stable) {
            r = stable.value();
            if (r != null) {
                return StableValueUtil.unwrap(r);
            }
            r = original.apply(value);
            stable.setOrThrow(r);
        }
        return r;
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
        return "CachedFunction[values=" + renderValues() + ", original=" + original + "]";
    }

    private String renderValues() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (var e:values.entrySet()) {
            final Object value = e.getValue().value();
            if (value == this) {
                sb.append("(self)");
            } else {
                sb.append(e.getKey()).append('=').append(StableValueUtil.render(value));
            }
        }
        sb.append("}");
        return sb.toString();
    }

    public static <T, R> CachedFunction<T, R> of(Set<T> inputs,
                                                 Function<? super T, ? extends R> original) {
        return new CachedFunction<>(StableValueUtil.ofMap(inputs), original);
    }

}
