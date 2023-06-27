/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4329140
 * @summary ConnectedVMs checks the method
 * VirtualMachineManager.connectedVirtualMachines()
 * @author Robert Field
 *
 * @library /test/lib
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g InstTarg.java
 * @run driver ConnectedVMs Kill
 * @run driver ConnectedVMs Resume-to-exit
 * @run driver ConnectedVMs dispose()
 * @run driver ConnectedVMs exit()
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;

import jdk.test.lib.Platform;

public class ConnectedVMs extends TestScaffold {
    static int failCount = 0;;
    static String passName;

    public static void main(String args[]) throws Exception {
        new ConnectedVMs(args[0]).startTests();
        if (failCount > 0) {
            throw new RuntimeException(
             "VirtualMachineManager.connectedVirtualMachines() " +
             failCount + " tests failed");
        } else {
            System.out.println(
          "VirtualMachineManager.connectedVirtualMachines() tests passed");
        }
    }

    ConnectedVMs(String name) throws Exception {
        super(new String[0]);
        passName = name;
        System.out.println("create " + passName);
    }

    @Override
    protected boolean allowedExitValue(int exitValue) {
        if (passName.equals("Kill")) {
            // 143 is SIGTERM, which we expect to get when doing a Process.destroy(),
            // unless we are on Windows, which will exit with a 1. However, sometimes
            // there is a race and the main thread exits before SIGTERM can force
            // an exit(143), so we need to allow exitValue 0 also.
            if (!Platform.isWindows()) {
                return exitValue == 143 || exitValue == 0;
            } else {
                return exitValue == 1 || exitValue == 0;
            }
        } else if (passName.equals("exit()")) {
            // This version of the test does an exit(1), so that's what we expect.
            // But similar to the SIGTERM race issue, the exit(1) might not happen
            // before the main thread exits, so we need to expect 0 also.
            return exitValue == 1 || exitValue == 0;
        }
        return super.allowedExitValue(exitValue);
    }

    void vms(int expected) {
        List vms = Bootstrap.virtualMachineManager().
            connectedVirtualMachines();
        if (vms.size() != expected) {
            System.out.println("FAILURE! " + passName +
                               " - expected: " + expected +
                               ", got: " + vms.size());
            ++failCount;
        }
    }

    protected void runTests() throws Exception {
        System.out.println("Testing " + passName);
        vms(0);
        BreakpointEvent bp = startToMain("InstTarg");
        waitForVMStart();
        StepEvent stepEvent = stepIntoLine(bp.thread());
        vms(1);

        // pick a way to die based on the input arg.
        if (passName.equals("Kill")) {
            vm().process().destroy();
        } else if (passName.equals("Resume-to-exit")) {
            vm().resume();
        } else if (passName.equals("dispose()")) {
            vm().dispose();
        } else if (passName.equals("exit()")) {
            vm().exit(1);
        } else {
            throw new Exception("Unknown pass name");
        }

        resumeToVMDisconnect();
        vms(0);
    }
}
