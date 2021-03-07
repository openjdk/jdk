/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.apps.LingeredApp;
import jtreg.SkippedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @test
 * @bug 8261095
 * @summary Test the clhsdb 'symbol' command on live process
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbSymbol
 */

public class ClhsdbSymbol {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting the ClhsdbSymbol test");
        LingeredApp theApp = null;

        try {
            List<String> cmds = null;
            String cmdStr = null;
            Map<String, List<String>> expStrMap = null;
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            //Use command "class java.lang.Thread" to get the address of the InstanceKlass for java.lang.Thread
            cmdStr = "class java.lang.Thread";
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of("java/lang/Thread"));
            String classOutput = test.run(theApp.getPid(), cmds, expStrMap, null);
            String threadAddress = null;
            String[] parts = classOutput.split("\n");

            //extract thread address from the output line similar to "java/lang/Thread @0x000000080001d940"
            for (String part : parts) {
                if (part.startsWith("java/lang/Thread")) {
                    String[] addresses = part.split(" @");
                    threadAddress = addresses[1];
                    break;
                }
            }
            if (threadAddress == null) {
                throw new RuntimeException("Cannot find address of the InstanceKlass for java.lang.Thread in output");
            }

            //Use "inspect" on the thread address we extracted in previous step
            cmdStr = "inspect " + threadAddress;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of("Symbol"));
            String inspectOutput = test.run(theApp.getPid(), cmds, expStrMap, null);
            String symbolAddress = null;
            parts = inspectOutput.split("\n");

            //extract address comes along with Symbol instance, following is corresponding sample output line
            //Symbol* Klass::_name: Symbol @ 0x0000000800471120
            for (String part : parts) {
                if (part.startsWith("Symbol")) {
                    String[] symbolParts = part.split("@ ");
                    symbolAddress = symbolParts[1];
                    break;
                }
            }
            if (symbolAddress == null) {
                throw new RuntimeException("Cannot find address with Symbol instance");
            }

            //Running "symbol" command on the Symbol instance address extracted in previous step
            cmdStr = "symbol " + symbolAddress;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of("#java/lang/Thread"));
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
