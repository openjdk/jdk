/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8310242 8328874
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
    //Max length in Modified UTF-8 bytes for class names.
    private static final int JAVA_CLASSNAME_MAX_LEN = 65535;

    private static final String ONE_BYTE = "A";                    // 1-byte UTF-8
    private static final String TWO_BYTE = "\u0100";               // 2-byte UTF-8
    private static final String THREE_BYTE = "\u2600";             // 3-byte UTF-8

    private static final String ERR_MSG_IN_CORE = "Class name length exceeds limit of"; // check in corelib
    private static final String ERR_MSG_IN_JVM = "Class name exceeds maximum length";   // check in jvm

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

    static Stream<Arguments> validLen() {
        return Stream.of(
                // 1-byte character
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN - 1)),
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN)),
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3 - 1)),
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3)),
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3 + 1)),
                // 2-byte characters
                Arguments.of(TWO_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 2)),
                Arguments.of(TWO_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 6)),
                Arguments.of(TWO_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 6 + 1)),
                // 3-byte characters
                Arguments.of(THREE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3 - 1)),
                Arguments.of(THREE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3)),
                Arguments.of(THREE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 9)),
                Arguments.of(THREE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 9 + 1))
        );
    }

    /*
     * Test class name length handling in 1-arg and 3-arg Class::forName
     * with valid length.
     */
    @ParameterizedTest
    @MethodSource("validLen")
    void testValidLen(String cn) {
        ClassLoader loader = ForNameNames.class.getClassLoader();
        // 3-arg Class.forName
        ClassNotFoundException ex = assertThrows(ClassNotFoundException.class,
                                                 () -> Class.forName(cn, false, loader));
        assertFalse(ex.getMessage().contains(ERR_MSG_IN_CORE)
                    || ex.getMessage().contains(ERR_MSG_IN_JVM),
                    "Unexpected exception message");

        // 1-arg Class.forName
        ex = assertThrows(ClassNotFoundException.class,
                          () -> Class.forName(cn));
        assertFalse(ex.getMessage().contains(ERR_MSG_IN_CORE)
                    || ex.getMessage().contains(ERR_MSG_IN_JVM),
                    "Unexpected exception message");
    }

    static Stream<Arguments> invalidLen() {
        return Stream.of(
                // 1-byte characters over the limit
                Arguments.of(ONE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN + 1)),
                // 2-byte characters over the limit
                Arguments.of(TWO_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 2 + 1)),
                // 3-byte characters over the limit
                Arguments.of(THREE_BYTE.repeat(JAVA_CLASSNAME_MAX_LEN / 3 + 1))
        );
    }

    /*
     * Test class name length handling in 1-arg and 3-arg Class::forName
     * with invalid (too long) length.
     */
    @ParameterizedTest
    @MethodSource("invalidLen")
    void testInvalidLen(String cn) {
        ClassLoader loader = ForNameNames.class.getClassLoader();
        // 3-arg Class.forName
        ClassNotFoundException ex = assertThrows(ClassNotFoundException.class,
                                                 () -> Class.forName(cn, false, loader));
        assertTrue(ex.getMessage().contains(ERR_MSG_IN_CORE),
                   "Unexpected exception message");

        // 1-arg Class.forName
        ex = assertThrows(ClassNotFoundException.class,
                          () -> Class.forName(cn));
        assertTrue(ex.getMessage().contains(ERR_MSG_IN_CORE),
                   "Unexpected exception message");
    }
}