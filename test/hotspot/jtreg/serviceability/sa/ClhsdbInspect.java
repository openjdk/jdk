/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8192985
 * @summary Test the clhsdb 'inspect' command
 * @library /test/lib
 * @run main/othervm ClhsdbInspect
 */

public class ClhsdbInspect {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting the ClhsdbInspect test");

        LingeredAppWithLock theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = new LingeredAppWithLock();
            LingeredApp.startApp(null, theApp);
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            // Run the 'jstack -v' command to get the address of a Method*
            // and the oop address of a java.lang.ref.ReferenceQueue$Lock
            // object
            List<String> cmds = List.of("jstack -v");

            String jstackOutput = test.run(theApp.getPid(), cmds, null, null);

            if (jstackOutput == null) {
                // Output could be null due to attach permission issues
                // and if we are skipping this.
                LingeredApp.stopApp(theApp);
                return;
            }

            String addressString = null;
            Map<String, String> tokensMap = new HashMap<>();
            tokensMap.put("waiting to lock",
                          "instance of Oop for java/lang/Class");
            tokensMap.put("Method\\*=", "Type is Method");
            tokensMap.put("waiting to re-lock in wait",
                          "instance of Oop for java/lang/ref/ReferenceQueue$Lock");

            for (String key: tokensMap.keySet()) {
                cmds = new ArrayList<String>();
                Map<String, List<String>> expStrMap = new HashMap<>();

                String[] snippets = jstackOutput.split(key);
                String[] tokens = snippets[1].split(" ");
                for (String token: tokens) {
                    if (token.contains("0x")) {
                        addressString = token.replace("<", "").replace(">", "");
                        break;
                    }
                }

                String cmd = "inspect " + addressString;
                cmds.add(cmd);
                expStrMap.put(cmd, List.of(tokensMap.get(key)));
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
