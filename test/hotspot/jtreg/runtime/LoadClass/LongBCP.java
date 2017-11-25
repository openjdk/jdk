/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary JVM should be able to handle full path (directory path plus
 *          class name) or directory path longer than MAX_PATH specified
 *          in -Xbootclasspath/a on windows.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main LongBCP
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import jdk.test.lib.Platform;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class LongBCP {

    private static final int MAX_PATH = 260;

    public static void main(String args[]) throws Exception {
        Path sourceDir = Paths.get(System.getProperty("test.src"), "test-classes");
        Path classDir = Paths.get(System.getProperty("test.classes"));
        Path destDir = classDir;

        // create a sub-path so that the destDir length is almost MAX_PATH
        // so that the full path (with the class name) will exceed MAX_PATH
        int subDirLen = MAX_PATH - classDir.toString().length() - 2;
        if (subDirLen > 0) {
            char[] chars = new char[subDirLen];
            Arrays.fill(chars, 'x');
            String subPath = new String(chars);
            destDir = Paths.get(System.getProperty("test.classes"), subPath);
        }

        CompilerUtils.compile(sourceDir, destDir);

        String bootCP = "-Xbootclasspath/a:" + destDir.toString();
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            bootCP, "Hello");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Hello World")
              .shouldHaveExitValue(0);

        // increase the length of destDir to slightly over MAX_PATH
        destDir = Paths.get(destDir.toString(), "xxxxx");
        CompilerUtils.compile(sourceDir, destDir);

        bootCP = "-Xbootclasspath/a:" + destDir.toString();
        pb = ProcessTools.createJavaProcessBuilder(
            bootCP, "Hello");

        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Hello World")
              .shouldHaveExitValue(0);

        // relative path tests
        // We currently cannot handle relative path specified in the
        // -Xbootclasspath/a on windows.
        //
        // relative path length within the 256 limit
        char[] chars = new char[255];
        Arrays.fill(chars, 'y');
        String subPath = new String(chars);
        destDir = Paths.get(".", subPath);

        CompilerUtils.compile(sourceDir, destDir);

        bootCP = "-Xbootclasspath/a:" + destDir.toString();
        pb = ProcessTools.createJavaProcessBuilder(
            bootCP, "Hello");

        output = new OutputAnalyzer(pb.start());
        if (!Platform.isWindows()) {
            output.shouldContain("Hello World")
                  .shouldHaveExitValue(0);
        } else {
            output.shouldContain("Could not find or load main class Hello")
                  .shouldHaveExitValue(1);
        }

        // total relative path length exceeds MAX_PATH
        destDir = Paths.get(destDir.toString(), "yyyyyyyy");

        CompilerUtils.compile(sourceDir, destDir);

        bootCP = "-Xbootclasspath/a:" + destDir.toString();
        pb = ProcessTools.createJavaProcessBuilder(
            bootCP, "Hello");

        output = new OutputAnalyzer(pb.start());
        if (!Platform.isWindows()) {
            output.shouldContain("Hello World")
                  .shouldHaveExitValue(0);
        } else {
            output.shouldContain("Could not find or load main class Hello")
                  .shouldHaveExitValue(1);
        }
    }
}
