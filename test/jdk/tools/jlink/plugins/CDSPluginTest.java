/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.*;

import tests.Helper;

import jtreg.SkippedException;

/* @test
 * @bug 8264322
 * @summary Test the --generate-cds-archive plugin
 * @requires vm.cds
 * @library ../../lib
 * @library /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main CDSPluginTest
 */

public class CDSPluginTest {

    public static void main(String[] args) throws Throwable {

        if (!Platform.isDefaultCDSArchiveSupported())
            throw new SkippedException("not a supported platform");

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        var module = "cds";
        helper.generateDefaultJModule(module);
        Path image = helper.generateDefaultImage(new String[] { "--generate-cds-archive" },
                                                module)
            .assertSuccess();

        String subDir;
        String sep = File.separator;
        if (Platform.isWindows()) {
            subDir = "bin" + sep;
        } else {
            subDir = "lib" + sep;
        }
        subDir += "server" + sep;

        if (Platform.isAArch64() || Platform.isX64()) {
            helper.checkImage(image, module, null, null,
                      new String[] { subDir + "classes.jsa", subDir + "classes_nocoops.jsa" });
        } else {
            helper.checkImage(image, module, null, null,
                      new String[] { subDir + "classes.jsa" });
        }

       // Simulate different platforms between current runtime and target image.
       if (Platform.isLinux()) {
           System.out.println("---- Test different platforms scenario ----");
           String jlinkPath = JDKToolFinder.getJDKTool("jlink");
           // copy over the packaged modules java.base and java.logging to a temporary directory
           // which we then use as a --module-path during jlink image generation. using such a
           // separate --module-path will force the JLink task to read the ModuleTarget from
           // the java.base module-info.class to identify the target platform for the image
           // being generated.
           Path jdkRoot = Path.of(System.getProperty("test.jdk"));
           Path modsPath = Files.createDirectory(Path.of("mods"));
           copyPackagedModules(jdkRoot, modsPath, new String[] {"java.base", "java.logging"});
           String[] cmd = {jlinkPath, "--verbose",
                           "--add-modules", "java.base,java.logging",
                           // java.base in a custom module path will ensure the ModuleTarget
                           // attribute in module-info.class is parsed and target platform is
                           // inferred as a linux-*
                           "--module-path", modsPath.toString(),
                           "-J-Dos.name=windows", // simulate current platform as windows
                           // enable the CDSPlugin, which, during the processing is then expected
                           // to throw an exception because of current and target platform mismatch
                           "--generate-cds-archive",
                           "--output", System.getProperty("test.classes") + sep + module + "-tmp"};
           StringBuilder cmdLine = new StringBuilder();
           for (String s : cmd) {
               cmdLine.append(s).append(' ');
           }
           System.out.println("Command line: [" + cmdLine.toString() + "]");
           ProcessBuilder pb = new ProcessBuilder(cmd);
           OutputAnalyzer out = new OutputAnalyzer(pb.start());
           System.out.println("    stdout: " + out.getStdout());
           out.shouldMatch("Error: Cannot generate CDS archives: target image platform linux-.*is different from runtime platform windows-.*");
           out.shouldHaveExitValue(1);
       }
    }

    private static void copyPackagedModules(Path jdkRoot, Path targetDir, String[] modules)
            throws IOException {
        for (String module : modules) {
            Path moduleFile = jdkRoot.resolve("jmods").resolve(module + ".jmod");
            if (!Files.exists(moduleFile)) {
                throw new AssertionError("Missing " + moduleFile);
            }
            Path copy = targetDir.resolve(moduleFile.getFileName());
            Files.copy(moduleFile, copy);
        }
    }
}
