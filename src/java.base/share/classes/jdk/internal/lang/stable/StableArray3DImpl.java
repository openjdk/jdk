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

import jdk.internal.lang.StableArray3D;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;

public record StableArray3DImpl<V>(
        @Stable int dim0,
        @Stable int dim1,
        @Stable int dim2,
        @Stable StableValueImpl<V>[] elements
) implements StableArray3D<V> {

    private StableArray3DImpl(int dim0, int dim1, int dim2) {
        this(dim0, dim1, dim2, StableUtil.newStableValueArray(Math.multiplyExact(Math.multiplyExact(dim0, dim1), dim2)));
    }

    @ForceInline
    @Override
    public StableValue<V> get(int i0, int i1, int i2) {
        Objects.checkIndex(i0, dim0);
        Objects.checkIndex(i1, dim1);
        Objects.checkIndex(i2, dim2);
        final int index = i0 * dim1 * dim2 + i1 * dim2 + i2;
        StableValueImpl<V> stable = elements[index];
        return stable == null
                ? StableUtil.getOrSetVolatile(elements, index)
                : stable;
    }

    @Override
    public int length(int dimension) {
        return switch (dimension) {
            case  0 -> dim0;
            case  1 -> dim1;
            case  2 -> dim2;
            default -> throw new IllegalArgumentException();
        };
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
        if (dim0 == 0 || dim1 == 0 || dim2 == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        final int dim0 = length(0);
        final int dim1 = length(1);
        final int dim2 = length(2);
        for (int i0 = 0; i0 < dim0; i0++) {
            if (i0 != 0) {
                sb.append(',').append(' ');
            }
            sb.append('[');
            for (int i1 = 0; i1 < dim1; i1++) {
                if (i1 != 0) {
                    sb.append(',').append(' ');
                }
                sb.append('[');
                for (int i2 = 0; i2 < dim2; i2++) {
                    if (i2 != 0) {
                        sb.append(',').append(' ');
                    }
                    final StableValue<V> stable = get(i0, i1, i2);
                    if (stable.isSet()) {
                        final V v = stable.orThrow();
                        sb.append(v == this ? "(this StableArray)" : stable);
                    } else {
                        sb.append(stable);
                    }
                }
                sb.append(']');
            }
            sb.append(']');
        }
        sb.append(']');
        return sb.toString();
    }

    public static <V> StableArray3D<V> of(int dim0, int dim1, int dim2) {
        return new StableArray3DImpl<>(dim0, dim1, dim2);
    }

}
