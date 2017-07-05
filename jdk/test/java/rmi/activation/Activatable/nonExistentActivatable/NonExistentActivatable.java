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
 * @bug 4115296
 * @summary synopsis: NoSuchObjectException not thrown for non-existent
 * activatable objects
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     ActivateMe NonExistentActivatable_Stub
 * @run main/othervm/policy=security.policy/timeout=240 NonExistentActivatable
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

public class NonExistentActivatable
        extends Activatable
        implements ActivateMe, Runnable
{

    public NonExistentActivatable(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
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
    public void shutdown() throws Exception
    {
        (new Thread(this,"NonExistentActivatable")).start();
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

        System.out.println("\nRegression test for bug 4115331\n");

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

            System.err.println("Create activation group in this VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            ActivationGroup.createGroup(groupID, groupDesc, 0);

            System.err.println("Creating descriptor");
            ActivationDesc desc =
                new ActivationDesc("NonExistentActivatable", null, null);

            System.err.println("Registering descriptor");
            ActivateMe obj = (ActivateMe) Activatable.register(desc);

            System.err.println("Activate object via method call");
            obj.ping();

            System.err.println("Unregister object");
            obj.unregister();

            System.err.println("Make object inactive");
            obj.shutdown();

            System.err.println("Reactivate object");
            try {
                obj.ping();
            } catch (NoSuchObjectException e) {
                System.err.println("Test succeeded: " +
                                   "NoSuchObjectException caught");
                return;
            } catch (Exception e) {
                TestLibrary.bomb("Test failed: exception other than NoSuchObjectException",
                     e);
            }

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            rmid.cleanup();
        }
    }
}
