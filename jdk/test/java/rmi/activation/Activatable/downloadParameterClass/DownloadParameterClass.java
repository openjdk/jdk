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
 * @bug 4149366
 * @summary The class loader used to load classes for parameter types sent in
 * an RMI call to an activatable object should delegate to the class loader
 * that loaded the class of the activatable object itself, to maximize the
 * likelihood of type compatibility between downloaded parameter types and
 * supertypes shared with the activatable object.
 * @author Peter Jones (much code taken from Ann Wollrath's activation tests)
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     Foo FooReceiverImpl FooReceiverImpl_Stub Bar
 * @run main/othervm/policy=security.policy/timeout=240 DownloadParameterClass
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;

public class DownloadParameterClass {

    public interface FooReceiver extends Remote {

        /*
         * The interface can't actually declare that the method takes a
         * Foo, because then Foo would have to be in the test's CLASSPATH,
         * which might get propagated to the group VM's CLASSPATH, which
         * would nullify the test (the Foo supertype must be loaded in the
         * group VM only through the class loader that loaded the
         * activatable object).
         */
        public void receiveFoo(Object obj) throws RemoteException;
    }

    public static void main(String[] args) {

        System.err.println("\nRegression test for bug 4149366\n");

        /*
         * Install classes to be seen by the activatable object's class
         * loader in the "codebase1" subdirectory of working directory, and
         * install the subtype to be downloaded into the activatable object
         * into the "codebase2" subdirectory.
         */
        URL codebase1 = null;
        URL codebase2 = null;
        try {
            codebase1 = TestLibrary.installClassInCodebase("FooReceiverImpl", "codebase1");
            TestLibrary.installClassInCodebase("FooReceiverImpl_Stub", "codebase1");
            TestLibrary.installClassInCodebase("Foo", "codebase1");
            codebase2 = TestLibrary.installClassInCodebase("Bar", "codebase2");
        } catch (MalformedURLException e) {
            TestLibrary.bomb("failed to install test classes", e);
        }

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
             * Create and register descriptors for activatable object in a
             * group other than this VM's group, so that another VM will be
             * spawned with the object is activated.
             */
            System.err.println("Creating descriptors");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationGroupID groupID =
                ActivationGroup.getSystem().registerGroup(groupDesc);
            ActivationDesc objDesc =
                new ActivationDesc(groupID, "FooReceiverImpl",
                                   codebase1.toString(), null, false);

            System.err.println("Registering descriptors");
            FooReceiver obj = (FooReceiver) Activatable.register(objDesc);

            /*
             * Create an instance of the subtype to be downloaded by the
             * activatable object.  The codebase must be a path including
             * "codebase1" as well as "codebase2" because the supertype
             * must be visible here as well; the supertype cannot be
             * installed in both codebases (like it would be in a typical
             * setup) because of the trivial installation mechanism used
             * below, and see the comment above for why it can't be in
             * the test's CLASSPATH.
             */
            Class subtype = RMIClassLoader.loadClass(
                codebase2 + " " + codebase1, "Bar");
            Object subtypeInstance = subtype.newInstance();

            obj.receiveFoo(subtypeInstance);

            System.err.println("\nTEST PASSED\n");

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
