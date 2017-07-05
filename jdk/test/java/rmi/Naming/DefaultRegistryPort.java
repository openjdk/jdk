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

/*
 * @test
 * @bug 4251878
 * @summary change in default URL port causes regression in java.rmi.Naming
 * @author Dana Burns
 * @library ../testlibrary
 * @build TestLibrary
 * @run main DefaultRegistryPort
 */

/*
 * Ensure that the default registry port for java.rmi.Naming URLs
 * is 1099. Test creates a registry on port 1099 and then does a
 * lookup with a Naming URL that uses the default port. Test fails
 * if the lookup yields a NotBoundException. If the registry could
 * not be created, a fallback strategy of using an existing one is
 * tried.
 */

import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class DefaultRegistryPort {

    public static void main(String args[]) {

        Registry registry = null;
        try {

            System.err.println(
                "Starting registry on default port REGISTRY_PORT=" +
                Registry.REGISTRY_PORT);

            registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);

            System.err.println("Created registry=" + registry);

        } catch(java.rmi.RemoteException e) {

            try {

                System.err.println(
                    "Failed to create a registry, try using existing one");
                registry = LocateRegistry.getRegistry();

                System.err.println("Found registry=" + registry);

            } catch (Exception ge) {

                TestLibrary.bomb(
                    "Test Failed: cound not find or create a registry");
            }

        }

        try {

            if (registry != null) {

                registry.rebind("myself", registry);

                Remote myself = Naming.lookup("rmi://localhost/myself");

                System.err.println("Test PASSED");

            } else {

                TestLibrary.bomb(
                    "Test Failed: cound not find or create a registry");

            }

        } catch(java.rmi.NotBoundException e) {

            TestLibrary.bomb(
                "Test Failed: could not find myself");

        } catch(Exception e) {

            e.printStackTrace();
            TestLibrary.bomb(
                "Test failed: unexpected exception");

        }

    }

}
