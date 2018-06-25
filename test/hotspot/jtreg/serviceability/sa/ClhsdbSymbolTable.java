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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import jdk.test.lib.apps.LingeredApp;

/**
 * @test
 * @bug 8191538
 * @summary Test the clhsdb 'symboltable' and 'symbol' commands
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbSymbolTable
 */

public class ClhsdbSymbolTable {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting the ClhsdbSymbolTable test");

        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            // Test the symboltable command
            List<String> cmds = List.of(
                "symboltable main",
                "symboltable java/lang/Class",
                "symboltable java/lang/Object",
                "symboltable java/lang/String",
                "symboltable java/util/List",
                "symboltable jdk/test/lib/apps/LingeredApp");

            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put("symboltable main", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            expStrMap.put("symboltable java/lang/Class", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            expStrMap.put("symboltable java/lang/Object", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            expStrMap.put("symboltable java/lang/String", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            expStrMap.put("symboltable java/util/List", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            expStrMap.put("symboltable jdk/test/lib/apps/LingeredApp", List.of(
                "sun.jvm.hotspot.oops.Symbol@"));
            String consolidatedOutput =
                test.run(theApp.getPid(), cmds, expStrMap, null);

            // Test the 'symbol' command passing in the address obtained from
            // the 'symboltable' command
            expStrMap = new HashMap<>();
            cmds = new ArrayList<String>();
            int expectedStringsIdx = 0;
            String expectedStrings[] = {"#main",
                                        "#java/lang/Class", "#java/lang/Object",
                                        "#java/lang/String", "#java/util/List",
                                        "#jdk/test/lib/apps/LingeredApp"};
            if (consolidatedOutput != null) {
                // Output could be null due to attach permission issues
                // and if we are skipping this.
                String[] singleCommandOutputs = consolidatedOutput.split("hsdb>");

                for (String singleCommandOutput : singleCommandOutputs) {
                    if (singleCommandOutput.contains("@")) {
                        String[] tokens = singleCommandOutput.split("@");
                        String addressString = tokens[1].replace("\n","");

                        // tokens[1] represents the address of the symbol
                        String cmd = "symbol " + addressString;
                        cmds.add(cmd);
                        expStrMap.put(cmd, List.of
                            (expectedStrings[expectedStringsIdx++]));
                    }
                }
                test.run(theApp.getPid(), cmds, expStrMap, null);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
        System.out.println("Test PASSED");
    }
}
