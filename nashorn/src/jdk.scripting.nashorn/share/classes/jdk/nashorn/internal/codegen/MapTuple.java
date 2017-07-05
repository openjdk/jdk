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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.ObjectClassGenerator.OBJECT_FIELDS_ONLY;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.Symbol;

/**
 * A tuple of values used for map creation
 * @param <T> value type
 */
class MapTuple<T> {
    final String key;
    final Symbol symbol;
    final Type   type;
    final T      value;

    MapTuple(final String key, final Symbol symbol, final Type type) {
        this(key, symbol, type, null);
    }

    MapTuple(final String key, final Symbol symbol, final Type type, final T value) {
        this.key    = key;
        this.symbol = symbol;
        this.type   = type;
        this.value  = value;
    }

    public Class<?> getValueType() {
        return OBJECT_FIELDS_ONLY ? Object.class : null; //until proven otherwise we are undefined, see NASHORN-592 int.class;
    }

    boolean isPrimitive() {
        return !OBJECT_FIELDS_ONLY && getValueType().isPrimitive() && getValueType() != boolean.class;
    }

    @Override
    public String toString() {
        return "[key=" + key + ", symbol=" + symbol + ", value=" + value + " (" + (value == null ? "null" : value.getClass().getSimpleName()) +")]";
    }
}
