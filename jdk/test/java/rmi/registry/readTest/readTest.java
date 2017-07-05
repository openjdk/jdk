/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class readTest {

    public static void main(String args[]) throws Exception {
        try {
            testPkg.Server obj = new testPkg.Server();
            testPkg.Hello stub = (testPkg.Hello) UnicastRemoteObject.exportObject(obj, 0);
            // Bind the remote object's stub in the registry
            Registry registry =
                LocateRegistry.getRegistry(TestLibrary.READTEST_REGISTRY_PORT);
            registry.bind("Hello", stub);

            System.err.println("Server ready");

            // now, let's test client
            testPkg.Client client =
                new testPkg.Client(TestLibrary.READTEST_REGISTRY_PORT);
            String testStubReturn = client.testStub();
            if(!testStubReturn.equals(obj.hello)) {
                throw new RuntimeException("Test Fails : unexpected string from stub call");
            } else {
                System.out.println("Test passed");
            }
            registry.unbind("Hello");

        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }

    }
}
