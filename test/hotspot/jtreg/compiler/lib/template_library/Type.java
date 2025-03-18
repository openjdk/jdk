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
import java.util.stream.Stream;

import compiler.lib.template_framework.Name;

/**
 * The {@link Type} abstract class defines the basic functionalities that any {@link Type}
 * must provide, such as generating random expressions.
 *
 * TODO
 */
public abstract class Type implements Name.Type {

    public static final PrimitiveType bytes() { return PrimitiveType.ByteType.INSTANCE; }
    public static final PrimitiveType chars() { return PrimitiveType.CharType.INSTANCE; }
    public static final PrimitiveType shorts() { return PrimitiveType.ShortType.INSTANCE; }
    public static final PrimitiveType ints() { return PrimitiveType.IntType.INSTANCE; }
    public static final PrimitiveType longs() { return PrimitiveType.LongType.INSTANCE; }
    public static final PrimitiveType floats() { return PrimitiveType.FloatType.INSTANCE; }
    public static final PrimitiveType doubles() { return PrimitiveType.DoubleType.INSTANCE; }
    public static final PrimitiveType booleans() { return PrimitiveType.BooleanType.INSTANCE; }

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

    public static final List<PrimitiveType> INT_LONG_TYPES = List.of(
        ints(),
        longs()
    );

    public static final List<PrimitiveType> FLOATING_TYPES = List.of(
        floats(),
        doubles()
    );

    public static final List<VectorAPIType> VECTOR_API_BYTE_VECTOR_TYPES = List.of(
        VectorAPIType.BYTE_64,
        VectorAPIType.BYTE_128,
        VectorAPIType.BYTE_256,
        VectorAPIType.BYTE_512
    );

    public static final List<VectorAPIType> VECTOR_API_SHORT_VECTOR_TYPES = List.of(
        VectorAPIType.SHORT_64,
        VectorAPIType.SHORT_128,
        VectorAPIType.SHORT_256,
        VectorAPIType.SHORT_512
    );

    public static final List<VectorAPIType> VECTOR_API_INT_VECTOR_TYPES = List.of(
        VectorAPIType.INT_64,
        VectorAPIType.INT_128,
        VectorAPIType.INT_256,
        VectorAPIType.INT_512
    );

    public static final List<VectorAPIType> VECTOR_API_LONG_VECTOR_TYPES = List.of(
        VectorAPIType.LONG_64,
        VectorAPIType.LONG_128,
        VectorAPIType.LONG_256,
        VectorAPIType.LONG_512
    );

    public static final List<VectorAPIType> VECTOR_API_FLOAT_VECTOR_TYPES = List.of(
        VectorAPIType.FLOAT_64,
        VectorAPIType.FLOAT_128,
        VectorAPIType.FLOAT_256,
        VectorAPIType.FLOAT_512
    );

    public static final List<VectorAPIType> VECTOR_API_DOUBLE_VECTOR_TYPES = List.of(
        VectorAPIType.DOUBLE_64,
        VectorAPIType.DOUBLE_128,
        VectorAPIType.DOUBLE_256,
        VectorAPIType.DOUBLE_512
    );

    // TODO: concat?
    public static final List<VectorAPIType> VECTOR_API_VECTOR_TYPES = Stream.of(
        VECTOR_API_BYTE_VECTOR_TYPES,
        VECTOR_API_SHORT_VECTOR_TYPES,
        VECTOR_API_INT_VECTOR_TYPES,
        VECTOR_API_LONG_VECTOR_TYPES,
        VECTOR_API_FLOAT_VECTOR_TYPES,
        VECTOR_API_DOUBLE_VECTOR_TYPES
    ).flatMap((List<VectorAPIType> l) -> l.stream()).toList();

    public static final List<VectorAPIType.MaskType> VECTOR_API_MASK_TYPES =
        VECTOR_API_VECTOR_TYPES.stream().map(t -> t.maskType).toList();

    public static final List<VectorAPIType.ShuffleType> VECTOR_API_SHUFFLE_TYPES =
        VECTOR_API_VECTOR_TYPES.stream().map(t -> t.shuffleType).toList();

    public static final List<Type> ALL_VECTOR_API_TYPES = Library.concat(
        VECTOR_API_VECTOR_TYPES,
        VECTOR_API_MASK_TYPES,
        VECTOR_API_SHUFFLE_TYPES
    );

    public static final List<Type> ALL_BUILTIN_TYPES = Library.concat(
        PRIMITIVE_TYPES,
        ALL_VECTOR_API_TYPES
    );

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
