/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary JVM should be able to handle loading class via symlink on windows
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @run driver TestSymlinkLoad
 */

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestSymlinkLoad {
    public static void main(String args[]) throws Exception {
        Path sourceDir = Paths.get(System.getProperty("test.src"), "test-classes");
        Path classDir = Paths.get(System.getProperty("test.classes"));
        Path destDir = classDir;

        String subPath = "compiled";
        destDir = Paths.get(System.getProperty("test.classes"), subPath);

        CompilerUtils.compile(sourceDir, destDir);

        String bootCP = "-Xbootclasspath/a:" + destDir.toString();

        String className = "Hello";
        /*
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                bootCP, "-XX:+PauseAtStartup", className);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Hello World")
                .shouldHaveExitValue(0);*/

        Path classFile = Path.of(destDir + File.separator + className + ".class");

        String classLinkName = "Hello_link";
        Path link = createLink(destDir, classFile, classLinkName);


        // try to load class via its symlink
        ProcessBuilder pb2 = ProcessTools.createLimitedTestJavaProcessBuilder(
                bootCP, "-XX:+PauseAtStartup", classLinkName);
        OutputAnalyzer output2 = new OutputAnalyzer(pb2.start());
        output2.shouldHaveExitValue(0);



        int n = 1;
    }

    public static Path createLink(final Path destDir, final Path target, final String classLinkName) throws IOException {
        Path link = Paths.get(destDir + File.separator, classLinkName + ".class");
        if (Files.exists(link)) {
            Files.delete(link);
        }
        Files.createSymbolicLink(link, target);
        return link;
    }

}
