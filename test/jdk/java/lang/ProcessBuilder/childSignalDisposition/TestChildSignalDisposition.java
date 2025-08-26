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

/**
 * @test id=posix_spawn
 * @bug 8364611
 * @summary Check that childs start with SIG_DFL as SIGPIPE disposition
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=posix_spawn -agentlib:ChangeSignalDisposition TestChildSignalDisposition
 */

/**
 * @test id=fork
 * @bug 8364611
 * @summary Check that childs start with SIG_DFL as SIGPIPE disposition
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=fork -agentlib:ChangeSignalDisposition TestChildSignalDisposition
 */

/**
 * @test id=vfork
 * @bug 8364611
 * @summary Check that childs start with SIG_DFL as SIGPIPE disposition
 * @requires os.family == "linux"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=vfork -agentlib:ChangeSignalDisposition TestChildSignalDisposition
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
public class TestChildSignalDisposition {
    // This test has two native parts:
    // - a library injected into the JVM with -agentlib changes signal disposition of the VM process for SIGPIPE to
    //   SIG_IGN
    // - a small native executable that prints out, in its main function, all signal handler dispositions, to be executed
    //   as a child process.
    //
    // What should happen: In child process, SIGPIPE should be set to default.
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createNativeTestProcessBuilder("PrintSignalDisposition");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
        output.shouldNotMatch("SIGPIPE: +ignore");
        output.shouldNotMatch("SIGPIPE: +block");
        output.shouldMatch("SIGPIPE: +default");
        output.reportDiagnosticSummary();
    }
}
