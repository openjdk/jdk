/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

/**
 * @test
 * @bug 8386291
 * @summary Test clhsdb pstack command when a thread is stopped in a native library without .eh_frame
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @requires (os.family == "linux")
 * @requires (os.arch == "aarch64")
 * @library /test/lib
 * @run main/othervm ClhsdbPstackNoEhFrame
 */
public class ClhsdbPstackNoEhFrame {

    public static void main(String[] args) throws Exception {
        if (Platform.isMusl()) {
            throw new SkippedException("This test does not work on musl libc.");
        }
        System.out.println("Starting ClhsdbPstackNoEhFrame test");

        LingeredAppWithNoEhFrame theApp = null;
        try {
            // Needed for LingeredApp to be able to resolve native library.
            String libPath = System.getProperty("java.library.path");
            String[] vmArgs;
            if (libPath == null) {
                vmArgs = Utils.prependTestJavaOpts("-Dtest.timeout.factor=" + Utils.TIMEOUT_FACTOR);
            } else {
                vmArgs = Utils.prependTestJavaOpts("-Djava.library.path=" + libPath,
                    "-Dtest.timeout.factor=" + Utils.TIMEOUT_FACTOR);
            }

            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = new LingeredAppWithNoEhFrame();
            LingeredApp.startAppExactJvmOpts(theApp, vmArgs);
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            List<String> cmds = List.of("pstack -v");

            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put("pstack -v", List.of("main", LingeredAppWithNoEhFrame.THREAD_NAME));
            String output = test.run(theApp.getPid(), cmds, expStrMap, null);
            String targetThread = output.lines()
                .dropWhile(line -> !line.contains(LingeredAppWithNoEhFrame.THREAD_NAME))
                .takeWhile(line -> !line.contains("-------"))
                .collect(Collectors.joining(System.lineSeparator()));
            OutputAnalyzer out = new OutputAnalyzer(targetThread);
            out.shouldContain("Java_LingeredAppWithNoEhFrame_waitInNative");

        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
