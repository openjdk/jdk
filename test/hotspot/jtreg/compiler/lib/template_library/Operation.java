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
import java.util.HashSet;

public sealed interface Operation permits Operation.Unary,
                                          Operation.Binary,
                                          Operation.Ternary,
                                          Operation.Quaternary {

    /**
     * Check if input types are in the set.
     * Check that return type is subtype of the desired type.
     */
    public abstract boolean matchesReturnType(Type returnType);

    public abstract boolean matchesTypes(HashSet<Type> types);

    public static record Unary(Type r, String s0, Type t0, String s1, List<String> exceptions) implements Operation {
        @Override
        public boolean matchesReturnType(Type returnType) {
            return r.isSubtypeOf(returnType);
        }

        @Override
        public boolean matchesTypes(HashSet<Type> types) {
            return types.contains(r) &&
                   types.contains(t0);
        }
    }

    public static record Binary(Type r, String s0, Type t0, String s1, Type t1, String s2, List<String> exceptions) implements Operation {
        @Override
        public boolean matchesReturnType(Type returnType) {
            return r.isSubtypeOf(returnType);
        }

        @Override
        public boolean matchesTypes(HashSet<Type> types) {
            return types.contains(r) &&
                   types.contains(t0) &&
                   types.contains(t1);
        }
    }

    public static record Ternary(Type r, String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3, List<String> exceptions) implements Operation {
        @Override
        public boolean matchesReturnType(Type returnType) {
            return r.isSubtypeOf(returnType);
        }

        @Override
        public boolean matchesTypes(HashSet<Type> types) {
            return types.contains(r) &&
                   types.contains(t0) &&
                   types.contains(t1) &&
                   types.contains(t2);
        }
    }

    public static record Quaternary(Type r, String s0, Type t0, String s1, Type t1, String s2, Type t2, String s3, Type t3, String s4, List<String> exceptions) implements Operation {
        @Override
        public boolean matchesReturnType(Type returnType) {
            return r.isSubtypeOf(returnType);
        }

        @Override
        public boolean matchesTypes(HashSet<Type> types) {
            return types.contains(r) &&
                   types.contains(t0) &&
                   types.contains(t1) &&
                   types.contains(t2) &&
                   types.contains(t3);
        }
    }
}
