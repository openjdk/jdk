/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8334433
 * @summary Verify that when running JShell on platforms that support FFMTerminalProvider,
 *          no new processes are spawned.
 * @requires os.family == "windows" | os.family == "mac" | os.family == "linux"
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavaTask TerminalNoExecTest
 * @run main TerminalNoExecTest
 */

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.consumer.RecordingStream;
import jdk.jshell.tool.JavaShellToolBuilder;

import toolbox.ToolBox;

public class TerminalNoExecTest {

    public static void main(String... args) throws Exception {
        if (args.length > 0) {
            AtomicBoolean spawnedNewProcess = new AtomicBoolean();
            try (var rs = new RecordingStream()) {
                rs.enable("jdk.ProcessStart").withoutThreshold();
                rs.onEvent(evt -> {
                    System.err.println("evt: " + evt);
                    spawnedNewProcess.set(true);
                });
                rs.startAsync();
                JavaShellToolBuilder.builder().run("--execution=local", "--no-startup");
                rs.stop();
            }
            if (spawnedNewProcess.get()) {
                System.err.println("Spawned a new process!");
                System.exit(1);
            }
            System.exit(0);
        } else {
            Path testScript = Paths.get("do-exit");
            try (Writer w = Files.newBufferedWriter(testScript)) {
                w.append("/exit\n");
            }

            ToolBox tb = new ToolBox();
            Process target =
                new ProcessBuilder(tb.getJDKTool("java").toString(),
                                   "-classpath", System.getProperty("java.class.path"),
                                   TerminalNoExecTest.class.getName(),
                                   "run-test")
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectInput(testScript.toFile())
                        .start();

            target.waitFor();

            int exitCode = target.exitValue();

            if (exitCode != 0) {
                throw new AssertionError("Incorrect exit value, expected 0, got: " + exitCode);
            }
        }
    }

}
