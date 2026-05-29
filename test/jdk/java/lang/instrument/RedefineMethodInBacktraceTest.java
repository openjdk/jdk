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
 * @bug 7174978
 * @summary Redefine a class with a method stored in a backtrace.
 * @author Stefan Karlsson
 * @modules java.instrument
 *          jdk.management
 *          jdk.compiler
 * @library /test/lib
 * @build RedefineMethodInBacktraceAgent RedefineMethodInBacktraceTarget
 *        RedefineMethodInBacktraceApp
 * @run driver jdk.test.lib.util.JavaAgentBuilder RedefineMethodInBacktraceAgent RedefineMethodInBacktraceAgent.jar Can-Redefine-Classes:true
 * @run driver RedefineMethodInBacktraceTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.tools.ToolProvider;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RedefineMethodInBacktraceTest {
    public static void main(String[] args) throws Exception {
        String testSrc = System.getProperty("test.src");

        Files.copy(Path.of(testSrc, "RedefineMethodInBacktraceTarget_2.java"),
                Path.of("RedefineMethodInBacktraceTarget.java"),
                StandardCopyOption.REPLACE_EXISTING);
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", ".", "RedefineMethodInBacktraceTarget.java");
        if (rc != 0) throw new RuntimeException("Compilation failed: " + rc);

        Files.copy(Path.of(testSrc, "RedefineMethodInBacktraceTargetB_2.java"),
                Path.of("RedefineMethodInBacktraceTargetB.java"),
                StandardCopyOption.REPLACE_EXISTING);
        rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", ".", "RedefineMethodInBacktraceTargetB.java");
        if (rc != 0) throw new RuntimeException("Compilation failed: " + rc);

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-javaagent:RedefineMethodInBacktraceAgent.jar",
                "-XX:+AllowRedefinitionToAddDeleteMethods",
                "RedefineMethodInBacktraceApp");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldNotContain("Test failed");
        output.shouldHaveExitValue(0);
    }
}
