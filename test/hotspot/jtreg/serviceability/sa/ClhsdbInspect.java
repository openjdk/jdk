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

/**
 * @test
 * @bug 8192985
 * @summary Test the clhsdb 'inspect' command
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm/timeout=480 ClhsdbInspect
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import jdk.test.lib.apps.LingeredApp;
import jtreg.SkippedException;

public class ClhsdbInspect {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting the ClhsdbInspect test");

        LingeredAppWithLock theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = new LingeredAppWithLock();
            LingeredApp.startApp(theApp);
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            // Run the 'jstack -v' command to get the address of a Method*,
            // the oop address of a java.lang.ref.ReferenceQueue$Lock
            // and the oop address of a java.lang.Class object
            List<String> cmds = List.of("jstack -v");

            String jstackOutput = test.run(theApp.getPid(), cmds, null, null);

            Map<String, String> tokensMap = new HashMap<>();
            tokensMap.put("(a java.lang.Class for LingeredAppWithLock)",
                          "instance of Oop for java/lang/Class");
            tokensMap.put("Method*=", "Type is Method");
            tokensMap.put("(a java.lang.ref.ReferenceQueue$Lock)",
                          "instance of Oop for java/lang/ref/ReferenceQueue\\$Lock");

            String[] lines = jstackOutput.split("\\R");

            for (String key: tokensMap.keySet()) {
                cmds = new ArrayList<String>();
                Map<String, List<String>> expStrMap = new HashMap<>();

                String addressString = null;
                for (String line : lines) {
                    if (line.contains(key)) {
                        // Escape the token "Method*=" because the split method uses
                        // a regex, not just a straight String.
                        String escapedKey = key.replace("*","\\*");
                        String[] words = line.split(escapedKey+"|[ ]");
                        for (String word : words) {
                            word = word.replace("<","").replace(">","");
                            if (word.startsWith("0x")) {
                                addressString = word;
                                break;
                            }
                        }
                        if (addressString != null)
                            break;
                      }
                  }

                String cmd = "inspect " + addressString;
                cmds.add(cmd);
                expStrMap.put(cmd, List.of(tokensMap.get(key)));
                test.run(theApp.getPid(), cmds, expStrMap, null);
            }
        } catch (SkippedException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
        System.out.println("Test PASSED");
    }
}
