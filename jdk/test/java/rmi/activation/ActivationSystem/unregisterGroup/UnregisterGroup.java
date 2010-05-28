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

/* @test
 * @bug 4134233
 * @bug 4213186
 *
 * @summary synopsis: ActivationSystem.unregisterGroup should unregister objects in group
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID JavaVM StreamPipe
 * @build ActivateMe CallbackInterface
 * @build UnregisterGroup
 * @build UnregisterGroup_Stub
 * @build Callback_Stub
 * @run main/othervm/policy=security.policy/timeout=480 UnregisterGroup
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

class Callback extends UnicastRemoteObject implements CallbackInterface {

  public static int num_deactivated = 0;

  public Callback() throws RemoteException { super(); }

  public void inc() throws RemoteException {
    incNumDeactivated();
  }

  public synchronized int getNumDeactivated() throws RemoteException {
    return(num_deactivated);
  }

  public synchronized void incNumDeactivated() {
    num_deactivated++;
  }

}

public class UnregisterGroup
        extends Activatable
        implements ActivateMe, Runnable
{

    private static Exception exception = null;
    private static String error = null;
    private static boolean done = false;
    private static ActivateMe lastResortExitObj = null;
    private static final int NUM_OBJECTS = 10;
    private static int PORT = 2006;

    public UnregisterGroup(ActivationID id, MarshalledObject mobj)
        throws Exception
    {
        super(id, 0);
    }

    public void ping()
    {}

    public void unregister() throws Exception {
        super.unregister(super.getID());
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception {
        (new Thread(this,"UnregisterGroup")).start();
    }

    /**
     * To support exiting of group VM as a last resort
     */
    public void justGoAway() {
        System.exit(0);
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {

        ActivationLibrary.deactivate(this, getID());
        System.err.println("\tActivationLibrary.deactivate returned");

        try {
            CallbackInterface cobj =
                (CallbackInterface)Naming.lookup("//:" + PORT + "/Callback");
            cobj.inc();
        } catch (Exception e) {
            System.err.println("cobj.inc exception");
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {

        Registry registry;

        System.err.println("\nRegression test for bug 4134233\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");
        RMID rmid = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            /* Cause activation groups to have a security policy that will
             * allow security managers to be downloaded and installed
             */
            final Properties p = new Properties();
            // this test must always set policies/managers in its
            // activation groups
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",
                  TestParams.defaultSecurityManager);

            //final int NUM_OBJECTS = 10;

            Thread t = new Thread() {
                public void run () {
                    try {
                        System.err.println("Creating group descriptor");
                        ActivationGroupDesc groupDesc =
                            new ActivationGroupDesc(p, null);
                        ActivationSystem system = ActivationGroup.getSystem();
                        ActivationGroupID groupID =
                            system.registerGroup(groupDesc);

                        ActivateMe[] obj = new ActivateMe[NUM_OBJECTS];

                        for (int i = 0; i < NUM_OBJECTS; i++) {
                            System.err.println("Creating descriptor: " + i);
                            ActivationDesc desc =
                                new ActivationDesc(groupID, "UnregisterGroup",
                                                   null, null);
                            System.err.println("Registering descriptor: " + i);
                            obj[i] = (ActivateMe) Activatable.register(desc);
                            System.err.println("Activating object: " + i);
                            obj[i].ping();
                        }
                        lastResortExitObj = obj[0];

                        System.err.println("Unregistering group");
                        system.unregisterGroup(groupID);

                        try {
                            System.err.println("Get the group descriptor");
                            system.getActivationGroupDesc(groupID);
                            error = "test failed: group still registered";
                        } catch (UnknownGroupException e) {
                            System.err.println("Test passed: " +
                                               "group unregistered");
                        }


                        /*
                         * Deactivate objects so group VM will exit.
                         */
                        for (int i = 0; i < NUM_OBJECTS; i++) {
                            System.err.println("Deactivating object: " + i);
                            obj[i].shutdown();
                            obj[i] = null;
                        }
                        lastResortExitObj = null;

                    } catch (Exception e) {
                        exception = e;
                    }

                    done = true;
                }
            };

            t.start();
            t.join(120000);

            if (exception != null) {
                TestLibrary.bomb("test failed", exception);
            } else if (error != null) {
                TestLibrary.bomb(error, null);
            } else if (!done) {
                TestLibrary.bomb("test failed: not completed before timeout", null);
            } else {
                System.err.println("Test passed");
            }


        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            if (lastResortExitObj != null) {
                try {
                    lastResortExitObj.justGoAway();
                } catch (Exception munch) {
                }
            }

            // Wait for the object deactivation to take place first
            try {

                // create reg and export callback object
                registry = LocateRegistry.createRegistry(PORT);
                Callback robj = new Callback();
                registry.bind("Callback", robj);

                //get the callback object
                int maxwait=30;
                int nd = robj.getNumDeactivated();
                while ((nd < NUM_OBJECTS) && (maxwait> 0)) {
                    System.err.println("num_deactivated="+nd);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {}
                    maxwait--;
                    nd = robj.getNumDeactivated();
                }
            } catch (Exception ce) {
                System.err.println("E:"+ce);
                ce.printStackTrace();
            }

            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
