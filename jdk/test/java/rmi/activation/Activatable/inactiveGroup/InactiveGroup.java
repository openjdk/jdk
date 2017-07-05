/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4116082
 *
 * @summary synopsis: rmid should not destroy group when it reports
 * inactiveGroup
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary ActivateMe InactiveGroup_Stub
 * @run main/othervm/policy=security.policy/timeout=240 InactiveGroup
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

public class InactiveGroup
        implements ActivateMe, Runnable
{

    private ActivationID id;

    public InactiveGroup(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
    {
        this.id = id;
        Activatable.exportObject(this, id, 0);
    }

    public InactiveGroup() throws RemoteException {
        UnicastRemoteObject.exportObject(this, 0);
    }

    public void ping()
    {}

    public ActivateMe getUnicastVersion() throws RemoteException {
        return new InactiveGroup();
    }

    public ActivationID getID() {
        return id;
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"InactiveGroup")).start();
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

        System.out.println("\nRegression test for bug 4116082\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        RMID rmid = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            /* Cause activation groups to have a security policy that will
             * allow security managers to be downloaded and installed
             */
            Properties p = new Properties();
            // this test must always set policies/managers in its
            // activation groups
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",
                  TestParams.defaultSecurityManager);

            /*
             * Create descriptor and activate object in a separate VM.
             */
            System.err.println("Creating descriptor");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);
            ActivationDesc desc =
                new ActivationDesc(groupID, "InactiveGroup", null, null);

            System.err.println("Registering descriptor");
            ActivateMe activatableObj = (ActivateMe) Activatable.register(desc);

            System.err.println("Activate object via method call");
            activatableObj.ping();

            /*
             * Create a unicast object in the activatable object's VM.
             */
            System.err.println("Obtain unicast object");
            ActivateMe unicastObj = activatableObj.getUnicastVersion();

            /*
             * Make activatable object (and therefore group) inactive.
             */
            System.err.println("Make activatable object inactive");
            activatableObj.shutdown();

            /*
             * Ping the unicast object a few times to make sure that the
             * activation group's process hasn't gone away.
             */
            System.err.println("Ping unicast object for existence");
            for (int i = 0; i < 10; i++) {
                unicastObj.ping();
                Thread.sleep(500);
            }

            /*
             * Now, reactivate the activatable object; the unicast object
             * should no longer be accessible, since reactivating the
             * activatable object should kill the previous group's VM
             * and the unicast object along with it.
             */
            System.err.println("Reactivate activatable obj");
            activatableObj.ping();

            try {
                System.err.println("Ping unicast object again");
                unicastObj.ping();
            } catch (Exception thisShouldFail) {
                System.err.println("Test passed: couldn't reach unicast obj: " +
                                   thisShouldFail.getMessage());
                return;
            }

            TestLibrary.bomb("Test failed: unicast obj accessible after group reactivates",
                 null);

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
