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

package compiler.lib.template_framework.library;

import compiler.lib.generators.Generators;
import compiler.lib.generators.Generator;

import compiler.lib.template_framework.DataName;

/**
 * The {@link Float16Type} models Java's {@link Float16} type.
 */
final class Float16Type implements CodeGenerationDataNameType {
    private static final Generator<Short> GEN_FLOAT16 = Generators.G.float16s();

    // We only need one static instance of the class.
    static final Float16Type FLOAT16 = new Float16Type();

    // Private constructor so nobody can create duplicate instances.
    private Float16Type() {}

    @Override
    public boolean isSubtypeOf(DataName.Type other) {
        return other instanceof Float16Type;
    }

    @Override
    public String name() {
        return "Float16";
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public Object con() {
        return "Float16.shortBitsToFloat16((short)" + GEN_FLOAT16.next() + ")";
    }
}
