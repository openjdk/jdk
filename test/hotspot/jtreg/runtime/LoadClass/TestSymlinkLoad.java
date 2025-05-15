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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;

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

        // try to load a class itself directly, i.e. not via a symlink
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                bootCP, className);

        // make sure it runs as expected
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Hello World")
            .shouldHaveExitValue(0);

        // create a symlink to the classfile in a subdir with a given name
        Path classFile = Path.of(destDir + File.separator + className + ".class");
        final String subdir = "remote";
        final String pathToFolderForSymlink = destDir + File.separator + subdir + File.separator;
        createLinkInSeparateFolder(pathToFolderForSymlink, classFile, className);

        // try to load class via its symlink, which is in a different directory
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                bootCP + File.separator + subdir, className);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Hello World")
            .shouldHaveExitValue(0);

        // remove the subdir
        FileUtils.deleteFileTreeWithRetry(Path.of(pathToFolderForSymlink));
    }

    public static void createLinkInSeparateFolder(final String pathToFolderForSymlink, final Path target, final String className) throws IOException {
        File theDir = new File(pathToFolderForSymlink);
        if (!theDir.exists()) {
            theDir.mkdirs();
        }
        Path link = Paths.get(pathToFolderForSymlink, className + ".class");
        if (Files.exists(link)) {
            Files.delete(link);
        }
        Files.createSymbolicLink(link, target);
    }

}
