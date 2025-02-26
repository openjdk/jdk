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

package compiler.lib.template_library.types;

import java.util.List;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;

import compiler.lib.template_library.Operation;

public final class IntType extends PrimitiveType {
    public static final IntType INSTANCE = new IntType();
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();

    private static final List<Operation> OPERATIONS = List.of(
        new Operation.Unary("((int)", ByteType.INSTANCE, ")"),
        new Operation.Unary("((int)", CharType.INSTANCE, ")"),
        new Operation.Unary("((int)", ShortType.INSTANCE, ")"),
        new Operation.Unary("((int)", LongType.INSTANCE, ")"),
        new Operation.Unary("((int)", FloatType.INSTANCE, ")"),
        new Operation.Unary("((int)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary("(-(", IntType.INSTANCE, "))"),
        new Operation.Unary("(~", IntType.INSTANCE, ")"),

        new Operation.Binary("(", IntType.INSTANCE, " + ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " - ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " * ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " / ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " % ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " & ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " | ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " ^ ",   IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " << ",  IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " >> ",  IntType.INSTANCE, ")"),
        new Operation.Binary("(", IntType.INSTANCE, " >>> ", IntType.INSTANCE, ")"),

        new Operation.Binary("Byte.compare(", ByteType.INSTANCE, ", ", ByteType.INSTANCE, ")"),
        new Operation.Binary("Byte.compareUnsigned(", ByteType.INSTANCE, ", ", ByteType.INSTANCE, ")"),
        new Operation.Unary("Byte.toUnsignedInt(", ByteType.INSTANCE, ")"),

        new Operation.Binary("Character.compare(", CharType.INSTANCE, ", ", CharType.INSTANCE, ")"),

        new Operation.Binary("Short.compare(", ShortType.INSTANCE, ", ", ShortType.INSTANCE, ")"),
        new Operation.Binary("Short.compareUnsigned(", ShortType.INSTANCE, ", ", ShortType.INSTANCE, ")"),
        new Operation.Unary("Short.toUnsignedInt(", ShortType.INSTANCE, ")"),

        new Operation.Ternary("(", BooleanType.INSTANCE, " ? ", IntType.INSTANCE, " : ", IntType.INSTANCE, ")")
    );

    @Override
    public final String name() { return "int"; }

    @Override
    public final Object con() {
        return GEN_INT.next();
    }

    @Override
    public final List<Operation> operations() {
        return OPERATIONS;
    }
}
