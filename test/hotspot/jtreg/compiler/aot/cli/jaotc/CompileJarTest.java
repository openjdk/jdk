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
 * @build compiler.aot.cli.jaotc.CompileJarTest
 * @run driver ClassFileInstaller compiler.aot.cli.jaotc.data.HelloWorldOne
 *                                compiler.aot.cli.jaotc.data.HelloWorldTwo
 * @run driver compiler.aot.cli.jaotc.CompileJarTest
 * @summary check jaotc can compile jar
 */

package compiler.aot.cli.jaotc;

import compiler.aot.cli.jaotc.data.HelloWorldOne;
import compiler.aot.cli.jaotc.data.HelloWorldTwo;
import java.io.File;
import java.io.IOException;
import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;

public class CompileJarTest {
    private static final String JAR_NAME = "test.jar";

    public static void main(String[] args) {
        createJar();
        OutputAnalyzer oa = JaotcTestHelper.compileLibrary("--jar", JAR_NAME);
        oa.shouldHaveExitValue(0);
        File compiledLibrary = new File(JaotcTestHelper.DEFAULT_LIB_PATH);
        Asserts.assertTrue(compiledLibrary.exists(), "Compiled library file missing");
        Asserts.assertGT(compiledLibrary.length(), 0L, "Unexpected compiled library size");
        JaotcTestHelper.checkLibraryUsage(HelloWorldOne.class.getName());
        JaotcTestHelper.checkLibraryUsage(HelloWorldTwo.class.getName());
    }

    private static void createJar() {
        JDKToolLauncher jar = JDKToolLauncher.create("jar")
                .addToolArg("-cf")
                .addToolArg(JAR_NAME)
                .addToolArg("-C")
                .addToolArg(".")
                .addToolArg(".");
        OutputAnalyzer oa;
        try {
            oa = new OutputAnalyzer(new ProcessBuilder(jar.getCommand()).start());
        } catch (IOException e) {
            throw new Error("Problems launching jar: " + e, e);
        }
        oa.shouldHaveExitValue(0);
    }
}
