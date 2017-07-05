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

public class SADebugServerAttachingConnector extends ConnectorImpl implements AttachingConnector {

    static final String ARG_DEBUG_SERVER_NAME = "debugServerName";
    private Transport transport;

    public SADebugServerAttachingConnector(com.sun.tools.jdi.VirtualMachineManagerService ignored) {
        this();
    }

    public SADebugServerAttachingConnector() {
         // fixme jjh  create resources for the these strings,
        addStringArgument(
                ARG_DEBUG_SERVER_NAME,
                "Debug Server",                      //getString("sa.debugServer.label"),
                "Name of a remote SA Debug Server",  //getString("sa.debugServer.description");
                "",
                true);
        transport = new Transport() {
                   public String name() {
                       return "RMI";
                   }
                 };
    }

    private VirtualMachine createVirtualMachine(Class vmImplClass,
                                                String debugServerName)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        java.lang.reflect.Method connectByServerMethod =
                            vmImplClass.getMethod(
                                   "createVirtualMachineForServer",
                                   new Class[] {
                                       VirtualMachineManager.class,
                                       String.class,
                                       Integer.TYPE
                                   });
        return (VirtualMachine) connectByServerMethod.invoke(null,
                                   new Object[] {
                                       Bootstrap.virtualMachineManager(),
                                       debugServerName,
                                       new Integer(0)
                                   });
    }

    public VirtualMachine attach(Map arguments) throws IOException,
                                      IllegalConnectorArgumentsException {
        String debugServerName = argument(ARG_DEBUG_SERVER_NAME, arguments).value();
        if (debugServerName == null || debugServerName.equals("")) {
            throw new IllegalConnectorArgumentsException("debugServerName should be non-null and non-empty",
                                                         ARG_DEBUG_SERVER_NAME);
        }
        VirtualMachine myVM;
        try {
            try {
                Class vmImplClass = loadVirtualMachineImplClass();
                myVM = createVirtualMachine(vmImplClass, debugServerName);
            } catch (InvocationTargetException ite) {
                Class vmImplClass = handleVMVersionMismatch(ite);
                if (vmImplClass != null) {
                    return createVirtualMachine(vmImplClass, debugServerName);
                } else {
                    throw ite;
                }
            }
        } catch (Exception ee) {
            if (DEBUG) {
                System.out.println("VirtualMachineImpl() got an exception:");
                ee.printStackTrace();
                System.out.println("debug server name = " + debugServerName);
            }
            throw (IOException) new IOException().initCause(ee);
        }
        setVMDisposeObserver(myVM);
        return myVM;
    }

    public String name() {
        return "sun.jvm.hotspot.jdi.SADebugServerAttachingConnector";
    }

    public String description() {
        return getString("This connector allows you to attach to a Java Process via a debug server with the Serviceability Agent");
    }

    public Transport transport() {
        return transport;
    }
}
