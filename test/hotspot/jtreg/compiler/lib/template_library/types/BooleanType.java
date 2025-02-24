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

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_library.Operation;

public final class BooleanType extends PrimitiveType {
    public static final BooleanType INSTANCE = new BooleanType();
    private static final Random RANDOM = Utils.getRandomInstance();

    private static final List<Operation> OPERATIONS = List.of(
        new Operation.Unary("(!(", BooleanType.INSTANCE, "))"),

        new Operation.Binary("(", BooleanType.INSTANCE, " || ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary("(", BooleanType.INSTANCE, " && ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary("(", BooleanType.INSTANCE, " ^ ",    BooleanType.INSTANCE, ")"),

        new Operation.Ternary("(", BooleanType.INSTANCE, " ? ", BooleanType.INSTANCE, " : ", BooleanType.INSTANCE, ")")
    );

    @Override
    public final String name() { return "boolean"; }

    @Override
    public final Object con() {
        // TODO: generator for boolean? Could have different probabilities!
        return RANDOM.nextInt() % 2 == 0;
    }

    @Override
    public final List<Operation> operations() {
        return OPERATIONS;
    }
}
