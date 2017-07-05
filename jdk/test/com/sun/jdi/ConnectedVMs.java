/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 *  @run main ConnectedVMs InstTarg
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
    static int pass;
    static String[] passNames = {"Kill", "Resume to exit",
                                 "dispose()", "exit()"};

    public static void main(String args[]) throws Exception {
        for (pass=0; pass < passNames.length; pass++) {
            new ConnectedVMs(args).startTests();
        }
        if (failCount > 0) {
            throw new RuntimeException(
             "VirtualMachineManager.connectedVirtualMachines() " +
             failCount + " tests failed");
        } else {
            System.out.println(
          "VirtualMachineManager.connectedVirtualMachines() tests passed");
        }
    }

    ConnectedVMs(String args[]) throws Exception {
        super(args);
        System.out.println("create");
    }

    void vms(int expected) {
        List vms = Bootstrap.virtualMachineManager().
            connectedVirtualMachines();
        if (vms.size() != expected) {
            System.out.println("FAILURE! " + passNames[pass] +
                               " - expected: " + expected +
                               ", got: " + vms.size());
            ++failCount;
        }
    }

    protected void runTests() throws Exception {
        System.out.println("Testing " + passNames[pass]);
        vms(0);
        startToMain("InstTarg");
        ThreadReference thread = waitForVMStart();
        StepEvent stepEvent = stepIntoLine(thread);
        vms(1);

        // pick a way to die
        switch (pass) {
            case 0:
                vm().process().destroy();
                break;
            case 1:
                vm().resume();
                break;
            case 2:
                vm().dispose();
                break;
            case 3:
                vm().exit(1);
                break;
        }

        resumeToVMDisconnect();
        vms(0);
    }
}
