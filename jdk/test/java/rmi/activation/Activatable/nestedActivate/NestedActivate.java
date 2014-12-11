/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4138056
 * @summary synopsis: Activating objects from an Activatable constructor causes deadlock
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary ActivateMe NestedActivate_Stub
 * @run main/othervm/policy=security.policy/timeout=240 NestedActivate
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

public class NestedActivate
        extends Activatable
        implements ActivateMe, Runnable
{

    private static Exception exception = null;
    private static boolean done = false;
    private ActivateMe obj = null;

    public NestedActivate(ActivationID id, MarshalledObject mobj)
        throws Exception
    {
        super(id, 0);
        System.err.println("NestedActivate<>: activating object");
        if (mobj != null) {
            System.err.println("NestedActivate<>: ping obj to activate");
            obj = (ActivateMe) mobj.get();
            obj.ping();
            System.err.println("NestedActivate<>: ping completed");
        }
    }

    public void ping()
    {}

    public void unregister() throws Exception {
        super.unregister(super.getID());
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"NestedActivate")).start();
        if (obj != null)
            obj.shutdown();
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {
        ActivationLibrary.deactivate(this, getID());
    }

    public static void main(String[] args) {

        System.err.println("\nRegression test for bug 4138056\n");

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

            Thread t = new Thread() {
                public void run () {
                    try {
                        System.err.println("Creating group descriptor");
                        ActivationGroupDesc groupDesc =
                            new ActivationGroupDesc(p, null);
                        ActivationGroupID groupID =
                            ActivationGroup.getSystem().
                            registerGroup(groupDesc);

                        System.err.println("Creating descriptor: object 1");
                        ActivationDesc desc1 =
                            new ActivationDesc(groupID, "NestedActivate",
                                               null, null);

                        System.err.println("Registering descriptor: object 1");
                        ActivateMe obj1 =
                            (ActivateMe) Activatable.register(desc1);

                        System.err.println("Creating descriptor: object 2");
                        ActivationDesc desc2 =
                            new ActivationDesc(groupID, "NestedActivate", null,
                                               new MarshalledObject(obj1));

                        System.err.println("Registering descriptor: object 2");
                        ActivateMe obj2 =
                            (ActivateMe) Activatable.register(desc2);

                        System.err.println("Activating object 2");
                        obj2.ping();

                        System.err.println("Deactivating objects");
                        obj2.shutdown();
                    } catch (Exception e) {
                        exception = e;
                    }
                    done = true;
                }
            };

            t.start();
            t.join(35000);

            if (exception != null) {
                TestLibrary.bomb("test failed", exception);
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
