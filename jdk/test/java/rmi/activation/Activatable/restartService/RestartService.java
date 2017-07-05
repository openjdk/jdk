/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4095165 4321151
 * @key intermittent
 * @summary synopsis: activator should restart daemon services
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 *          java.base/sun.nio.ch
 * @build TestLibrary RMID RMIDSelectorProvider ActivationLibrary ActivateMe RestartService_Stub
 * @run main/othervm/policy=security.policy/timeout=240 RestartService
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Vector;
import java.util.Properties;

public class RestartService
        implements ActivateMe, Runnable
{

    private ActivationID id;
    private static Object lock = new Object();
    private Vector responders = new Vector();

    private static final String RESTARTABLE = "restartable";
    private static final String ACTIVATABLE = "activatable";


    public RestartService(ActivationID id, MarshalledObject mobj)
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

        obj.ping(responder);
    }

    public RestartService() throws RemoteException {
        UnicastRemoteObject.exportObject(this, 0);
    }

    public void ping(String responder) {
        System.err.println("RestartService: received ping from " + responder);
        synchronized (lock) {
            responders.add(responder);
            lock.notify();
        }
    }

    public boolean receivedPing(String responder) {
        return responders.contains(responder);
    }

    public ActivateMe getUnicastVersion() throws RemoteException {
        return new RestartService();
    }

    public ActivationID getID() {
        return id;
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"RestartService")).start();
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {

    }

    public static void main(String[] args) {

        System.out.println("\nRegression test for bug 4095165\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        RMID rmid = null;
        RestartService unicastObj = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMIDOnEphemeralPort();
            rmid.start();

            /* Cause activation groups to have a security policy that will
             * allow security managers to be downloaded and installed
             */
            Properties p = new Properties();
            // this test must always set policies/managers in its
            // activation groups
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",  "");

            /*
             * Create unicast object to be contacted when service is activated.
             */
            unicastObj = new RestartService();
            /*
             * Create and register descriptors for a restartable and
             * non-restartable service (respectively) in a group other than
             * this VM's group.
             */
            System.err.println("Creating descriptors");

            Object[] stuff = new Object[] { RESTARTABLE, unicastObj };
            MarshalledObject restartMobj = new MarshalledObject(stuff);
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);

            stuff[0] = ACTIVATABLE;
            MarshalledObject activateMobj = new MarshalledObject(stuff);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);
            ActivationDesc restartableDesc =
                new ActivationDesc(groupID, "RestartService", null,
                                   restartMobj, true);

            ActivationDesc activatableDesc =
                new ActivationDesc(groupID, "RestartService", null,
                                   activateMobj, false);

            System.err.println("Registering descriptors");
            ActivateMe restartableObj =
                (ActivateMe) Activatable.register(restartableDesc);

            ActivateMe activatableObj =
                (ActivateMe) Activatable.register(activatableDesc);

            /*
             * Restart rmid; it should start up the restartable service
             */
            rmid.restart();

            /*
             * Wait for service to be automatically restarted.
             */
            boolean gotPing = false;
            for (int i = 0; i < 15; i++) {
                synchronized (lock) {
                    if (unicastObj.receivedPing(RESTARTABLE) != true) {
                        lock.wait(5000);
                        if (unicastObj.receivedPing(RESTARTABLE) == true) {
                            System.err.println("Test1 passed: rmid restarted" +
                                               " service");
                            gotPing = true;
                            break;
                        }
                    } else {
                        gotPing = true;
                        break;
                    }
                }
            }

            if (gotPing == false)
                TestLibrary.bomb("Test1 failed: service not restarted by timeout", null);

            /*
             * Make sure activatable services wasn't automatically restarted.
             */
            synchronized (lock) {
                if (unicastObj.receivedPing(ACTIVATABLE) != true) {
                    lock.wait(5000);
                    if (unicastObj.receivedPing(ACTIVATABLE) != true) {
                        System.err.println("Test2 passed: rmid did not " +
                                           "restart activatable service");
                        return;
                    }
                }

                TestLibrary.bomb("Test2 failed: activatable service restarted!", null);
            }


        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            rmid.cleanup();
            TestLibrary.unexport(unicastObj);
        }
    }
}
