/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.test.JUnitUtils.ArrayConverter;
import jdk.jpackage.test.JUnitUtils.ExceptionPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class JUnitUtilsTest {

    @Test
    void test_assertArrayEquals() {
        JUnitUtils.assertArrayEquals(new int[] {1, 2, 3}, new int[] {1, 2, 3});
        JUnitUtils.assertArrayEquals(new long[] {1, 2, 3}, new long[] {1, 2, 3});
        JUnitUtils.assertArrayEquals(new boolean[] {true, true}, new boolean[] {true, true});
        JUnitUtils.assertArrayEquals(null, null);

        assertThrows(ClassCastException.class, () -> {
            JUnitUtils.assertArrayEquals(new int[0], new Integer[0]);
        });

        assertThrows(AssertionError.class, () -> {
            JUnitUtils.assertArrayEquals(new int[0], new int[1]);
        });

        assertThrows(AssertionError.class, () -> {
            JUnitUtils.assertArrayEquals(new int[0], null);
        });

        assertThrows(AssertionError.class, () -> {
            JUnitUtils.assertArrayEquals(null, new int[0]);
        });
    }

    @Test
    void test_assertArrayEquals_negative() {
        assertThrows(AssertionError.class, () -> {
            JUnitUtils.assertArrayEquals(new int[] {1, 2, 3}, new int[] {2, 3});
        });
    }

    @Test
    void test_removeExceptionCause() {

        var ex = JUnitUtils.removeExceptionCause(new IllegalArgumentException("Hello", new NullPointerException()));
        assertEquals(null, ex.getCause());

        var props = JUnitUtils.exceptionAsPropertyMap(ex);
        assertEquals(IllegalArgumentException.class, props.get("getType"));
        assertEquals("Hello", props.get("getMessage"));
        assertEquals(null, props.get("getCause"));
    }

    @Test
    void test_exceptionAsPropertyMap() {

        var ex = new IllegalArgumentException("Hello", new NullPointerException("Bye"));
        var props = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(IllegalArgumentException.class.getName(), props.get("getClass"));
        assertEquals("Hello", props.get("getMessage"));

        @SuppressWarnings("unchecked")
        var causeProps = (Map<String, Object>)props.get("getCause");
        assertEquals(NullPointerException.class.getName(), causeProps.get("getClass"));
        assertEquals("Bye", causeProps.get("getMessage"));
        assertEquals(null, causeProps.get("getCause"));
    }

    @Test
    void test_exceptionAsPropertyMapWithMessageWithoutCause() {

        var ex = new Exception("foo");

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of("getClass", Exception.class.getName(), "getMessage", "foo"), map);
    }

    @ParameterizedTest
    @CsvSource({
        "'a,b,c','100,200,300'",
    })
    void test_ArrayConverter(
            @ConvertWith(ArrayConverter.class) String[] strArray,
            @ConvertWith(ArrayConverter.class) Integer[] intArray) {

        assertEquals(List.of("a", "b", "c"), List.of(strArray));
        assertEquals(List.of(100, 200, 300), List.of(intArray));
    }

    @ParameterizedTest
    @CsvSource(",")
    void test_ArrayConverter_null(
            @ConvertWith(ArrayConverter.class) String[] strArray,
            @ConvertWith(ArrayConverter.class) Integer[] intArray) {

        assertEquals(null, strArray);
        assertEquals(null, intArray);
    }

    @ParameterizedTest
    @CsvSource({
        ",true,true",
        "FOO,true,false",
        "NULL,false,true",
        "BAR,false,false",
    })
    void test_ExceptionPattern_hasMessage(ExceptionPatternMessageMode mode, boolean exWithMessageMatch, boolean exWithoutMessageMatch) {

        var exWithMessage = new Exception(ExceptionPatternMessageMode.FOO.name());
        var exWithoutMessage = new Exception();

        var pattern = new ExceptionPattern();
        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> pattern.hasMessage(null);
                default -> pattern.hasMessage(m.name());
            }
        });

        assertEquals(exWithMessageMatch, pattern.match(exWithMessage));
        assertEquals(exWithoutMessageMatch, pattern.match(exWithoutMessage));
    }

    enum ExceptionPatternMessageMode {
        NULL,
        FOO,
        BAR,
    }

    @ParameterizedTest
    @CsvSource({
        ",true,true",
        "NULL,false,true",
        "IllegalArgumentException,true,false",
        "RuntimeException,true,false",
        "Exception,true,false",
        "IOException,false,false",
        "NullPointerException,false,false",
        "TRUE,true,false",
        "FALSE,false,true",
        "TRUE_DEFAULT,true,false",
    })
    void test_ExceptionPattern_hasCause(ExceptionPatternCauseMode mode, boolean exWithCauseMatch, boolean exWithoutCauseMatch) {

        var exWithCause = new Exception(new IllegalArgumentException());
        var exWithoutCause = new Exception();

        var pattern = new ExceptionPattern();
        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> pattern.isCauseInstanceOf(null);
                case IllegalArgumentException -> pattern.isCauseInstanceOf(IllegalArgumentException.class);
                case RuntimeException -> pattern.isCauseInstanceOf(RuntimeException.class);
                case Exception -> pattern.isCauseInstanceOf(Exception.class);
                case IOException -> pattern.isCauseInstanceOf(IOException.class);
                case NullPointerException -> pattern.isCauseInstanceOf(NullPointerException.class);
                case TRUE -> pattern.hasCause(true);
                case FALSE -> pattern.hasCause(false);
                case TRUE_DEFAULT -> pattern.hasCause();
            }
        });

        assertEquals(exWithCauseMatch, pattern.match(exWithCause));
        assertEquals(exWithoutCauseMatch, pattern.match(exWithoutCause));
    }

    enum ExceptionPatternCauseMode {
        NULL,
        IllegalArgumentException,
        RuntimeException,
        Exception,
        IOException,
        NullPointerException,
        TRUE,
        FALSE,
        TRUE_DEFAULT,
    }

    @ParameterizedTest
    @CsvSource({
        ",true",
        "NULL,true",
        "IllegalArgumentException,true",
        "RuntimeException,true",
        "Exception,true",
        "IOException,false",
        "NullPointerException,false",
    })
    void test_ExceptionPattern_isInstanceOf(ExceptionPatternTypeMode mode, boolean match) {

        var ex = new IllegalArgumentException();

        var pattern = new ExceptionPattern();
        Optional.ofNullable(mode).ifPresent(m -> {
            switch (m) {
                case NULL -> pattern.isInstanceOf(null);
                case IllegalArgumentException -> pattern.isInstanceOf(IllegalArgumentException.class);
                case RuntimeException -> pattern.isInstanceOf(RuntimeException.class);
                case Exception -> pattern.isInstanceOf(Exception.class);
                case IOException -> pattern.isInstanceOf(IOException.class);
                case NullPointerException -> pattern.isInstanceOf(NullPointerException.class);
            }
        });

        assertEquals(match, pattern.match(ex));
    }

    enum ExceptionPatternTypeMode {
        NULL,
        IllegalArgumentException,
        RuntimeException,
        Exception,
        NullPointerException,
        IOException,
    }
}
