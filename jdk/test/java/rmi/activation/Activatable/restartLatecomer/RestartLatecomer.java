/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4526514
 * @summary rmid does not handle group restart for latecomer objects
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @build RestartLatecomer
 * @build RestartLatecomer_Stub
 * @run main/othervm/policy=security.policy/timeout=240 RestartLatecomer
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Vector;
import java.util.Properties;

public class RestartLatecomer
        implements ActivateMe, Runnable
{

    private ActivationID id;
    private static Object lock = new Object();
    private Vector responders = new Vector();

    private static final String RESTARTABLE = "restartable";
    private static final String ACTIVATABLE = "activatable";


    public RestartLatecomer(ActivationID id, MarshalledObject mobj)
        throws ActivationException, RemoteException
    {
        this.id = id;
        Activatable.exportObject(this, id, 0);
        ActivateMe obj;
        String responder;
        try {
            Object[] stuff = (Object[]) mobj.get();
            responder = (String) stuff[0];
            System.err.println(responder + " service started");
            obj = (ActivateMe) stuff[1];
        } catch (Exception e) {
            System.err.println("unable to obtain stub from marshalled object");
            return;
        }

        /*
         * Call back object in the test VM to notify it that
         * this object has been activated or restarted.
         */
        obj.callback(responder);
    }

    public RestartLatecomer() throws RemoteException {
        UnicastRemoteObject.exportObject(this, 0);
    }

    private void waitFor(String responder) throws Exception {
        synchronized (lock) {
            for (int i = 0; i < 15; i++) {
                if (responders.contains(responder) != true) {
                    lock.wait(5000);
                    if (responders.contains(responder) == true) {
                        return;
                    }
                } else {
                    return;
                }
            }
        }

        throw new RuntimeException(
            "TEST FAILED: service not restarted by timeout");
    }

    private void clearResponders() {
        synchronized (lock) {
            responders.clear();
        }
    }


    /**
     * Notifies the receiver that the object denoted by "responder"
     * has activated or restarted.
     */
    public void callback(String responder) {
        System.err.println(
            "RestartLatecomer: received callback from " + responder);
        /*
         * Notify waiter that callback has been received and
         * test can proceed.
         */
        synchronized (lock) {
            responders.add(responder);
            lock.notifyAll();
        }
    }

    /**
     * Pings object (to activate it).
     */
    public void ping() {
        System.err.println("RestartLatecomer: recevied ping");
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() {
        System.err.println("RestartLatecomer: received shutdown request");
        (new Thread(this,"RestartLatecomer")).start();
    }

    public ActivationID getID() {
        return id;
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {
        System.exit(0);
    }

    public static void main(String[] args) {

        System.out.println("\nRegression test for bug 4526514\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        RMID rmid = null;
        RestartLatecomer callbackObj = null;

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
             * Create unicast object to be contacted when service is activated.
             */
            callbackObj = new RestartLatecomer();
            /*
             * Create and register descriptors for a restartable and
             * non-restartable service (respectively) in a group other than
             * this VM's group.
             */
            System.err.println("Creating descriptors");

            Object[] stuff = new Object[] { RESTARTABLE, callbackObj };
            MarshalledObject restartMobj = new MarshalledObject(stuff);
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);

            stuff[0] = ACTIVATABLE;
            MarshalledObject activateMobj = new MarshalledObject(stuff);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);

            ActivationDesc activatableDesc =
                new ActivationDesc(groupID, "RestartLatecomer", null,
                                   activateMobj, false);
            ActivationDesc restartableDesc =
                new ActivationDesc(groupID, "RestartLatecomer", null,
                                   restartMobj, true);


            System.err.println("Register activatable object's descriptor");
            ActivateMe activatableObj =
                (ActivateMe) Activatable.register(activatableDesc);

            System.err.println("Activate object (starts group VM)");
            activatableObj.ping();

            callbackObj.waitFor(ACTIVATABLE);
            callbackObj.clearResponders();
            System.err.println("Callback from activatable object received");

            System.err.println("Register restartable object's descriptor");
            ActivateMe restartableObj =
                (ActivateMe) Activatable.register(restartableDesc);

            System.err.println("Shutdown object (exits group VM)");
            try {
                activatableObj.shutdown();
            } catch (RemoteException ignore) {
                /*
                 * Since the shutdown method spawns a thread to call
                 * System.exit, the group's VM may exit, closing all
                 * connections, before this call had returned.  If that
                 * happens, then a RemoteException will be caught
                 * here.
                 */
            }

            System.err.println("Pause for shutdown to happen...");
            Thread.sleep(5000);

            /*
             * Wait for "latecomer" restartable service to be
             * automatically restarted.
             */
            callbackObj.waitFor(RESTARTABLE);
            System.err.println(
                "TEST PASSED: rmid restarted latecomer service");

        } catch (Exception e) {
            TestLibrary.bomb(e);
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
            TestLibrary.unexport(callbackObj);
        }
    }


}


interface ActivateMe extends Remote {
    public void ping() throws RemoteException;
    public void callback(String responder) throws RemoteException;
    public void shutdown() throws RemoteException;
}
