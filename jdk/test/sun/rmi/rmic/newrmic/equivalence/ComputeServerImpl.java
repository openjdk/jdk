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
 */

import java.rmi.*;
import java.rmi.server.*;
import java.io.*;

/**
 * Class accepts a task and runs it in its own space.
 */
public class ComputeServerImpl
    extends UnicastRemoteObject
    implements ComputeServer
{
        public ComputeServerImpl() throws RemoteException
        {

    }

    /**
     * Accepts task and runs it
     */
    public Object compute(Task task) {
        return task.run();
    }

    /**
     * Binds compute server and waits for tasks
     */
    public static void main(String args[]) throws Exception
        {
                // use the default, restrictive security manager
                System.setSecurityManager(new RMISecurityManager());

                Naming.rebind("/ComputeServer", new ComputeServerImpl());
                System.out.println("Ready to receive tasks.");

                System.err.println("DTI_DoneInitializing");
    }
}
