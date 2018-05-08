/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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


package nsk.jdi.TypeComponent.isSynthetic;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

public class issynthetic002 {
    final static String METHOD_NAME[] = {
        "<init>",
        "Mv",
        "Mz", "Mz1", "Mz2",
        "Mb", "Mb1", "Mb2",
        "Mc", "Mc1", "Mc2",
        "Md", "Md1", "Md2",
        "Mf", "Mf1", "Mf2",
        "Mi", "Mi1", "Mi2",
        "Ml", "Ml1", "Ml2",
        "Mr", "Mr1", "Mr2",

        "MvF", "MlF", "MlF1", "MlF2",
        "MvN", "MlN", "MlN1", "MlN2",
        "MvS", "MlS", "MlS1", "MlS2",
        "MvI", "MlI", "MlI1", "MlI2",
        "MvY", "MlY", "MlY1", "MlY2",
        "MvU", "MlU", "MlU1", "MlU2",
        "MvR", "MlR", "MlR1", "MlR2",
        "MvP", "MlP", "MlP1", "MlP2",

        "MX", "MX1", "MX2",
        "MO", "MO1", "MO2",

        "MLF", "MLF1", "MLF2",
        "MLN", "MLN1", "MLN2",
        "MLS", "MLS1", "MLS2",
        "MLI", "MLI1", "MLI2",
        "MLY", "MLY1", "MLY2",
        "MLU", "MLU1", "MLU2",
        "MLR", "MLR1", "MLR2",
        "MLP", "MLP1", "MLP2",

        "ME", "ME1", "ME2",
        "MEF", "MEF1", "MEF2",
        "MEN", "MEN1", "MEN2",
        "MES", "ME1S", "ME2S",
        "MEI", "MEI1", "MEI2",
        "MEY", "MEY1", "MEY2",
        "MEU", "MEU1", "MEU2",
        "MER", "MER1", "MER2",
        "MEP", "MEP1", "MEP2"
    };

    private static Log log;
    private final static String prefix = "nsk.jdi.TypeComponent.isSynthetic.";
    private final static String className = "issynthetic002";
    private final static String debugerName = prefix + className;
    private final static String debugeeName = debugerName + "a";
    private final static String classToCheckName = prefix + "issynthetic002aClassToCheck";

    public static void main(String argv[]) {
        System.exit(95 + run(argv, System.out));
    }

    public static int run(String argv[], PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        Binder binder = new Binder(argHandler, log);
        Debugee debugee = binder.bindToDebugee(debugeeName
                              + (argHandler.verbose() ? " -verbose" : ""));
        VirtualMachine vm = debugee.VM();
        boolean canGetSynthetic = vm.canGetSyntheticAttribute();
        IOPipe pipe = new IOPipe(debugee);
        boolean testFailed = false;
        List methods;
        int totalSyntheticMethods = 0;

        log.display("debuger> Value of canGetSyntheticAttribute in current "
                  + "VM is " + canGetSynthetic);

        // Connect with debugee and resume it
        debugee.redirectStderr(out);
        debugee.resume();
        String line = pipe.readln();
        if (line == null) {
            log.complain("debuger FAILURE> UNEXPECTED debugee's signal - null");
            return 2;
        }
        if (!line.equals("ready")) {
            log.complain("debuger FAILURE> UNEXPECTED debugee's signal - "
                      + line);
            return 2;
        }
        else {
            log.display("debuger> debugee's \"ready\" signal recieved.");
        }

        ReferenceType refType = debugee.classByName(classToCheckName);
        if (refType == null) {
            log.complain("debuger FAILURE> Class " + classToCheckName
                       + " not found.");
            return 2;
        }

        // Check methods from debuggee
        try {
            methods = refType.methods();
        } catch (Exception e) {
            log.complain("debuger FAILURE> Can't get methods from "
                       + classToCheckName);
            log.complain("debuger FAILURE> Exception: " + e);
            return 2;
        }
        int totalMethods = methods.size();
        if (totalMethods < 1) {
            log.complain("debuger FAILURE> Total number of methods in debuggee "
                       + "read " + totalMethods);
            return 2;
        }
        log.display("debuger> Total methods in debuggee read: "
                  + totalMethods + " total methods in debuger: "
                  + METHOD_NAME.length);
        for (int i = 0; i < totalMethods; i++) {
            Method method = (Method)methods.get(i);
            String name = method.name();
            boolean isSynthetic;
            boolean isRealSynthetic = true;

            try {
                isSynthetic = method.isSynthetic();

                if (!canGetSynthetic) {
                    log.complain("debuger FAILURE 1> Value of "
                               + "canGetSyntheticAttribute in current VM is "
                               + "false, so UnsupportedOperationException was "
                               + "expected for " + i + " method " + name);
                    testFailed = true;
                    continue;
                } else {
                    log.display("debuger> " + i + " method " + name + " with "
                              + "synthetic value " + isSynthetic + " read "
                              + "without UnsupportedOperationException");
                }
            } catch (UnsupportedOperationException e) {
                if (canGetSynthetic) {
                    log.complain("debuger FAILURE 2> Value of "
                               + "canGetSyntheticAttribute in current VM is "
                               + "true, but cannot get synthetic for method "
                               + "name.");
                    log.complain("debuger FAILURE 2> Exception: " + e);
                    testFailed = true;
                } else {
                    log.display("debuger> UnsupportedOperationException was "
                              + "thrown while getting isSynthetic for " + i
                              + " method " + name + " because value "
                              + "canGetSynthetic is false.");
                }
                continue;
            }

            // Find out if method exists in list of methods
            for (int j = 0; j < METHOD_NAME.length; j++) {
                String nameFromList = METHOD_NAME[j];

                if (nameFromList.equals(name)) {
                    // Method found in list - is not synthetic

                    isRealSynthetic = false;
                    break;
                }
            }

            if (isRealSynthetic != isSynthetic) {
                log.complain("debuger FAILURE 3> Method's " + name
                           + " synthetic is " + isSynthetic + ", but expected "
                           + "is " + isRealSynthetic);
                testFailed = true;
                continue;
            }

            if (isSynthetic) {
                totalSyntheticMethods++;
            }
        }

        if (totalSyntheticMethods == 0) {
            log.complain("debuger FAILURE 4> Synthetic methods not found.");
            testFailed = true;
        }

        pipe.println("quit");
        debugee.waitFor();
        int status = debugee.getStatus();
        if (testFailed) {
            log.complain("debuger FAILURE> TEST FAILED");
            return 2;
        } else {
            if (status == 95) {
                log.display("debuger> expected Debugee's exit "
                          + "status - " + status);
                return 0;
            } else {
                log.complain("debuger FAILURE> UNEXPECTED Debugee's exit "
                           + "status (not 95) - " + status);
                return 2;
            }
        }
    }
}
