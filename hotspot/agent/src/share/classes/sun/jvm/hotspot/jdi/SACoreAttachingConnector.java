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

public class SACoreAttachingConnector extends ConnectorImpl implements AttachingConnector {

    static final String ARG_COREFILE = "core";
    static final String ARG_JAVA_EXECUTABLE = "javaExecutable";
    private Transport transport;

    public SACoreAttachingConnector(com.sun.tools.jdi.VirtualMachineManagerService ignored) {
        this();
    }

    public SACoreAttachingConnector() {
        super();
        //fixme jjh  Must create resources for these strings
        addStringArgument(
                ARG_JAVA_EXECUTABLE,
                "Java Executable",              //getString("sa.javaExecutable.label"),
                "Pathname of Java Executable",  //getString("sa.javaExecutable.description");
                "",
                true);

        addStringArgument(
                ARG_COREFILE,
                "Corefile",                                    // getString("sa.CoreFile.label"),
                "Pathname of a corefile from a Java Process",  //getString("sa.CoreFile.description"),
                "core",
                false);

        transport = new Transport() {
                   public String name() {
                       return "filesystem";
                   }
               };
    }

    // security check to see whether the caller can perform attach
    private void checkCoreAttach(String corefile) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                // whether the caller can link against SA native library?
                checkNativeLink(sm, System.getProperty("os.name"));
                // check whether the caller can read the core file?
                sm.checkRead(corefile);
            } catch (SecurityException se) {
                throw new SecurityException("permission denied to attach to " + corefile);
            }
        }
    }

    private VirtualMachine createVirtualMachine(Class vmImplClass,
                                                String javaExec, String corefile)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        java.lang.reflect.Method connectByCoreMethod = vmImplClass.getMethod(
                                 "createVirtualMachineForCorefile",
                                  new Class[] {
                                      VirtualMachineManager.class,
                                      String.class, String.class,
                                      Integer.TYPE
                                  });
        return (VirtualMachine) connectByCoreMethod.invoke(null,
                                  new Object[] {
                                      Bootstrap.virtualMachineManager(),
                                      javaExec,
                                      corefile,
                                      new Integer(0)
                                  });
    }

    public VirtualMachine attach(Map arguments) throws IOException,
                                      IllegalConnectorArgumentsException {
        String javaExec = argument(ARG_JAVA_EXECUTABLE, arguments).value();
        if (javaExec == null || javaExec.equals("")) {
            throw new IllegalConnectorArgumentsException("javaExec should be non-null and non-empty",
                                                         ARG_JAVA_EXECUTABLE);
        }
        String corefile = argument(ARG_COREFILE, arguments).value();
        if (corefile == null || corefile.equals("")) {
            throw new IllegalConnectorArgumentsException("corefile should be non-null and non-empty",
                                                         ARG_COREFILE);
        }

        checkCoreAttach(corefile);

        VirtualMachine myVM = null;
        try {
            try {
                Class vmImplClass = loadVirtualMachineImplClass();
                myVM = createVirtualMachine(vmImplClass, javaExec, corefile);
            } catch (InvocationTargetException ite) {
                Class vmImplClass = handleVMVersionMismatch(ite);
                if (vmImplClass != null) {
                    return createVirtualMachine(vmImplClass, javaExec, corefile);
                } else {
                    throw ite;
                }
            }
        } catch (Exception ee) {
            if (DEBUG) {
                System.out.println("VirtualMachineImpl() got an exception:");
                ee.printStackTrace();
                System.out.println("coreFile = " + corefile + ", javaExec = " + javaExec);
            }
            throw (IOException) new IOException().initCause(ee);
        }
        setVMDisposeObserver(myVM);
        return myVM;
    }

    public String name() {
        return "sun.jvm.hotspot.jdi.SACoreAttachingConnector";
    }

    public String description() {
        return getString("This connector allows you to attach to a core file using the Serviceability Agent");
    }

    public Transport transport() {
        return transport;
    }
}
