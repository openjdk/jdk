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

/**
 * @test
 * @bug 8310242
 * @run junit ForNameNames
 * @summary Verify class names for Class.forName
 */

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class ForNameNames {
    static class Inner {}
    static Stream<Arguments> testCases() {
        return Stream.of(
                Arguments.of("java.lang.String", String.class),
                Arguments.of("[Ljava.lang.String;", String[].class),
                Arguments.of("ForNameNames$Inner", Inner.class),
                Arguments.of("[LForNameNames$Inner;", Inner[].class),
                Arguments.of("[[I", int[][].class)
        );
    }

    /*
     * Test 1-arg and 3-arg Class::forName.  Class::getName on the returned
     * Class object returns the name passed to Class::forName.
     */
    @ParameterizedTest
    @MethodSource("testCases")
    void testForName(String cn, Class<?> expected) throws ClassNotFoundException {
        ClassLoader loader = ForNameNames.class.getClassLoader();
        Class<?> c1 = Class.forName(cn, false, loader);
        assertEquals(expected, c1);
        assertEquals(cn, c1.getName());

        Class<?> c2 = Class.forName(cn);
        assertEquals(expected, c2);
        assertEquals(cn, c2.getName());
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of("I"),                   // primitive type
                Arguments.of("int[]"),               // fully-qualified name of int array
                Arguments.of("ForNameNames.Inner"),  // fully-qualified name of nested type
                Arguments.of("[java.lang.String"),   // missing L and ;
                Arguments.of("[Ljava.lang.String"),  // missing ;
                Arguments.of("[Ljava/lang/String;")  // type descriptor

        );
    }
    @ParameterizedTest
    @MethodSource("invalidNames")
    void testInvalidNames(String cn) {
        ClassLoader loader = ForNameNames.class.getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName(cn, false, loader));
    }

    @Test
    void testModule() {
        // Class.forName(Module, String) does not allow class name for array types
        Class<?> c = Class.forName(Object.class.getModule(), "[Ljava.lang.String;");
        assertNull(c);
    }

}
