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

public final class ByteType extends PrimitiveType {
    public static final ByteType INSTANCE = new ByteType();
    private static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);

    private static final List<Operation> OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to byte, e.g:
        //           byte a = (byte)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to byte, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary("((byte)", IntType.INSTANCE, ")"),
        new Operation.Unary("((byte)", LongType.INSTANCE, ")"),
        new Operation.Unary("((byte)", FloatType.INSTANCE, ")"),
        new Operation.Unary("((byte)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Ternary("(", BooleanType.INSTANCE, " ? ", ByteType.INSTANCE, " : ", ByteType.INSTANCE, ")")
    );

    @Override
    public final String name() { return "byte"; }

    @Override
    public final Object con() {
        return "(byte)" + GEN_BYTE.next();
    }

    @Override
    public final List<Operation> operations() {
        return OPERATIONS;
    }
}
