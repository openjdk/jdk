/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import com.sun.jdi.*;
import com.sun.jdi.connect.*;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;


/* This class is used to test multi VM connectivity feature of
 * SA/JDI. Accepts two PIDs as arguments. Connects to first VM
 *, disposes it, connects to second VM, disposes second VM.
 */


public class serialvm {
    static AttachingConnector myPIDConn;
    static VirtualMachine vm1;
    static VirtualMachine vm2;
    static VirtualMachineManager vmmgr;

    public static void println(String msg) {
        System.out.println(msg);
    }

    private static void usage() {
        System.err.println("Usage: java serialvm <pid1> <pid2>");
        System.exit(1);
    }

    public static void main(String args[]) {
        vmmgr = Bootstrap.virtualMachineManager();
        List attachingConnectors = vmmgr.attachingConnectors();
        if (attachingConnectors.isEmpty()) {
            System.err.println( "ERROR: No attaching connectors");
            return;
        }
        Iterator myIt = attachingConnectors.iterator();
        while (myIt.hasNext()) {
            AttachingConnector tmpCon = (AttachingConnector)myIt.next();
            if (tmpCon.name().equals(
                "sun.jvm.hotspot.jdi.SAPIDAttachingConnector")) {
                myPIDConn = tmpCon;
                break;
            }
        }

        int pid1 = 0, pid2 = 0;
        String pidText = null;
        switch (args.length) {
        case (2):
            try {
                pidText = args[0];
                pid1 = Integer.parseInt(pidText);
                System.out.println( "pid1: " + pid1);
                pidText = args[1];
                pid2 = Integer.parseInt(pidText);
                System.out.println( "pid2: " + pid2);
            } catch (NumberFormatException e) {
                println(e.getMessage());
                usage();
            }
            break;
        default:
            usage();
        }

        // attach, dispose, attach2, dispose2 pattern
        // as opposed to attach1, attach2, dispose1, dispose2
        vm1 = attachPID(pid1);
        if (vm1 != null) {
            System.out.println("vm1: attached ok!");
            System.out.println(vm1.version());
            sagdoit mine = new sagdoit(vm1);
            mine.doAll();
        }
        if (vm1 != null) {
            vm1.dispose();
        }

        vm2 = attachPID(pid2);
        if (vm2 != null) {
            System.out.println("vm2: attached ok!");
            System.out.println(vm2.version());
            sagdoit mine = new sagdoit(vm2);
            mine.doAll();
        }


        if (vm2 != null) {
            vm2.dispose();
        }
    }

   private static VirtualMachine attachPID(int pid) {
        Map connArgs = myPIDConn.defaultArguments();
        System.out.println("connArgs = " + connArgs);
        VirtualMachine vm;
        Connector.StringArgument connArg = (Connector.StringArgument)connArgs.get("pid");
        connArg.setValue(Integer.toString(pid));

        try {
            vm = myPIDConn.attach(connArgs);
        } catch (IOException ee) {
            System.err.println("ERROR: myPIDConn.attach got IO Exception:" + ee);
            vm = null;
        } catch (IllegalConnectorArgumentsException ee) {
            System.err.println("ERROR: myPIDConn.attach got illegal args exception:" + ee);
            vm = null;
        }
        return vm;
   }
}
