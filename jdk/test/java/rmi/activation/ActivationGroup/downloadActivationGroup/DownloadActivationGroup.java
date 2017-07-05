/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4510355
 * @summary ActivationGroup implementations cannot be downloaded by default;
 * Creates a custom activation group without setting a security manager
 * in activation group's descriptor.  The custom activation group
 * implementation should be downloaded when first object within that group
 * is activated.
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     DownloadActivationGroup MyActivationGroupImpl DownloadActivationGroup_Stub
 * @run main/othervm/policy=security.policy/timeout=240 DownloadActivationGroup
 */

import java.io.*;
import java.rmi.*;
import java.net.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Vector;
import java.util.Properties;

public class DownloadActivationGroup
        implements Ping, Runnable
{

    private ActivationID id;

    public DownloadActivationGroup(ActivationID id, MarshalledObject mobj)
        throws ActivationException, RemoteException
    {
        this.id = id;
        Activatable.exportObject(this, id, 0);
        System.err.println("object activated in group");
    }

    public DownloadActivationGroup() throws RemoteException {
        UnicastRemoteObject.exportObject(this, 0);
    }

    /**
     * Used to activate object.
     */
    public void ping() {
        System.err.println("received ping");
    }

    /**
     * Spawns a thread to deactivate the object (and thus, shuts down the
     * activation group).
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"DownloadActivationGroup")).start();
    }

    /**
     * Thread to deactivate object.
     */
    public void run() {
        ActivationLibrary.deactivate(this, getID());
    }

    public ActivationID getID() {
        return id;
    }


    public static void main(String[] args) {

        RMID rmid = null;

        System.out.println("\nRegression test for bug 4510355\n");

        try {
            TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

            /*
             * Install group class file in codebase.
             */
            System.err.println("install class file in codebase");
            URL groupURL = TestLibrary.installClassInCodebase(
                                  "MyActivationGroupImpl", "group");
            System.err.println("class file installed");

            /*
             * Start rmid.
             */
            RMID.removeLog();
            rmid = RMID.createRMID();
            String execPolicyOption = "-Dsun.rmi.activation.execPolicy=none";
            rmid.addOptions(new String[] { execPolicyOption });
            rmid.start();

            /*
             * Create and register descriptors for custom group and an
             * activatable object in that group.
             */
            System.err.println("register group");

            Properties p = new Properties();
            p.put("java.security.policy", TestParams.defaultGroupPolicy);

            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc("MyActivationGroupImpl",
                                        groupURL.toExternalForm(),
                                        null, p, null);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);


            System.err.println("register activatable object");
            ActivationDesc desc =
                new ActivationDesc(groupID, "DownloadActivationGroup",
                                   null, null);
            Ping obj = (Ping) Activatable.register(desc);

            /*
             * Start group (by calling ping).
             */
            System.err.println(
                "ping object (forces download of group's class)");
            obj.ping();
            System.err.println(
                "TEST PASSED: group's class downloaded successfully");
            System.err.println("shutdown object");
            obj.shutdown();
            System.err.println("TEST PASSED");

        } catch (Exception e) {
            TestLibrary.bomb(e);
        } finally {
            rmid.cleanup();
        }
    }
}

interface Ping extends Remote {
    public void ping() throws RemoteException;
    public void shutdown() throws Exception;
}
