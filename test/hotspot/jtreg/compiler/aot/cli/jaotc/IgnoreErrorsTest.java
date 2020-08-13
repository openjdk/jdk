/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @compile IllegalClass.jasm
 * @run driver/timeout=360 compiler.aot.cli.jaotc.IgnoreErrorsTest
 */

package compiler.aot.cli.jaotc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

public class IgnoreErrorsTest {
    public static void main(String[] args) {
        try {
            Files.write(Paths.get("Empty.class"), new byte[] { }, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new Error("can't create empty class file", e);
        }
        File compiledLibrary = new File(JaotcTestHelper.DEFAULT_LIB_PATH);
        OutputAnalyzer oa;

        System.out.println("Compiling empty class file w/o --ignore-errors");
        oa = JaotcTestHelper.compileLibrary(
            "--class-name", "Empty",
            "--class-name", "java.lang.Object");
        oa.shouldNotHaveExitValue(0);
        Asserts.assertTrue(!compiledLibrary.exists(), "Compiled library file exists");

        System.out.println("Compiling empty class file w/ --ignore-errors");
        oa = JaotcTestHelper.compileLibrary(
            "--ignore-errors",
            "--class-name", "Empty",
            "--class-name", "java.lang.Object");
        oa.shouldHaveExitValue(0);
        Asserts.assertTrue(compiledLibrary.exists(), "Compiled library file is missed");
        JaotcTestHelper.checkLibraryUsage("-version");
        compiledLibrary.delete();

        System.out.println("Compiling illegal class file w/o --ignore-errors");
        oa = JaotcTestHelper.compileLibrary(
            "--class-name", "IllegalClass",
            "--class-name", "java.lang.Object");
        oa.shouldNotHaveExitValue(0);
        Asserts.assertTrue(!compiledLibrary.exists(), "Compiled library file exists");

        System.out.println("Compiling illegal class file w/ --ignore-errors");
        oa = JaotcTestHelper.compileLibrary(
            "--ignore-errors",
            "--class-name", "IllegalClass",
            "--class-name", "java.lang.Object");
        oa.shouldHaveExitValue(0);
        Asserts.assertTrue(compiledLibrary.exists(), "Compiled library file is missed");
        JaotcTestHelper.checkLibraryUsage("-version");
        compiledLibrary.delete();
    }
}
