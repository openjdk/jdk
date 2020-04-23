/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import jtreg.SkippedException;

/**
 * @test
 * @bug 8190198
 * @summary Test clhsdb pstack command
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbPstack
 */

public class ClhsdbPstack {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbPstack test");

        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            List<String> cmds = List.of("pstack -v");

            Map<String, List<String>> expStrMap = new HashMap<>();
            if (Platform.isOSX()) {
                expStrMap.put("pstack -v", List.of(
                    "Not available on Mac OS X"));
            } else {
                expStrMap.put("pstack -v", List.of(
                    "No deadlocks found", "Common-Cleaner",
                    "Signal Dispatcher", "CompilerThread",
                    "Sweeper thread", "Service Thread",
                    "Reference Handler", "Finalizer", "main"));
            }

            test.run(theApp.getPid(), cmds, expStrMap, null);
        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
        System.out.println("Test PASSED");
    }
}
