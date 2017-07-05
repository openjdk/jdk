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

package sun.jvm.hotspot.jdi;

import com.sun.jdi.connect.*;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class SAPIDAttachingConnector extends ConnectorImpl implements AttachingConnector {
    static final String ARG_PID = "pid";
    private Transport transport;

    public SAPIDAttachingConnector(com.sun.tools.jdi.VirtualMachineManagerService ignored) {
         this();
    }

    public SAPIDAttachingConnector() {
         super();
         // fixme jjh:  create resources for the these strings,
        addStringArgument(
                ARG_PID,
                "PID",                     //getString("sa.pid.label"),
                "PID of a Java process",   //getString("sa.pid.description");
                "",
                true);
        transport = new Transport() {
                   public String name() {
                       return "local process";
                       }
                };
    }

    // security check to see whether the caller can perform attach
    private void checkProcessAttach(int pid) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            String os = System.getProperty("os.name");
            try {
                // Whether the caller can perform link against SA native library?
                checkNativeLink(sm, os);
                if (os.equals("SunOS") || os.equals("Linux")) {
                    // Whether the caller can read /proc/<pid> file?
                    sm.checkRead("/proc/" + pid);
                }
            } catch (SecurityException se) {
                throw new SecurityException("permission denied to attach to " + pid);
            }
        }
    }

    private VirtualMachine createVirtualMachine(Class virtualMachineImplClass, int pid)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        java.lang.reflect.Method createByPIDMethod
                  = virtualMachineImplClass.getMethod("createVirtualMachineForPID",
                     new Class[] {
                         VirtualMachineManager.class,
                         Integer.TYPE, Integer.TYPE
                     });
        return (VirtualMachine) createByPIDMethod.invoke(null,
                     new Object[] {
                         Bootstrap.virtualMachineManager(),
                         new Integer(pid),
                         new Integer(0)
                     });
    }

    public VirtualMachine attach(Map arguments) throws IOException,
                                      IllegalConnectorArgumentsException {
        int pid = 0;
        try {
            pid = Integer.parseInt(argument(ARG_PID, arguments).value());
        } catch (NumberFormatException nfe) {
            throw (IllegalConnectorArgumentsException) new IllegalConnectorArgumentsException
                                                  (nfe.getMessage(), ARG_PID).initCause(nfe);
        }

        checkProcessAttach(pid);

        VirtualMachine myVM = null;
        try {
            try {
                Class vmImplClass = loadVirtualMachineImplClass();
                myVM = createVirtualMachine(vmImplClass, pid);
            } catch (InvocationTargetException ite) {
                Class vmImplClass = handleVMVersionMismatch(ite);
                if (vmImplClass != null) {
                    return createVirtualMachine(vmImplClass, pid);
                } else {
                    throw ite;
                }
            }
        } catch (Exception ee) {
            if (DEBUG) {
                System.out.println("VirtualMachineImpl() got an exception:");
                ee.printStackTrace();
                System.out.println("pid = " + pid);
            }
            throw (IOException) new IOException().initCause(ee);
        }
        setVMDisposeObserver(myVM);
        return myVM;
    }

    public String name() {
        return "sun.jvm.hotspot.jdi.SAPIDAttachingConnector";
    }

    public String description() {
        return getString("This connector allows you to attach to a Java process using the Serviceability Agent");
    }

    public Transport transport() {
        return transport;
    }
}
