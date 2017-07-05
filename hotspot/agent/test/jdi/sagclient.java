/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

public class sagclient {
    static AttachingConnector myCoreConn;
    static AttachingConnector myPIDConn;
    static AttachingConnector myDbgSvrConn;
    static VirtualMachine vm;
    static VirtualMachineManager vmmgr;

    public static void println(String msg) {
        System.out.println("jj: " + msg);
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
                "sun.jvm.hotspot.jdi.SACoreAttachingConnector")) {
                myCoreConn = tmpCon;
            } else if (tmpCon.name().equals(
                "sun.jvm.hotspot.jdi.SAPIDAttachingConnector")) {
                myPIDConn = tmpCon;
            } else if (tmpCon.name().equals(
                "sun.jvm.hotspot.jdi.SADebugServerAttachingConnector")) {
                myDbgSvrConn = tmpCon;
            }
        }
        String execPath = null;
        String pidText = null;
        String coreFilename = null;
        String debugServer = null;
        int pid = 0;
        switch (args.length) {
        case (0):
            break;
        case (1):
            // If all numbers, it is a PID to attach to
            // Else, it is a pathname to a .../bin/java for a core file.
            try {
                pidText = args[0];
                pid = Integer.parseInt(pidText);
                System.out.println( "pid: " + pid);
                vm = attachPID(pid);
            } catch (NumberFormatException e) {
                System.out.println("trying remote server ..");
                debugServer = args[0];
                System.out.println( "remote server: " + debugServer);
                vm = attachDebugServer(debugServer);
            }
            break;

        case (2):
            execPath = args[0];
            coreFilename = args[1];
            System.out.println( "jdk: " + execPath);
            System.out.println( "core: " + coreFilename);
            vm = attachCore(coreFilename, execPath);
            break;
        }


        if (vm != null) {
            System.out.println("sagclient: attached ok!");
            sagdoit mine = new sagdoit(vm);
            mine.doAll();
            vm.dispose();
        }
    }

    private static VirtualMachine attachCore(String coreFilename, String execPath) {
        Map connArgs = myCoreConn.defaultArguments();
        System.out.println("connArgs = " + connArgs);
        VirtualMachine vm;
        Connector.StringArgument connArg = (Connector.StringArgument)connArgs.get("core");
        connArg.setValue(coreFilename);

        connArg =  (Connector.StringArgument)connArgs.get("javaExecutable");
        connArg.setValue(execPath);
        try {
            vm = myCoreConn.attach(connArgs);
        } catch (IOException ee) {
            System.err.println("ERROR: myCoreConn.attach got IO Exception:" + ee);
            vm = null;
        } catch (IllegalConnectorArgumentsException ee) {
            System.err.println("ERROR: myCoreConn.attach got illegal args exception:" + ee);
            vm = null;
        }
        return vm;
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


   private static VirtualMachine attachDebugServer(String debugServer) {
        Map connArgs = myDbgSvrConn.defaultArguments();
        System.out.println("connArgs = " + connArgs);
        VirtualMachine vm;
        Connector.StringArgument connArg = (Connector.StringArgument)connArgs.get("debugServerName");
        connArg.setValue(debugServer);

        try {
            vm = myDbgSvrConn.attach(connArgs);
        } catch (IOException ee) {
            System.err.println("ERROR: myDbgSvrConn.attach got IO Exception:" + ee);
            vm = null;
        } catch (IllegalConnectorArgumentsException ee) {
            System.err.println("ERROR: myDbgSvrConn.attach got illegal args exception:" + ee);
            vm = null;
        }
        return vm;
   }
}
