/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 *  @test
 *  @bug 4329140
 *  @author Robert Field
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run compile -g InstTarg.java
 *  @run main ConnectedVMs "Kill"
 *  @run main ConnectedVMs "Resume to exit"
 *  @run main ConnectedVMs "dispose()"
 *  @run main ConnectedVMs "exit()"
 *
 * @summary ConnectedVMs checks the method
 * VirtualMachineManager.connectedVirtualMachines()
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.List;

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
        startToMain("InstTarg");
        ThreadReference thread = waitForVMStart();
        StepEvent stepEvent = stepIntoLine(thread);
        vms(1);

        // pick a way to die based on the input arg.
        if (passName.equals("Kill")) {
            vm().process().destroy();
        } else if (passName.equals("Resume to exit")) {
            vm().resume();
        } else if (passName.equals("dispose()")) {
            vm().dispose();
        } else if (passName.equals("exit()")) {
            vm().exit(1);
        }

        resumeToVMDisconnect();
        vms(0);
    }
}
