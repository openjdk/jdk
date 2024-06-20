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

/*
 * @test
 * @bug 8304400
 * @summary Test source root directory computation
 * @modules jdk.compiler/com.sun.tools.javac.launcher
 * @run junit ProgramDescriptorTests
 */

import com.sun.tools.javac.launcher.ProgramDescriptor;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ProgramDescriptorTests {
    @ParameterizedTest
    @CsvSource(textBlock =
            """
            '/', '/Program.java', ''
            '/', '/a/Program.java', 'a',
            '/', '/a/b/Program.java', 'a.b',
            '/', '/a/b/c/Program.java', 'a.b.c'

            '/a', '/a/b/c/Program.java', 'b.c'
            '/a/b', '/a/b/c/Program.java', 'c'
            '/a/b/c', '/a/b/c/Program.java', ''
            """)
    @DisabledOnOs(OS.WINDOWS)
    void checkComputeSourceRootPath(Path expected, Path program, String packageName) {
        check(expected, program, packageName);
    }

    @ParameterizedTest
    @CsvSource(textBlock =
            """
            'C:\\', 'C:\\Program.java', ''
            'C:\\', 'C:\\a\\Program.java', 'a',
            'C:\\', 'C:\\a\\b\\Program.java', 'a.b',
            'C:\\', 'C:\\a\\b\\c\\Program.java', 'a.b.c'

            'C:\\a', 'C:\\a\\b\\c\\Program.java', 'b.c'
            'C:\\a\\b', 'C:\\a\\b\\c\\Program.java', 'c'
            'C:\\a\\b\\c', 'C:\\a\\b\\c\\Program.java', ''
            """)
    @EnabledOnOs(OS.WINDOWS)
    void checkComputeSourceRootPathOnWindows(Path expected, Path program, String packageName) {
        check(expected, program, packageName);
    }

    private void check(Path expectedRoot, Path programPath, String packageName) {
        assertTrue(expectedRoot.isAbsolute(), "Expected path not absolute: " + expectedRoot);
        assertTrue(programPath.isAbsolute(), "Program path not absolute: " + programPath);

        var actual = ProgramDescriptor.computeSourceRootPath(programPath, packageName);
        assertEquals(expectedRoot, actual);
    }
}
