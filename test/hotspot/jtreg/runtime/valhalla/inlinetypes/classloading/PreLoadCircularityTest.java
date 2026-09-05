
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
 *
 */

/*
 * @test
 * @summary Ensures circularity does not cause crashes.
 * @enablePreview
 * @compile BigClassTreeClassLoader.java
 * @run junit runtime.valhalla.inlinetypes.classloading.PreLoadCircularityTest
 */

package runtime.valhalla.inlinetypes.classloading;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// This test makes use of BigClassTreeClassLoader. Please refer to its documentation.
class PreLoadCircularityTest {

    @ParameterizedTest
    @MethodSource("constellations")
    void test(int depth, int fieldIndex, Optional<String> fieldClass, Optional<String> parentClass)
            throws ClassNotFoundException, ReflectiveOperationException {
        // Create the generator.
        var fg = new BigClassTreeClassLoader.FieldGeneration(fieldIndex, fieldClass, parentClass);
        BigClassTreeClassLoader cl = new BigClassTreeClassLoader(depth, fg);
        // Generate the classes!
        Class<?> clazz = Class.forName("Gen" + (depth - 1), false, cl);
        clazz.getDeclaredConstructor().newInstance();
    }

    private static Stream<Arguments> constellations() {
        return Stream.of(
            // Class Gen10 will have 30 fields and Field0 will inherit from Gen15.
            // This forms a cycle through field preloading and inheritance.
            Arguments.of(30, 10, Optional.of(Object.class.getName()), Optional.of("Gen15")),
            // Class Gen10 will have 30 fields and Field0 will refer to Gen10.
            // This forms a cycle through field preloading.
            Arguments.of(30, 10, Optional.of("Gen10"), Optional.empty()),
            // Class Gen10 will have 30 fields and Field0 will inherit from
            // Gen15 and refer to Gen13. This forms a cycle through field and
            // inheritance preloading.
            Arguments.of(30, 10, Optional.of("Gen13"), Optional.of("Gen15"))
        );
    }
}
