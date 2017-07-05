/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4720528
 * @summary synopsis: (spec) ActivationSystem.activeGroup spec should be
 * relaxed (duplicate call to activeGroup with same instantiator and
 * incarnation should not throw ActivationException; it should succeed)
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 * @run main/othervm/policy=security.policy/timeout=480 IdempotentActiveGroup
 */

import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.activation.ActivationSystem;
import java.rmi.server.UnicastRemoteObject;

public class IdempotentActiveGroup {

    public static void main(String[] args) {

        System.err.println("\nRegression test for bug 4720528\n");

        TestLibrary.suggestSecurityManager("java.lang.SecurityManager");
        RMID rmid = null;
        ActivationInstantiator inst1 = null;
        ActivationInstantiator inst2 = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            System.err.println("Create group descriptor");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(null, null);
            ActivationSystem system = ActivationGroup.getSystem();
            System.err.println("Register group descriptor");
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            inst1 = new FakeInstantiator();
            inst2 = new FakeInstantiator();

            System.err.println("Invoke activeGroup with inst1");
            system.activeGroup(groupID, inst1, 0);

            try {
            System.err.println("Invoke activeGroup with inst2");
                system.activeGroup(groupID, inst2, 0);
                throw new RuntimeException(
                    "TEST FAILED: activeGroup with unequal groups succeeded!");
            } catch (ActivationException expected) {
                System.err.println("Caught expected ActivationException");
                System.err.println("Test 1 (of 2) passed");
            }

            try {
                System.err.println("Invoke activeGroup with inst1");
                system.activeGroup(groupID, inst1, 0);
                System.err.println("activeGroup call succeeded");
                System.err.println("Test 2 (of 2) passed");
            } catch (ActivationException unexpected) {
                throw new RuntimeException(
                    "TEST FAILED: activeGroup with equal groups failed!",
                    unexpected);
            }

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            try {
                if (inst1 != null) {
                    UnicastRemoteObject.unexportObject(inst1, true);
                }
                if (inst2 != null) {
                    UnicastRemoteObject.unexportObject(inst2, true);
                }
            } catch (NoSuchObjectException unexpected) {
                throw new AssertionError(unexpected);
            }
            ActivationLibrary.rmidCleanup(rmid);
        }
    }

    private static class FakeInstantiator
        extends UnicastRemoteObject
        implements ActivationInstantiator
    {
        FakeInstantiator() throws RemoteException {}

        public MarshalledObject newInstance(ActivationID id,
                                            ActivationDesc desc)
        {
            throw new AssertionError();
        }
    }
}
