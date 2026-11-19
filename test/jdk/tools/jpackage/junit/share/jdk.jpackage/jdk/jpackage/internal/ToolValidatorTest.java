/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.DottedVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;


public class ToolValidatorTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAvailable(boolean checkExistsOnly) {
        assertNull(new ToolValidator(TOOL_JAVA).checkExistsOnly(checkExistsOnly).validate());
    }

    @Test
    public void testAvailable_setCommandLine() {
        // java doesn't recognize "--foo" command line option, but the validation will
        // still pass as there is no minimal version specified and the validator ignores
        // the exit code
        assertNull(new ToolValidator(TOOL_JAVA).setCommandLine("--foo").validate());
    }

    enum TestAvailableMode {
        NO_VERSION(null),
        TOO_OLD("0.9"),
        EQUALS("1.0"),
        NEWER("1.1");

        TestAvailableMode(String parsedVersion) {
            this.parsedVersion = parsedVersion;
        }

        final String parsedVersion;
    }

    @ParameterizedTest
    @EnumSource(TestAvailableMode.class)
    public void testAvailable(TestAvailableMode mode) {
        var minVer = TestAvailableMode.EQUALS.parsedVersion;
        var err = new ToolValidator(TOOL_JAVA).setVersionParser(lines -> {
            return mode.parsedVersion;
        }).setMinimalVersion(DottedVersion.greedy(minVer)).validate();

        if (Set.of(TestAvailableMode.NO_VERSION, TestAvailableMode.TOO_OLD).contains(mode)) {
            var expectedMessage = I18N.format("error.tool-old-version", TOOL_JAVA, minVer);
            var expectedAdvice = I18N.format("error.tool-old-version.advice", TOOL_JAVA, minVer);

            assertEquals(expectedMessage, err.getMessage());
            assertEquals(expectedAdvice, err.getAdvice());
        } else {
            assertNull(err);
        }
    }

    @ParameterizedTest
    @EnumSource(TestAvailableMode.class)
    public void testAvailable_setToolOldVersionErrorHandler(TestAvailableMode mode) {
        var handler = new ToolOldVersionErrorHandler();
        var minVer = TestAvailableMode.EQUALS.parsedVersion;
        var err = new ToolValidator(TOOL_JAVA).setVersionParser(lines -> {
            return mode.parsedVersion;
        }).setMinimalVersion(DottedVersion.greedy(minVer)).setToolOldVersionErrorHandler(handler).validate();

        if (Set.of(TestAvailableMode.NO_VERSION, TestAvailableMode.TOO_OLD).contains(mode)) {
            assertSame(ToolOldVersionErrorHandler.ERR, err);
            handler.verifyCalled(Path.of(TOOL_JAVA), mode.parsedVersion);
        } else {
            assertNull(err);
            handler.verifyNotCalled();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testNotAvailable(boolean checkExistsOnly, @TempDir Path dir) {
        var err = new ToolValidator(dir.resolve("foo")).checkExistsOnly(checkExistsOnly).validate();
        if (checkExistsOnly) {
            assertValidationFailure(err, false);
        } else {
            assertValidationFailureNoAdvice(err, !checkExistsOnly);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testToolIsDirectory(boolean checkExistsOnly, @TempDir Path dir) {
        var err = new ToolValidator(dir).checkExistsOnly(checkExistsOnly).validate();
        assertValidationFailureNoAdvice(err, !checkExistsOnly);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testNotAvailable_setToolNotFoundErrorHandler(boolean checkExistsOnly, @TempDir Path dir) {
        var handler = new ToolNotFoundErrorHandler();
        var err = new ToolValidator(dir.resolve("foo")).checkExistsOnly(checkExistsOnly)
                .setToolNotFoundErrorHandler(handler)
                .validate();
        if (checkExistsOnly) {
            handler.verifyCalled(dir.resolve("foo"));
            assertSame(ToolNotFoundErrorHandler.ERR, err);
        } else {
            handler.verifyNotCalled();
            assertValidationFailureNoAdvice(err, !checkExistsOnly);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testToolIsDirectory_setToolNotFoundErrorHandler(boolean checkExistsOnly, @TempDir Path dir) {
        var handler = new ToolNotFoundErrorHandler();
        var err = new ToolValidator(dir).checkExistsOnly(checkExistsOnly).validate();
        handler.verifyNotCalled();
        assertValidationFailureNoAdvice(err, !checkExistsOnly);
    }

    @Test
    public void testVersionParserUsage() {
        // Without minimal version configured, version parser should not be used
        new ToolValidator(TOOL_JAVA).setVersionParser(unused -> {
            throw new AssertionError();
        }).validate();

        // Minimal version is 1, actual is 10. Should be OK.
        assertNull(new ToolValidator(TOOL_JAVA).setMinimalVersion(
                DottedVersion.greedy("1")).setVersionParser(unused -> "10").validate());

        // Minimal version is 5, actual is 4.99.37. Error expected.
        assertValidationFailure(new ToolValidator(TOOL_JAVA).setMinimalVersion(
                DottedVersion.greedy("5")).setVersionParser(unused -> "4.99.37").validate(),
                false);

        // Minimal version is 8, actual is 10, lexicographical comparison is used. Error expected.
        assertValidationFailure(new ToolValidator(TOOL_JAVA).setMinimalVersion(
                "8").setVersionParser(unused -> "10").validate(), false);

        // Minimal version is 8, actual is 10, Use DottedVersion class for comparison. Should be OK.
        assertNull(new ToolValidator(TOOL_JAVA).setMinimalVersion(
                DottedVersion.greedy("8")).setVersionParser(unused -> "10").validate());
    }

    private static void assertValidationFailure(ConfigException v, boolean withCause) {
        assertNotNull(v);
        assertNotEquals("", v.getMessage().strip());
        assertNotEquals("", v.getAdvice().strip());
        if (withCause) {
            assertNotNull(v.getCause());
        } else {
            assertNull(v.getCause());
        }
    }

    private static void assertValidationFailureNoAdvice(ConfigException v, boolean withCause) {
        assertNotNull(v);
        assertNotEquals("", v.getMessage().strip());
        assertNull(v.getAdvice());
        if (withCause) {
            assertNotNull(v.getCause());
        } else {
            assertNull(v.getCause());
        }
    }


    private static final class ToolNotFoundErrorHandler implements Function<Path, ConfigException> {

        @Override
        public ConfigException apply(Path tool) {
            assertNotNull(tool);
            this.tool = tool;
            return ERR;
        }

        void verifyCalled(Path expectedTool) {
            assertEquals(Objects.requireNonNull(expectedTool), tool);
        }

        void verifyNotCalled() {
            assertNull(tool);
        }

        private Path tool;

        static final ConfigException ERR = new ConfigException("no tool", "install the tool");
    }


    private static final class ToolOldVersionErrorHandler implements BiFunction<Path, String, ConfigException> {

        @Override
        public ConfigException apply(Path tool, String parsedVersion) {
            assertNotNull(tool);
            this.tool = tool;
            this.parsedVersion = parsedVersion;
            return ERR;
        }

        void verifyCalled(Path expectedTool, String expectedParsedVersion) {
            assertEquals(Objects.requireNonNull(expectedTool), tool);
            assertEquals(expectedParsedVersion, parsedVersion);
        }

        void verifyNotCalled() {
            assertNull(tool);
        }

        private Path tool;
        private String parsedVersion;

        static final ConfigException ERR = new ConfigException("tool too old", "install the newer version");
    }


    private static final String TOOL_JAVA;

    static {
        String fname = "java";
        if (OperatingSystem.isWindows()) {
            fname = fname + ".exe";
        }
        TOOL_JAVA = Path.of(System.getProperty("java.home"), "bin", fname).toString();
    }
}
