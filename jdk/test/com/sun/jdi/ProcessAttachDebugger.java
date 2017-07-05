/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *
 * Unit test for ProcessAttachingConnector - this "debugger" attaches to a debuggee
 * given it's pid. Usage:
 *
 *      java ProcessAttachDebugger <pid>
 */

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.AttachingConnector;

import java.util.List;
import java.util.Map;

public class ProcessAttachDebugger {

    public static void main(String main_args[]) throws Exception {
        String pid = main_args[0];

        // find ProcessAttachingConnector

        List<AttachingConnector> l =
            Bootstrap.virtualMachineManager().attachingConnectors();
        AttachingConnector ac = null;
        for (AttachingConnector c: l) {
            if (c.name().equals("com.sun.jdi.ProcessAttach")) {
                ac = c;
                break;
            }
        }
        if (ac == null) {
            throw new RuntimeException("Unable to locate ProcessAttachingConnector");
        }

        Map<String,Connector.Argument> args = ac.defaultArguments();
        Connector.StringArgument arg = (Connector.StringArgument)args.get("pid");
        arg.setValue(pid);

        System.out.println("Debugger is attaching to: " + pid + " ...");

        VirtualMachine vm = ac.attach(args);

        System.out.println("Attached! Now listing threads ...");

        // list all threads

        for (ThreadReference thr: vm.allThreads()) {
            System.out.println(thr);
        }

        System.out.println("Debugger done.");
    }

}
