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

import java.util.List;

import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.Template;

/**
 * The {@link CodeGenerationDataNameType} extends the {@link DataName.Type} with
 * additional functionality for code generation. These types with their extended
 * functionality can be used with many other code generation facilities in the
 * lbrary, such as generating random {@code Expression}s.
 */
public interface CodeGenerationDataNameType extends DataName.Type {

    /**
     * This method provides a random constant value for the type, which can
     * be used as a token inside a {@link Template}.
     *
     * @return A random constant value.
     */
    public Object con();

    public static PrimitiveType bytes()    { return PrimitiveType.BYTES; }
    public static PrimitiveType shorts()   { return PrimitiveType.SHORTS; }
    public static PrimitiveType chars()    { return PrimitiveType.CHARS; }
    public static PrimitiveType ints()     { return PrimitiveType.INTS; }
    public static PrimitiveType longs()    { return PrimitiveType.LONGS; }
    public static PrimitiveType floats()   { return PrimitiveType.FLOATS; }
    public static PrimitiveType doubles()  { return PrimitiveType.DOUBLES; }
    public static PrimitiveType booleans() { return PrimitiveType.BOOLEANS; }

    public static final List<PrimitiveType> PRIMITIVE_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs(),
        floats(),
        doubles(),
        booleans()
    );

    public static final List<PrimitiveType> INTEGRAL_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs()
    );

    public static final List<PrimitiveType> SUBWORD_TYPES = List.of(
        bytes(),
        chars(),
        shorts()
    );

    public static final List<PrimitiveType> FLOATING_TYPES = List.of(
        floats(),
        doubles()
    );

    public static final List<PrimitiveType> INTEGRAL_AND_FLOATING_TYPES = List.of(
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
