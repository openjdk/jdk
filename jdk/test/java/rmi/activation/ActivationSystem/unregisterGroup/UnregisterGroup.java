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
 * @bug 4134233
 * @bug 4213186
 * @summary synopsis: ActivationSystem.unregisterGroup should unregister objects in group
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 *          java.base/sun.nio.ch
 * @build TestLibrary RMID ActivationLibrary ActivateMe RMIDSelectorProvider
 * @run main/othervm/policy=security.policy UnregisterGroup
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.util.Properties;

public class UnregisterGroup extends Activatable implements ActivateMe
{
    private static volatile Exception exception = null;
    private static volatile String error = null;
    private static volatile boolean done = false;
    private static final int NUM_OBJECTS = 10;

    public UnregisterGroup(ActivationID id, MarshalledObject mobj)
        throws Exception
    {
        super(id, 0);
    }

    /**
     * Does nothing, but serves to activate this object.
     */
    public void ping() { }

    /**
     * Deactivates the object. We need to unexport forcibly because
     * this call is in-progress on this object, which is the same object
     * that we are trying to deactivate.
     */
    public void shutdown() throws Exception {
        Activatable.unexportObject(this, true);
        ActivationLibrary.deactivate(this, getID());
    }

    public static void main(String[] args) throws RemoteException {
        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");
        RMID rmid = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMIDOnEphemeralPort();
            rmid.start();

            /* Cause activation groups to have a security policy that will
             * allow security managers to be downloaded and installed
             */
            final Properties p = new Properties();
            // this test must always set policies/managers in its
            // activation groups
            p.put("java.security.policy", TestParams.defaultGroupPolicy);
            p.put("java.security.manager", TestParams.defaultSecurityManager);

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
                        System.err.println("Successfully deactivated all objects.");

                    } catch (Exception e) {
                        exception = e;
                    }

                    done = true;
                }
            };

            t.start();

            // Default jtreg timeout is two minutes.
            // Timeout ourselves after one minute so that
            // we can clean up.
            t.join(60000);

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
            rmid.cleanup();
        }
    }
}
