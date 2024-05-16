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

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;

public record StableArrayImpl<V>(
        @Stable StableValueImpl<V>[] elements
) implements StableArray<V> {

    private StableArrayImpl(int length) {
        this(StableUtil.newStableValueArray(length));
    }

    @ForceInline
    @Override
    public StableValue<V> get(int firstIndex) {
        Objects.checkIndex(firstIndex, elements.length);
        StableValueImpl<V> stable = elements[firstIndex];
        return stable == null
                ? StableUtil.getOrSetVolatile(elements, firstIndex)
                : stable;
    }

    @Override
    public int length() {
        return elements.length;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        if (length() == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < length(); i++) {
            if (i != 0) {
                sb.append(',').append(' ');
            }
            final StableValue<V> stable = get(i);
            if (stable.isSet()) {
                V v = stable.orThrow();
                sb.append(v == this ? "(this StableArray)" : stable);
            } else {
                sb.append(stable);
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static <V> StableArray<V> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
