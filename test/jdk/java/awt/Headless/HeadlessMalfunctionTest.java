/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.nio.file.Files;
import java.nio.file.Path;

/*
 * @test
 * @bug 8336382
 * @summary Test that in absence of isHeadless method, the JDK throws a meaningful error message.
 * @library /test/lib
 * @requires os.family == "linux"
 * @build HeadlessMalfunctionAgent
 * @run driver  jdk.test.lib.helpers.ClassFileInstaller
 *              HeadlessMalfunctionAgent
 *              HeadlessMalfunctionAgent$1
 * @run driver HeadlessMalfunctionTest
 */
public class HeadlessMalfunctionTest {

    public static void main(String[] args) throws Exception {
        // Package agent
        Files.writeString(Path.of("MANIFEST.MF"), "Premain-Class: HeadlessMalfunctionAgent\n");
        final ProcessBuilder pbJar = new ProcessBuilder()
                .command(JDKToolFinder.getJDKTool("jar"), "cmf", "MANIFEST.MF", "agent.jar",
                        "HeadlessMalfunctionAgent.class",
                        "HeadlessMalfunctionAgent$1.class");
        ProcessTools.executeProcess(pbJar).shouldHaveExitValue(0);

        // Run test
        final ProcessBuilder pbJava = ProcessTools.createTestJavaProcessBuilder(
                "-javaagent:agent.jar",
                "HeadlessMalfunctionTest$Runner"
        );
        final OutputAnalyzer output = ProcessTools.executeProcess(pbJava);
        // Unpatched JDK logs: "FATAL ERROR in native method: Could not allocate library name"
        // Patched should mention that isHeadless is missing, log message differs between OSes;
        // e.g. LWCToolkit toolkit path on MacOS and Win32GraphicsEnvironment code path on Windows
        // logs "java.lang.NoSuchMethodError: 'boolean java.awt.GraphicsEnvironment.isHeadless()'",
        // whereas Linux logs "FATAL ERROR in native method: GetStaticMethodID isHeadless failed"
        output.shouldContain("FATAL ERROR in native method: GetStaticMethodID isHeadless failed");
        output.shouldNotHaveExitValue(0);
    }

    public static class Runner {
        public static void main(String[] args) {
            System.out.println(java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds());
        }
    }
}
