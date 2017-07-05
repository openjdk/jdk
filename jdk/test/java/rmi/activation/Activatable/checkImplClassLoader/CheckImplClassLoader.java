/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4289544
 * @summary ActivationGroupImpl.newInstance does not set context classloader for impl
 * @author Laird Dornin; code borrowed from Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID
 *     MyRMI ActivatableImpl ActivatableImpl ActivatableImpl_Stub
 * @run main/othervm/policy=security.policy/timeout=150 CheckImplClassLoader
 */

import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.net.URL;

/**
 * sun.rmi.server.ActivationGroupImpl.newInstance() needs to set the
 * context class loader when it constructs the implementation class of
 * an Activatable object.  It needs to set the ccl to be the class
 * loader of the implementation class.
 *
 * Test creates an Activatable object whose impl is loaded outside of
 * CLASSPATH.  The impls constructor checks to make sure that the
 * correct context class loader has been set when the constructor is
 * invoked.
 */
public class CheckImplClassLoader {

    private static Object dummy = new Object();
    private static MyRMI myRMI = null;
    private static ActivationGroup group = null;

    public static void main(String args[]) {
        /*
         * The following line is required with the JDK 1.2 VM because
         * of gc hocus pocus that may no longer be needed with an
         * exact vm (hotspot).
         */
        Object dummy1 = new Object();
        RMID rmid = null;

        System.err.println("\nRegression test for bug/rfe 4289544\n");

        try {

            URL implcb = TestLibrary.installClassInCodebase("ActivatableImpl",
                                                            "implcb");
            TestLibrary.installClassInCodebase("ActivatableImpl_Stub",
                                               "implcb");
            TestLibrary.suggestSecurityManager(
                TestParams.defaultSecurityManager);

            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            System.err.println("Create activation group in this VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(null, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            group = ActivationGroup.createGroup(groupID, groupDesc, 0);

            ActivationDesc desc = new ActivationDesc("ActivatableImpl",
                                                     implcb.toString(), null);
            myRMI = (MyRMI) Activatable.register(desc);

            System.err.println("Checking that impl has correct " +
                               "context class loader");
            if (!myRMI.classLoaderOk()) {
                TestLibrary.bomb("incorrect context class loader for " +
                                 "activation constructor");
            }

            System.err.println("Deactivate object via method call");
            myRMI.shutdown();

            System.err.println("\nsuccess: CheckImplClassLoader test passed ");

        } catch (Exception e) {
            TestLibrary.bomb("\nfailure: unexpected exception ", e);
        } finally {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }

            myRMI = null;
            System.err.println("rmid shut down");
            ActivationLibrary.rmidCleanup(rmid);
            TestLibrary.unexport(group);
        }
    }
}
