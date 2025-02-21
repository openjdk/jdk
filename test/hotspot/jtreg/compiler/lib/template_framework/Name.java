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

package compiler.lib.template_framework;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Name}s represent things like fields and local variables, or even method names that can be
 * added to a code scope with {@link Template#addName} and sampled with {@link Template#sampleName},
 * according to the {@code 'weight'} of each {@link Name}. Every {@link Name} has a {@link Name.Type},
 * so that sampling can be restricted to these types, or subtypes, defined by {@link Name.Type#isSubtypeOf}.
 *
 * @param name The {@link String} name used in code.
 * @param type The type with which we restrict {@link Template#weighNames} and {@link Template#sampleName}.
 * @param mutable Defines if the name is considered mutable or immutable.
 * @param weight The weight measured by {@link Template#weighNames} and according to which we sample with {@link Template#sampleName}.
 */
public record Name(String name, Name.Type type, boolean mutable, int weight) {

    /**
     * Creates a new {@link Name}.
     */
    public Name {
        if (0 >= weight || weight > 1000) {
            throw new IllegalArgumentException("Unexpected weight: " + weight);
        }
    }

    /**
     * The interface for the type of a {@link Name}.
     */
    public interface Type {
        /**
         * The name of the type, that can be used in code.
         *
         * @return The {@link String} representation of the type, that can be used in code.
         */
        String name();

        /**
         * Defines the subtype relationship with other types, which is used to filter {@link Name}s
         * in {@link Template#weighNames} and {@link Template#sampleName}.
         *
         * @param other The other type, where we check if it is the supertype of {@code 'this'}.
         * @return If {@code 'this'} is a subtype of {@code 'other'}.
         */
        boolean isSubtypeOf(Type other);
    }
}
