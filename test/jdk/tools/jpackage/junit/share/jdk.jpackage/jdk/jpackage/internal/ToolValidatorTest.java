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

import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.ConfigException;
import java.nio.file.Path;
import jdk.internal.util.OperatingSystem;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;


public class ToolValidatorTest {

    @Test
    public void testAvailable() {
        assertNull(new ToolValidator(TOOL_JAVA).validate());
    }

    @Test
    public void testNotAvailable() {
        assertValidationFailure(new ToolValidator(TOOL_UNKNOWN).validate(), true);
    }

    @Test
    public void testVersionParserUsage() {
        // Without minimal version configured, version parser should not be used
        new ToolValidator(TOOL_JAVA).setVersionParser(unused -> {
            throw new RuntimeException();
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

    private static final String TOOL_JAVA;
    private static final String TOOL_UNKNOWN = Path.of(System.getProperty(
            "java.home"), "bin").toString();

    static {
        String fname = "java";
        if (OperatingSystem.isWindows()) {
            fname = fname + ".exe";
        }
        TOOL_JAVA = Path.of(System.getProperty("java.home"), "bin", fname).toString();
    }
}
