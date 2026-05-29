/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7182152 8007935
 * @summary Redefine a subclass that implements two interfaces and
 *   verify that the right methods are called.
 * @author Daniel D. Daugherty
 * @modules jdk.compiler
 * @library /test/lib
 * @build RedefineSubclassWithTwoInterfacesAgent RedefineSubclassWithTwoInterfacesApp
 * @run driver jdk.test.lib.util.JavaAgentBuilder RedefineSubclassWithTwoInterfacesAgent RedefineSubclassWithTwoInterfacesAgent.jar Can-Redefine-Classes:true
 * @run driver RedefineSubclassWithTwoInterfacesTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.tools.ToolProvider;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RedefineSubclassWithTwoInterfacesTest {
    public static void main(String[] args) throws Exception {
        String testSrc = System.getProperty("test.src");
        String testClasses = System.getProperty("test.classes");

        Files.copy(Path.of(testSrc, "RedefineSubclassWithTwoInterfacesTarget_1.java"),
                Path.of("RedefineSubclassWithTwoInterfacesTarget.java"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of(testSrc, "RedefineSubclassWithTwoInterfacesImpl_1.java"),
                Path.of("RedefineSubclassWithTwoInterfacesImpl.java"),
                StandardCopyOption.REPLACE_EXISTING);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-cp", testClasses, "-d", ".",
                "RedefineSubclassWithTwoInterfacesTarget.java",
                "RedefineSubclassWithTwoInterfacesImpl.java");
        if (rc != 0) throw new RuntimeException("Compilation failed: " + rc);

        Files.move(Path.of("RedefineSubclassWithTwoInterfacesTarget.class"),
                Path.of("RedefineSubclassWithTwoInterfacesTarget_1.class"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(Path.of("RedefineSubclassWithTwoInterfacesImpl.class"),
                Path.of("RedefineSubclassWithTwoInterfacesImpl_1.class"),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-Xlog:redefine+class+load=trace,redefine+class+load+exceptions=trace,redefine+class+timer=trace,redefine+class+obsolete=trace,redefine+class+obsolete+metadata=trace,redefine+class+constantpool=trace",
                "-javaagent:RedefineSubclassWithTwoInterfacesAgent.jar",
                "RedefineSubclassWithTwoInterfacesApp");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldNotContain("guarantee");
        output.shouldHaveExitValue(0);

        int v0Count = 0;
        for (String line : output.getStdout().split("\n")) {
            if (line.contains("before any redefines") && line.contains("version-0")) v0Count++;
        }
        if (v0Count != 2) throw new RuntimeException("Expected 2 version-0 'before any redefines' messages, found " + v0Count);

        int v1Count = 0;
        for (String line : output.getStdout().split("\n")) {
            if (line.contains("after redefine") && line.contains("version-1")) v1Count++;
        }
        if (v1Count != 2) throw new RuntimeException("Expected 2 version-1 'after redefine' messages, found " + v1Count);
    }
}
