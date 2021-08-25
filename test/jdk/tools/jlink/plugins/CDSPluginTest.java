/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.*;

import tests.Helper;

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

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        var module = "cds";
        helper.generateDefaultJModule(module);
        var image = helper.generateDefaultImage(new String[] { "--generate-cds-archive" },
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
        helper.checkImage(image, module, null, null,
                          new String[] { subDir + "classes.jsa", subDir + "classes_nocoops.jsa" });

       // Simulate different platforms between current runtime and target image.
       if (Platform.isLinux()) {
           System.out.println("---- Test different platforms scenario ----");
           String jlinkPath = JDKToolFinder.getJDKTool("jlink");
           String[] cmd = {jlinkPath, "--add-modules", "java.base,java.logging",
                           "-J-Dos.name=windows", "--generate-cds-archive",
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
}
