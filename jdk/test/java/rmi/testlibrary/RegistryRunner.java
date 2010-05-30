/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

/**/

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;

/**
 * Class to run a registry whos VM can be told to exit remotely; using
 * the rmiregistry in this fashion makes tests more robust under
 * windows where Process.destroy() seems not to be 100% reliable.
 */
public class RegistryRunner extends UnicastRemoteObject
    implements RemoteExiter
{
    private static Registry registry = null;
    private static RemoteExiter exiter = null;

    public RegistryRunner() throws RemoteException {
    }

    /**
     * Ask the registry to exit instead of forcing it do so; this
     * works better on windows...
     */
    public void exit() throws RemoteException {
        // REMIND: create a thread to do this to avoid
        // a remote exception?
        System.err.println("received call to exit");
        System.exit(0);
    }

    /**
     * Request that the registry process exit and handle
     * related exceptions.
     */
    public static void requestExit() {
        try {
            RemoteExiter exiter =
                (RemoteExiter)
                Naming.lookup("rmi://localhost:" +
                              TestLibrary.REGISTRY_PORT +
                              "/RemoteExiter");
            try {
                exiter.exit();
            } catch (RemoteException re) {
            }
            exiter = null;
        } catch (java.net.MalformedURLException mfue) {
            // will not happen
        } catch (NotBoundException nbe) {
            TestLibrary.bomb("exiter not bound?", nbe);
        } catch (RemoteException re) {
            TestLibrary.bomb("remote exception trying to exit",
                             re);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.err.println("Usage: <port>");
                System.exit(0);
            }
            int port = TestLibrary.REGISTRY_PORT;
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
            }

            // create a registry
            registry = LocateRegistry.createRegistry(port);

            // create a remote object to tell this VM to exit
            exiter = new RegistryRunner();
            Naming.rebind("rmi://localhost:" + port +
                          "/RemoteExiter", exiter);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
