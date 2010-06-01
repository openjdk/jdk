/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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
 */
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.security.CodeSource;
import java.net.URL;

public class EchoImpl
    extends Activatable
    implements Echo, Runnable
{
    private static final byte[] pattern = { (byte) 'A' };

    /**
     * Initialization constructor.
     */
    public EchoImpl(String protocol)
        throws ActivationException, RemoteException
    {
        super(null, makeMarshalledObject(protocol), false, 0,
              new MultiSocketFactory.ClientFactory(protocol, pattern),
              new MultiSocketFactory.ServerFactory(protocol, pattern));
    }

    /**
     * Activation constructor.
     */
    public EchoImpl(ActivationID id, MarshalledObject obj)
        throws RemoteException
    {
        super(id, 0,
              new MultiSocketFactory.ClientFactory(getProtocol(obj), pattern),
              new MultiSocketFactory.ServerFactory(getProtocol(obj), pattern));
    }

    private static MarshalledObject makeMarshalledObject(String protocol) {
        MarshalledObject obj = null;
        try {
            obj = new MarshalledObject(protocol);
        } catch (Exception willNotHappen) {
        }

        return obj;
    }

    private static String getProtocol(MarshalledObject obj) {
        String protocol = "";
        try {
            protocol = (String) obj.get();
        } catch (Exception willNotHappen) {
        }

        return protocol;
    }

    public byte[] echoNot(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++)
            result[i] = (byte) ~data[i];
        return result;
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"Echo.shutdown")).start();
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run()
    {
        ActivationLibrary.deactivate(this, getID());
    }

    public static void main(String[] args) {
        /*
         * The following line is required with the JDK 1.2 VM so that the
         * VM can exit gracefully when this test completes.  Otherwise, the
         * conservative garbage collector will find a handle to the server
         * object on the native stack and not clear the weak reference to
         * it in the RMI runtime's object table.
         */
        Object dummy = new Object();

        System.setSecurityManager(new RMISecurityManager());

        try {
            String protocol = "";
            if (args.length >= 1)
                protocol = args[0];

            System.out.println("EchoServer: creating remote object");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(null, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            ActivationGroup.createGroup(groupID, groupDesc, 0);

            EchoImpl impl = new EchoImpl(protocol);
            System.out.println("EchoServer: binding in registry");
            Naming.rebind("//:" + UseCustomSocketFactory.REGISTRY_PORT +
                          "/EchoServer", impl);
            System.out.println("EchoServer ready.");
        } catch (Exception e) {
            System.err.println("EXCEPTION OCCURRED:");
            e.printStackTrace();
        }
    }
}
