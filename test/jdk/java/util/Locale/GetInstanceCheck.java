/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6312358
 * @summary Verify that an NPE is thrown by invoking Locale.getInstance() with
 * any argument being null.
 * @modules java.base/java.util:open
 * @run junit GetInstanceCheck
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.fail;

public class GetInstanceCheck {

    static Method getInstanceMethod;
    static final String NAME = "getInstance";

    /**
     * Initialize the non-public Locale.getInstance() method.
     */
    @BeforeAll
    static void initializeMethod() {
        try {
            // Locale.getInstance is not directly accessible.
            getInstanceMethod = Locale.class.getDeclaredMethod(
                    NAME, String.class, String.class, String.class
            );
            getInstanceMethod.setAccessible(true);
        } catch (java.lang.NoSuchMethodException exc) {
            // The test should fail if we can not test the desired method
            fail(String.format("Tried to get the method '%s' which was not found," +
                    " further testing is not possible, failing test", NAME));
        }
    }

    /**
     * Exists as sanity check that Locale.getInstance() will not throw
     * an NPE if no arguments are null.
     */
    @ParameterizedTest
    @MethodSource("passingArguments")
    public void noNPETest(String language, String country, String variant)
            throws IllegalAccessException {
        try {
            getInstanceMethod.invoke(null, language, country, variant);
        } catch (InvocationTargetException exc) {
            // Determine underlying exception
            Throwable cause = exc.getCause();
            if (exc.getCause() instanceof NullPointerException) {
                fail(String.format("%s should not be thrown when no args are null", cause));
            } else {
                fail(String.format("%s unexpectedly thrown, when no exception should be thrown", cause));
            }
        }
    }

    /**
     * Make sure the Locale.getInstance() method throws an NPE
     * if any given argument is null.
     */
    @ParameterizedTest
    @MethodSource("failingArguments")
    public void throwNPETest(String language, String country, String variant)
            throws IllegalAccessException {
        try {
            getInstanceMethod.invoke(null, language, country, variant);
            fail("Should NPE with any argument set to null");
        } catch (InvocationTargetException exc) {
            // Determine underlying exception
            Throwable cause = exc.getCause();
            if (cause instanceof NullPointerException) {
                System.out.println("NPE successfully thrown");
            } else {
                fail(cause + " is thrown, when NPE should have been thrown");
            }
        }
    }

    private static Stream<Arguments> passingArguments() {
        return Stream.of(
                Arguments.of("null", "GB", ""),
                Arguments.of("en", "null", ""),
                Arguments.of("en", "GB", "null")
        );
    }

    private static Stream<Arguments> failingArguments() {
        return Stream.of(
                Arguments.of(null, "GB", ""),
                Arguments.of("en", null, ""),
                Arguments.of("en", "GB", null)
        );
    }
}
