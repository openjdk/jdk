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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class StandardValueConverterTest {

    @Test
    public void test_identityConv() throws Exception {

        final var testee = StandardValueConverter.identityConv();

        assertEquals("foo", testee.convert("foo"));
    }

    @Test
    public void testNullValue() {
        assertThrowsExactly(NullPointerException.class, () -> StandardValueConverter.identityConv().convert(null));
        assertThrowsExactly(NullPointerException.class, () -> StandardValueConverter.pathConv().convert(null));
        assertThrowsExactly(NullPointerException.class, () -> StandardValueConverter.uuidConv().convert(null));
        assertThrowsExactly(NullPointerException.class, () -> StandardValueConverter.booleanConv().convert(null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_pathConv(boolean positive) throws Exception {

        final var testee = StandardValueConverter.pathConv();

        if (positive) {
            assertEquals(Path.of("foo"), testee.convert("foo"));
        } else {
            final var ex = assertThrowsExactly(IllegalArgumentException.class, () -> testee.convert("\0"));
            assertNotNull(ex.getCause());
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test_booleanConv(String value, Boolean expected) throws Exception {

        assertEquals(expected, StandardValueConverter.booleanConv().convert(value));
    }

    @ParameterizedTest
    @MethodSource
    public void test_mainLauncherShortcutConv(String value, LauncherShortcut expected) throws Exception {

        assertEquals(expected, StandardValueConverter.mainLauncherShortcutConv().convert(value));
    }

    @ParameterizedTest
    @MethodSource
    public void test_mainLauncherShortcutConv_invalid(String value) {

        assertThrowsExactly(IllegalArgumentException.class, () -> StandardValueConverter.mainLauncherShortcutConv().convert(value));
    }

    @ParameterizedTest
    @MethodSource
    public void test_addLauncherShortcutConv(String value, LauncherShortcut expected) throws Exception {

        assertEquals(expected, StandardValueConverter.addLauncherShortcutConv().convert(value));
    }

    @ParameterizedTest
    @MethodSource
    @Disabled("There is no invalid string value!")
    public void test_addLauncherShortcutConv_invalid(String value) {

        assertThrowsExactly(IllegalArgumentException.class, () -> StandardValueConverter.addLauncherShortcutConv().convert(value));
    }

    private static List<Object[]> test_booleanConv() {
        return List.<Object[]>of(
                booleanConvTestCase("", false),
                booleanConvTestCase("true", true),
                booleanConvTestCase("True", true),
                booleanConvTestCase("TRUE", true),
                booleanConvTestCase("true2", false),
                booleanConvTestCase("false", false),
                booleanConvTestCase("false2", false)
        );
    }

    private static Object[] booleanConvTestCase(String value, boolean expected) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(expected);
        return new Object[] { value, expected };
    }

    private static List<Object[]> test_mainLauncherShortcutConv() {
        return List.<Object[]>of(
                launcherShortcutConvTestCase("app-dir", new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR))
        );
    }

    private static List<Arguments> test_mainLauncherShortcutConv_invalid() {
        return List.of(
                Arguments.of("true"),
                Arguments.of("false"),
                Arguments.of("App-dir"),
                Arguments.of("APP-DIR")
        );
    }

    private static List<Object[]> test_addLauncherShortcutConv() {
        return List.<Object[]>of(
                launcherShortcutConvTestCase("app-dir", new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR)),
                launcherShortcutConvTestCase("true", new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT)),
                launcherShortcutConvTestCase("false", new LauncherShortcut()),
                launcherShortcutConvTestCase("APP-DIR", new LauncherShortcut())
        );
    }

    private static List<Arguments> test_addLauncherShortcutConv_invalid() {
        return List.of(
        );
    }

    private static Object[] launcherShortcutConvTestCase(String value, LauncherShortcut expected) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(expected);
        return new Object[] { value, expected };
    }
}
