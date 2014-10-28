/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4097135
 * @summary Need a specific subtype of RemoteException for activation failure.
 *          If activation fails to happen during a call to a remote object,
 *          then the call should end in an ActivateFailedException. In this
 *          test, the actual "activatable" remote object fails to activate
 *          since its * "activation" constructor throws an exception.
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     ActivateMe ActivateFails_Stub ShutdownThread
 * @run main/othervm/java.security.policy=security.policy/timeout=240 ActivateFails
 */

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.io.*;
import java.util.Properties;

public class ActivateFails
        extends Activatable
        implements ActivateMe
{

    public ActivateFails(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
    {
        super(id, 0);

        boolean refuseToActivate = false;
        try {
            refuseToActivate = ((Boolean)obj.get()).booleanValue();
        } catch (Exception impossible) {
        }

        if (refuseToActivate)
            throw new RemoteException("object refuses to activate");
    }

    public void ping()
    {}

    /**
     * Spawns a thread to deactivate the object.
     */
    public ShutdownThread shutdown() throws Exception
    {
        ShutdownThread shutdownThread = new ShutdownThread(this, getID());
        shutdownThread.start();
        return(shutdownThread);
    }

    public static void main(String[] args)
    {
        RMID rmid = null;
        ActivateMe obj1, obj2;
        ShutdownThread shutdownThread;

        System.err.println("\nRegression test for bug 4097135\n");
        try {
            TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

            /*
             * First run "rmid" and wait for it to start up.
             */
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
             * Create activation descriptor...
             */
            System.err.println("creating activation descriptor...");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);

            ActivationDesc desc1 =
                new ActivationDesc(groupID, "ActivateFails",
                                   null,
                                   new MarshalledObject(new Boolean(true)));

            ActivationDesc desc2 =
                new ActivationDesc(groupID, "ActivateFails",
                                   null,
                                   new MarshalledObject(new Boolean(false)));
            /*
             * Register activation descriptor and make a call on
             * the stub. Activation should fail with an
             * ActivateFailedException.  If not, report an
             * error as a RuntimeException
             */

            System.err.println("registering activation descriptor...");
            obj1 = (ActivateMe)Activatable.register(desc1);
            obj2 = (ActivateMe)Activatable.register(desc2);

            System.err.println("invoking method on activatable object...");
            try {
                obj1.ping();

            } catch (ActivateFailedException e) {

                /*
                 * This is what is expected so exit with status 0
                 */
                System.err.println("\nsuccess: ActivateFailedException " +
                                   "generated");
                e.getMessage();
            }

            obj2.ping();
            shutdownThread = obj2.shutdown();

            // wait for shutdown to work
            Thread.sleep(2000);

            shutdownThread = null;

        } catch (Exception e) {
            /*
             * Test failed; unexpected exception generated.
             */
            TestLibrary.bomb("\nfailure: unexpected exception " +
                               e.getClass().getName() + ": " + e.getMessage(), e);

        } finally {
            obj1 = obj2 = null;
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
