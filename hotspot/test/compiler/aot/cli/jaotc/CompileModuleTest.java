/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library / /test/lib /testlibrary
 * @modules java.base/jdk.internal.misc
 * @requires vm.bits == "64" & os.arch == "amd64" & os.family == "linux"
 * @run driver compiler.aot.cli.jaotc.CompileModuleTest
 * @summary check jaotc can compile module
 */

package compiler.aot.cli.jaotc;

import compiler.aot.cli.jaotc.data.HelloWorldTwo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

public class CompileModuleTest {
    private static final String TESTED_CLASS_NAME = HelloWorldTwo.class.getName();
    private static final String STRING_LENGTH = String.class.getName() + ".length";
    private static final String COMPILE_COMMAND = "compileOnly " + STRING_LENGTH + ".*";
    private static final Path COMPILE_COMMAND_FILE = Paths.get("stringLengthOnly.list");
    private static final String[] EXPECTED = new String[]{
        JaotcTestHelper.DEFAULT_LIBRARY_LOAD_MESSAGE,
        STRING_LENGTH
    };
    private static final String[] UNEXPECTED = new String[]{
        TESTED_CLASS_NAME
    };

    public static void main(String[] args) {
        // compile only java.lang.String::length from java.base module to have reasonable compilation time
        try {
            Files.write(COMPILE_COMMAND_FILE, Arrays.asList(COMPILE_COMMAND),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new Error("TESTBUG: can't write list file " + e, e);
        }
        OutputAnalyzer oa = JaotcTestHelper.compileLibrary("--compile-commands",
                COMPILE_COMMAND_FILE.toString(), "--module", "java.base");
        oa.shouldHaveExitValue(0);
        File compiledLibrary = new File(JaotcTestHelper.DEFAULT_LIB_PATH);
        Asserts.assertTrue(compiledLibrary.exists(), "Compiled library file missing");
        Asserts.assertGT(compiledLibrary.length(), 0L, "Unexpected compiled library size");
        JaotcTestHelper.checkLibraryUsage(TESTED_CLASS_NAME, EXPECTED, UNEXPECTED);
    }
}
