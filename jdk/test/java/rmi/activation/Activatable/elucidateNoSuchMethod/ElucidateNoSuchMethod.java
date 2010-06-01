/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4128620
 *
 * @summary synopsis: NoSuchMethodError should be elucidated
 *
 * @author Laird Dornin
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID
 * @build ActivateMe ElucidateNoSuchMethod ElucidateNoSuchMethod_Stub
 * @run main/othervm/policy=security.policy/timeout=240 ElucidateNoSuchMethod
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.Properties;

public class ElucidateNoSuchMethod
        extends Activatable
        implements ActivateMe, Runnable
{

    /**
     *  provide a constructor that alllows this object to be made
     *  activatable, or at least registered.
     */
    ElucidateNoSuchMethod(ActivationID id, int port)
        throws RemoteException
    {
        super(id, port);
    }

    /**
     * dont provide an activation constructor so that we get a no such
     * method error.
     */

    public void ping() {}

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception {
        (new Thread(this,"ElucidateNoSuchMethod")).start();
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

        System.out.println("\nRegression test for 4128620 \n");

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
                new ActivationDesc("ElucidateNoSuchMethod", null, null);

            System.err.println("Registering descriptor");
            ActivateMe obj = (ActivateMe) Activatable.register(desc);

            System.err.println("Activate object via method call");

            try {
                obj.ping();
            } catch (ActivateFailedException afe) {
                ActivationException a = (ActivationException) afe.detail;

                if (((a.detail instanceof NoSuchMethodException) ||
                     (a.detail instanceof NoSuchMethodError)) &&
                        (a.getMessage().indexOf
                      ("must provide an activation constructor") > -1)) {
                    System.err.println("\ntest passed for 4128620\n");
                } else {
                    TestLibrary.bomb("test failed", afe);
                }
            }

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
