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

import java.io.*;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;

public class ServerImpl
    extends UnicastRemoteObject
    implements Server
{
    private String name;
    Callback cLocal;

    public ServerImpl(String s) throws java.rmi.RemoteException {
        super();
        name = s;
    }

    public String sayHello(Callback c) throws RemoteException {
        System.out.println("Calling Callback method from the ServerImpl");
        cLocal = c;
        new Thread(new Runnable() {
            public void run() {
                System.out.println(
                    "+ running a new thread in sayHello method!");
                try {
                    cLocal.callback();
                } catch(RemoteException e) {
                    System.out.println(
                        "ServerImpl.main: exception while calling callback " +
                        "method:");
                    e.printStackTrace();
                }
            }
        }).start();
        return "Hello Callback!";
    }

    public static void main(String args[]) {
        // Create and install the security manager
        System.setSecurityManager(new RMISecurityManager());

        ServerImpl obj = null;

        try {
            obj = new ServerImpl("ServerImpl");
            Naming.rebind("/ServerImpl", obj);
            System.out.println("ServerImpl created and bound in the registry" +
                " to the name ServerImpl");
            System.err.println("DTI_DoneInitializing");
        } catch (Exception e) {
            System.out.println("ServerImpl.main: an exception occurred:");
            e.printStackTrace();
            System.err.println("DTI_Error");
        }

    }
}
