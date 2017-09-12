/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.aot
 * @library / /test/lib /testlibrary
 * @modules java.base/jdk.internal.misc
 * @build compiler.aot.cli.jaotc.ListOptionTest
 * @run driver ClassFileInstaller compiler.aot.cli.jaotc.data.HelloWorldOne
 * @run driver compiler.aot.cli.jaotc.ListOptionTest
 * @summary check jaotc can use --compile-commands option successfully and respective compileCommand is applied
 */

package compiler.aot.cli.jaotc;

import compiler.aot.cli.jaotc.data.HelloWorldOne;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

public class ListOptionTest {
    private static final String TESTED_CLASS_NAME = HelloWorldOne.class.getName();
    private static final String HELLOWORLDONE_MAIN = TESTED_CLASS_NAME + ".main";
    private static final String COMPILE_COMMAND = "compileOnly " + HELLOWORLDONE_MAIN + ".*";
    private static final Path COMPILE_COMMAND_FILE = Paths.get("helloWorldMainMethodOnly.list");
    private static final String[] EXPECTED = new String[]{
        JaotcTestHelper.DEFAULT_LIBRARY_LOAD_MESSAGE,
        TESTED_CLASS_NAME + ".main"
    };
    private static final String[] UNEXPECTED = new String[]{
        TESTED_CLASS_NAME + ".<init>"
    };

    public static void main(String[] args) {
        try {
            Files.write(COMPILE_COMMAND_FILE, Arrays.asList(COMPILE_COMMAND),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new Error("TESTBUG: can't write list file " + e, e);
        }
        OutputAnalyzer oa = JaotcTestHelper.compileLibrary("--compile-commands", COMPILE_COMMAND_FILE.toString(),
                "--class-name", JaotcTestHelper.getClassAotCompilationName(HelloWorldOne.class));
        oa.shouldHaveExitValue(0);
        File compiledLibrary = new File(JaotcTestHelper.DEFAULT_LIB_PATH);
        Asserts.assertTrue(compiledLibrary.exists(), "Compiled library file missing");
        Asserts.assertGT(compiledLibrary.length(), 0L, "Unexpected compiled library size");
        JaotcTestHelper.checkLibraryUsage(TESTED_CLASS_NAME, EXPECTED, UNEXPECTED);
    }
}
