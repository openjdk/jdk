/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_library;

import java.util.List;

import compiler.lib.template_framework.Name;

/**
 * The {@link Type} abstract class defines the basic functionalities that any {@link Type}
 * must provide, such as generating random expressions.
 *
 * TODO
 */
public abstract class Type implements Name.Type {

    public static final Type bytes() { return PrimitiveType.ByteType.INSTANCE; }
    public static final Type chars() { return PrimitiveType.CharType.INSTANCE; }
    public static final Type shorts() { return PrimitiveType.ShortType.INSTANCE; }
    public static final Type ints() { return PrimitiveType.IntType.INSTANCE; }
    public static final Type longs() { return PrimitiveType.LongType.INSTANCE; }
    public static final Type floats() { return PrimitiveType.FloatType.INSTANCE; }
    public static final Type doubles() { return PrimitiveType.DoubleType.INSTANCE; }
    public static final Type booleans() { return PrimitiveType.BooleanType.INSTANCE; }

    public static final List<Type> primitives() {
        return List.of(
            bytes(),
            chars(),
            shorts(),
            ints(),
            longs(),
            floats(),
            doubles(),
            booleans()
        );
    }

    /**
     * Returns name of the type that can be used in Java code.
     *
     * @return The name of the type that can be used in Java code.
     */
    public abstract String name();

    @Override
    public final String toString() { return name(); }

    public abstract Object con();
}
