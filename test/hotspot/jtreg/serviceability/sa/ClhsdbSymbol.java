/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;

/**
 * @test
 * @bug 8190198
 * @summary Test clhsdb symboldump command
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbSymbol
 */

public class ClhsdbSymbol {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbSymbol test");

        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            List<String> cmds = List.of("symboldump");

            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put("symboldump", List.of(
                    "java/lang/String", "java/util/HashMap",
                    "Ljava/io/InputStream", "LambdaMetafactory", "PerfCounter",
                    "isAnonymousClass", "JVMTI_THREAD_STATE_TERMINATED", "jdi",
                    "checkGetClassLoaderPermission", "lockCreationTime",
                    "stderrBuffer", "stdoutBuffer", "getProcess",
                    "LingeredApp"));

            test.run(theApp.getPid(), cmds, expStrMap, null);
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
        System.out.println("Test PASSED");
    }
}
