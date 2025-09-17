/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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

/**
 * @test
 * @bug 8366118
 * @summary Check that a huge method is not compiled under -XX:+DontCompileHugeMethods.
 * @library /test/lib
 * @run main compiler.runtime.TestDontCompileHugeMethods
 */
package compiler.runtime;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestDontCompileHugeMethods {

    private static final String HUGE_SWITCH_CLASS_NAME = "HugeSwitch";

    private static void generateClass(Writer writer) throws IOException {
        writer.write("""
            public class HugeSwitch {
              private static int hugeSwitch(int x) {
                switch (x) {
            """);
        for (int i = 0; i < 2000; i++) {
            writer.write("      case " + i + ": return " + i + " + 1;\n");
        }
        writer.write("""
                  default:
                    return 0;
                }
              }
              private static int shortMethod(int x) {
                if (x % 3 == 0) {
                  return x - 1;
                }
                return x + 1;
              }
              public static void main(String[] args) {
                int val = 0;
                for (int i = 0; i < 100000; i++) {
                  val += hugeSwitch(shortMethod(i));
                }
                System.out.println(val);
              }
            }
            """);
    }

    private static void compileClass(Path workDir, Path sourceFile) throws Exception {
        JDKToolLauncher javac = JDKToolLauncher.create("javac").addToolArg("-d")
            .addToolArg(workDir.toAbsolutePath().toString()).addToolArg("-cp")
            .addToolArg(Utils.TEST_CLASS_PATH).addToolArg(sourceFile.toAbsolutePath().toString());

        OutputAnalyzer output = ProcessTools.executeProcess(javac.getCommand());
        output.shouldHaveExitValue(0);
    }

    private static void generateAndCompileClass(Path workDir) throws Exception {
        Path sourceFile = workDir.resolve(HUGE_SWITCH_CLASS_NAME + ".java");
        try (Writer writer = Files.newBufferedWriter(sourceFile)) {
            generateClass(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        compileClass(workDir, sourceFile);
    }

    private static void runTest(Path workDir, List jvmArgs) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("-XX:+PrintCompilation");
        command.add("-Xbatch");
        command.addAll(jvmArgs);
        command.add("-cp");
        command.add(workDir.toAbsolutePath().toString());
        command.add(HUGE_SWITCH_CLASS_NAME);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("   HugeSwitch::shortMethod (");
        analyzer.shouldNotContain("   HugeSwitch::hugeSwitch (");
    }

    public static void main(String[] args) throws Exception {
        Path workDir = Paths.get("");
        generateAndCompileClass(workDir);

        runTest(workDir, List.of());
        runTest(workDir, List.of("-XX:-TieredCompilation"));
    }
}
