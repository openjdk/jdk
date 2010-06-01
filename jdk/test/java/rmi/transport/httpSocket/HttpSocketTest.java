/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 *
 * @summary HttpSocket functionality test
 * @author Dana Burns
 *
 * @library ../../testlibrary
 * @build HttpSocketTest HttpSocketTest_Stub
 * @run main/othervm/policy=security.policy HttpSocketTest
 */

/*
 *  This test assures remote methods can be carried out over RMI.
 *  After setting the RMI runtime socket factory to the http proxy version,
 *  a registry is created, a remote object (an instance of this class) is
 *  registered with it, and then it is exercised.
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import sun.rmi.transport.proxy.RMIHttpToPortSocketFactory;

interface MyRemoteInterface extends Remote {
    void setRemoteObject( Remote r ) throws RemoteException;
    Remote getRemoteObject() throws RemoteException;
}

public class HttpSocketTest extends UnicastRemoteObject
    implements MyRemoteInterface
{

    private static final String NAME = "HttpSocketTest";
    private static final String REGNAME =
        "//:" + TestLibrary.REGISTRY_PORT + "/" + NAME;

    public HttpSocketTest() throws RemoteException{}

    private Remote ro;

    public static void main(String[] args)
        throws Exception
    {

        Registry registry = null;

        TestLibrary.suggestSecurityManager(null);

        // Set the socket factory.
        System.err.println("installing socket factory");
        RMISocketFactory.setSocketFactory(new RMIHttpToPortSocketFactory());

        try {

            System.err.println("Starting registry");
            registry = LocateRegistry.createRegistry(TestLibrary.REGISTRY_PORT);

        } catch (Exception e) {
            TestLibrary.bomb(e);
        }

        try {

            registry.rebind( NAME, new HttpSocketTest() );
            MyRemoteInterface httpTest =
                (MyRemoteInterface)Naming.lookup( REGNAME );
            httpTest.setRemoteObject( new HttpSocketTest() );
            Remote r = httpTest.getRemoteObject();

        } catch (Exception e) {
            TestLibrary.bomb(e);
        }


    }

    public void setRemoteObject( Remote ro ) throws RemoteException {
        this.ro = ro;
    }

    public Remote getRemoteObject() throws RemoteException {
        return( this.ro );
    }

}
