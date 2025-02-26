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

public final class ShortType extends PrimitiveType {
    public static final ShortType INSTANCE = new ShortType();
    private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);

    private static final List<Operation> OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to short, e.g:
        //           short a = (short)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to short, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary("((short)", ByteType.INSTANCE, ")"),
        new Operation.Unary("((short)", CharType.INSTANCE, ")"),
        new Operation.Unary("((short)", IntType.INSTANCE, ")"),
        new Operation.Unary("((short)", LongType.INSTANCE, ")"),
        new Operation.Unary("((short)", FloatType.INSTANCE, ")"),
        new Operation.Unary("((short)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary("Short.reverseBytes(", ShortType.INSTANCE, ")"),

        new Operation.Unary("Float.floatToFloat16(", FloatType.INSTANCE, ")"),

        new Operation.Ternary("(", BooleanType.INSTANCE, " ? ", ShortType.INSTANCE, " : ", ShortType.INSTANCE, ")")
    );

    @Override
    public final String name() { return "short"; }

    @Override
    public final Object con() {
        return "(short)" + GEN_SHORT.next();
    }

    @Override
    public final List<Operation> operations() {
        return OPERATIONS;
    }
}
