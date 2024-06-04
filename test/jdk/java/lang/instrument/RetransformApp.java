/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.io.PrintStream;
import java.nio.file.Path;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;

/*
 * @test
 * @bug 6274264 6274241 5070281
 * @summary test retransformClasses
 *
 * @modules java.instrument
 * @library /test/lib
 * @build RetransformAgent asmlib.Instrumentor
 * @enablePreview
 * @comment The test uses asmlib/Instrumentor.java which relies on ClassFile API PreviewFeature.
 * @run driver/timeout=240 RetransformApp roleDriver
 * @comment The test uses a higher timeout to prevent test timeouts noted in JDK-6528548
 */
public class RetransformApp {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if (!"roleDriver".equals(args[0])) {
                throw new Exception("unexpected program argument: " + args[0]);
            }
            // launch the RetransformApp java process after creating the necessary
            // infrastructure
            System.out.println("creating agent jar");
            final Path agentJar = createAgentJar();
            System.out.println("launching app, with javaagent jar: " + agentJar);
            launchApp(agentJar);
        } else {
            System.err.println("running app");
            new RetransformApp().run(System.out);
        }
    }

    private static Path createAgentJar() throws Exception {
        Path agentJar = Path.of("RetransformAgent.jar");
        final String manifest = """
                Manifest-Version: 1.0
                Premain-Class: RetransformAgent
                Can-Retransform-Classes: true
                """;
        System.out.println("Manifest is:\n" + manifest);
        ClassFileInstaller.writeJar(agentJar.getFileName().toString(),
                ClassFileInstaller.Manifest.fromString(manifest),
                "RetransformAgent",
                "asmlib.Instrumentor");
        return agentJar;
    }

    private static void launchApp(final Path agentJar) throws Exception {
        final OutputAnalyzer oa = ProcessTools.executeTestJava(
                "--enable-preview", // due to usage of ClassFile API PreviewFeature in the agent
                "-javaagent:" + agentJar.toString(),
                RetransformApp.class.getName());
        oa.shouldHaveExitValue(0);
        // make available stdout/stderr in the logs, even in case of successful completion
        oa.reportDiagnosticSummary();
    }

    int foo(int x) {
        return x * x;
    }

    public void run(PrintStream out) throws Exception {
        out.println("start");
        for (int i = 0; i < 4; i++) {
            if (foo(3) != 9) {
                throw new Exception("ERROR: unexpected application behavior");
            }
        }
        out.println("undo");
        RetransformAgent.undo();
        for (int i = 0; i < 4; i++) {
            if (foo(3) != 9) {
                throw new Exception("ERROR: unexpected application behavior");
            }
        }
        out.println("end");
        if (RetransformAgent.succeeded()) {
            out.println("Instrumentation succeeded.");
        } else {
            throw new Exception("ERROR: Instrumentation failed.");
        }
    }
}
