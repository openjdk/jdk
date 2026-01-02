/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


public class EnquoterTest {

    @ParameterizedTest
    @MethodSource
    public void testForShellLiterals(String expected, String input) {
        var actual = Enquoter.forShellLiterals().applyTo(input);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource
    public void testForPropertyValues(String expected, String input) {
        var actual = Enquoter.forPropertyValues().applyTo(input);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource
    public void testIdentity(String input) {
        var actual = Enquoter.identity().applyTo(input);
        assertEquals(input, actual);
    }

    @ParameterizedTest
    @MethodSource("testIdentity")
    public void testNoEscaper(String input) {
        var actual = Enquoter.identity().setEnquotePredicate(_ -> true).applyTo(input);
        assertEquals('"' + input + '"', actual);
    }

    private static Stream<Arguments> testForShellLiterals() {
        return Stream.of(
                Arguments.of("''", ""),
                Arguments.of("'foo'", "foo"),
                Arguments.of("' foo '", " foo "),
                Arguments.of("'foo bar'", "foo bar"),
                Arguments.of("'foo\\' bar'", "foo' bar")
        );
    }

    private static Stream<Arguments> testForPropertyValues() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("foo", "foo"),
                Arguments.of("\" foo \"", " foo "),
                Arguments.of("\"foo bar\"", "foo bar"),
                Arguments.of("\"foo' bar\"", "foo' bar")
        );
    }

    private static Stream<String> testIdentity() {
        return Stream.of("", "foo", " foo ", "foo bar", "foo' bar");
    }
}
