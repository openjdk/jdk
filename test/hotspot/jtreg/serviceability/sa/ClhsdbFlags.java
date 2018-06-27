/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8190198
 * @summary Test clhsdb flags command
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbFlags
 */

public class ClhsdbFlags {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbFlags test");

        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UnlockExperimentalVMOptions");
            vmArgs.add("-XX:+UnlockDiagnosticVMOptions");
            vmArgs.add("-XX:-MaxFDLimit");
            vmArgs.addAll(Utils.getVmOptions());
            theApp = LingeredApp.startApp(vmArgs);
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            List<String> cmds = List.of(
                    "flags", "flags -nd",
                    "flags UnlockDiagnosticVMOptions", "flags MaxFDLimit",
                    "flags MaxJavaStackTraceDepth");

            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put("flags", List.of(
                    "UnlockDiagnosticVMOptions = true",
                    "MaxFDLimit = false",
                    "MaxJavaStackTraceDepth = 1024",
                    "VerifyMergedCPBytecodes",
                    "ConcGCThreads", "UseThreadPriorities",
                    "ShowHiddenFrames"));
            expStrMap.put("flags -nd", List.of(
                    "UnlockDiagnosticVMOptions = true",
                    "MaxFDLimit = false",
                    "InitialHeapSize",
                    "MaxHeapSize"));
            expStrMap.put("flags UnlockDiagnosticVMOptions", List.of(
                    "UnlockDiagnosticVMOptions = true"));
            expStrMap.put("flags MaxFDLimit", List.of(
                    "MaxFDLimit = false"));
            expStrMap.put("flags MaxJavaStackTraceDepth", List.of(
                    "MaxJavaStackTraceDepth = 1024"));

            test.run(theApp.getPid(), cmds, expStrMap, null);
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
        System.out.println("Test PASSED");
    }
}
