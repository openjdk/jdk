/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.arrays;

import jdk.nashorn.internal.objects.Global;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import jdk.nashorn.internal.runtime.PropertyDescriptor;

/**
 * ArrayData after the array has been frozen by Object.freeze call.
 */
final class FrozenArrayFilter extends SealedArrayFilter {
    FrozenArrayFilter(final ArrayData underlying) {
        super(underlying);
    }

    @Override
    public ArrayData copy() {
        return this;
    }

    @Override
    public PropertyDescriptor getDescriptor(final Global global, final int index) {
        return global.newDataDescriptor(getObject(index), false, true, false);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        if (strict) {
            throw typeError("cant.set.property", Integer.toString(index), "frozen array");
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        if (strict) {
            throw typeError("cant.set.property", Integer.toString(index), "frozen array");
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (strict) {
            throw typeError("cant.set.property", Integer.toString(index), "frozen array");
        }
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (strict) {
            throw typeError("cant.set.property", Integer.toString(index), "frozen array");
        }
        return this;
    }
}
