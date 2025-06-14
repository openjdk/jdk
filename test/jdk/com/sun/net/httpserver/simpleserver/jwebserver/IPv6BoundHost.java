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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jdk.internal.util.OperatingSystem;
import jdk.test.lib.net.IPSupport;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8332020
 * @summary verifies that when jwebserver is launched with a IPv6 bind address
 *          then the URL printed contains the correct host literal
 * @modules jdk.httpserver java.base/jdk.internal.util
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @run driver IPv6BoundHost
 */
public class IPv6BoundHost {

    private static final Path JDK_BIN_DIR = Path.of(System.getProperty("java.home")).resolve("bin");
    private static final Path JWEBSERVER_BINARY = OperatingSystem.isWindows()
            ? JDK_BIN_DIR.resolve("jwebserver.exe") : JDK_BIN_DIR.resolve("jwebserver");
    private static final String LOCALE_OPT = "-J-Duser.language=en -J-Duser.country=US";

    public static void main(final String[] args) throws Exception {
        IPSupport.printPlatformSupport(System.err); // for debug purposes
        if (!IPSupport.hasIPv6()) {
            throw new SkippedException("Skipping test - IPv6 is not supported");
        }
        final String output = launchJwebserverAndExit(List.of("-b", "::1", "-p", "0"));
        if (output.contains("URL http://[::1]:")
                || output.contains("URL http://[0:0:0:0:0:0:0:1]:")) {
            // found expected content
            System.out.println("found expected URL in jwebserver output");
        } else {
            throw new AssertionError("missing IPv6 address in jwebserver process output");
        }
    }

    private static String launchJwebserverAndExit(final List<String> args) throws Exception {
        final Predicate<String> waitForLine = (s) -> s.startsWith("URL http://");
        final StringBuilder sb = new StringBuilder();  // stdout & stderr
        final List<String> cmd = new ArrayList<>();
        cmd.add(JWEBSERVER_BINARY.toString());
        cmd.add(LOCALE_OPT);
        cmd.addAll(args);
        // start the process and await the waitForLine before returning
        final Process p = ProcessTools.startProcess("8332020-test", new ProcessBuilder(cmd),
                line -> sb.append(line).append("\n"),
                waitForLine,
                30,  // suitably high default timeout, not expected to timeout
                TimeUnit.SECONDS);
        System.out.println(sb.toString()); // print the process' stdout/stderr
        // the process has started and it is confirmed that the process output has the line
        // we were waiting for. now kill the process.
        p.destroy();
        final int exitCode = p.waitFor();
        if (!isNormalExitCode(exitCode)) {
            throw new AssertionError("jwebserver exited with unexpected exit code: " + exitCode);
        }
        return sb.toString();
    }

    private static boolean isNormalExitCode(final int exitCode) {
        final int SIGTERM = 15;
        if (OperatingSystem.isWindows()) {
            return exitCode == 1; // we expect exit code == 1 on Windows for Process.destroy()
        } else {
            // signal terminated exit code on Unix is 128 + signal value
            return exitCode == (128 + SIGTERM);
        }
    }
}
