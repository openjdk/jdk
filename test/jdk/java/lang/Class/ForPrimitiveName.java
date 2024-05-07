/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test Class.forPrimitiveName
 * @bug 6361826
 */

import java.util.*;

public class ForPrimitiveName {
    public static void main(String... args) {
        positiveTests();
        negativeTests();
    }

    private static final void positiveTests() {

        /**
         * immutable table mapping primitive type names to corresponding
         * class objects
         */
        final Map<String, Class<?>> primClasses =
            Map.of("boolean", boolean.class,
                   "byte",    byte.class,
                   "char",    char.class,
                   "short",   short.class,
                   "int",     int.class,
                   "long",    long.class,
                   "float",   float.class,
                   "double",  double.class,
                   "void",    void.class);

        for (var entry : primClasses.entrySet()) {
            String key = entry.getKey();
            Class<?> expected = entry.getValue();
            Class<?> result = Class.forPrimitiveName(key);

            // For java.lang.Class, equality is identity.
            if (result != expected) {
                throw new RuntimeException("Unexpected mapping for " + key);
            }
        }
    }

    private static final void negativeTests() {
        final List<String> expectedNull =
            List.of("java.lang.Object",
                    "java.lang.String",

                    // descriptor string names for primitive types
                    "Z", // boolean
                    "B", // byte
                    "C", // char
                    "D", // double
                    "F", // float
                    "I", // int
                    "L", // long
                    "S", // short
                    "V", // void

                    // Wrapper classes
                    "java.lang.Byte",
                    "java.lang.Boolean",
                    "java.lang.Character",
                    "java.lang.Short",
                    "java.lang.Integer",
                    "java.lang.Long",
                    "java.lang.Float",
                    "java.lang.Double",
                    "java.lang.Void");
        for (var entry : expectedNull) {
            Class<?> result = Class.forPrimitiveName(entry);
            if (result != null ) {
                throw new RuntimeException("Unexpected nonnull result for " +
                                           entry);
            }
        }
    }
}


