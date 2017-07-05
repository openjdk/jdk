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
 * @bug 4127754
 *
 * @summary synopsis: need to modify registered ActivationDesc and
 * ActivationGroupDesc
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     ActivateMe ModifyDescriptor_Stub
 * @run main/othervm/policy=security.policy/timeout=240 ModifyDescriptor
 */

import java.io.*;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.*;

public class ModifyDescriptor
        implements ActivateMe, Runnable
{

    private ActivationID id;
    private String message;

    private static final String MESSAGE1 = "hello";
    private static final String MESSAGE2 = "hello, again";


    public ModifyDescriptor(ActivationID id, MarshalledObject mobj)
        throws ActivationException, RemoteException
    {
        this.id = id;
        Activatable.exportObject(this, id, 0);

        try {
            message = (String) mobj.get();
        } catch (Exception e) {
            System.err.println("unable to get message from marshalled object");
        }
    }

    public String getMessage() {
        return message;
    }

    public String getProperty(String name) {
        return TestLibrary.getProperty(name, null);
    }

    public ActivationID getID() {
        return id;
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"ModifyDescriptor")).start();
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

        System.out.println("\nRegression test for bug 4127754\n");

        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        RMID rmid = null;

        try {
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            /*
             * Create and register a group and activatable object
             */

            System.err.println("Creating group descriptor");
            Properties props = new Properties();
            props.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            props.put("java.security.manager",
                  TestParams.defaultSecurityManager);
            props.put("test.message", MESSAGE1);
            ActivationGroupDesc initialGroupDesc =
                new ActivationGroupDesc(props, null);
            System.err.println("Registering group");
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(initialGroupDesc);

            System.err.println("Creating descriptor");
            ActivationDesc initialDesc =
                new ActivationDesc(groupID, "ModifyDescriptor", null,
                                   new MarshalledObject(MESSAGE1), false);

            System.err.println("Registering descriptor");
            ActivateMe obj = (ActivateMe) Activatable.register(initialDesc);

            /*
             * Ping object and verify that MarshalledObject is okay.
             */
            System.err.println("Ping object");
            String message1 = obj.getMessage();
            System.err.println("message = " + message1);

            if (message1.equals(MESSAGE1)) {
                System.err.println("Test1a passed: initial MarshalledObject " +
                                   "correct");
            } else {
                TestLibrary.bomb("Test1 failed: unexpected MarshalledObject passed to " +
                     "constructor", null);
            }

            /*
             * Get property from remote group and make sure it's okay
             */
            message1 = obj.getProperty("test.message");
            if (message1.equals(MESSAGE1)) {
                System.err.println("Test1b passed: initial group property " +
                                   "correct");
            } else {
                TestLibrary.bomb("Test1 failed: unexpected property passed to " +
                     "group", null);
            }

            /*
             * Update activation descriptor for object and group
             */
            System.err.println("Update activation descriptor");
            ActivationDesc newDesc =
                new ActivationDesc(groupID, "ModifyDescriptor", null,
                               new MarshalledObject(MESSAGE2), false);
            ActivationID id = obj.getID();
            ActivationDesc oldDesc = system.setActivationDesc(id, newDesc);

            if (oldDesc.equals(initialDesc)) {
                System.err.println("Test2a passed: desc returned from " +
                                   "setActivationDesc is okay");
            } else {
                TestLibrary.bomb("Test2a failed: desc returned from setActivationDesc " +
                     "is not the initial descriptor!", null);
            }


            Properties props2 = new Properties();
            props2.put("test.message", MESSAGE2);
            props2.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            props2.put("java.security.manager",
                  TestParams.defaultSecurityManager);
            ActivationGroupDesc newGroupDesc =
                new ActivationGroupDesc(props2, null);

            ActivationGroupDesc oldGroupDesc =
                system.setActivationGroupDesc(groupID, newGroupDesc);

            if (oldGroupDesc.equals(initialGroupDesc)) {
                System.err.println("Test2b passed: group desc returned from " +
                                   "setActivationGroupDesc is okay");
            } else {
                TestLibrary.bomb("Test2b failed: group desc returned from " +
                     "setActivationGroupDesc is not the initial descriptor!",
                     null);
            }

            /*
             * Restart rmid; and ping object to make sure that it has
             * new message.
             */
            rmid.restart();

            System.err.println("Ping object after restart");
            String message2 = obj.getMessage();

            if (message2.equals(MESSAGE2)) {
                System.err.println("Test3a passed: setActivationDesc takes " +
                                   "effect after a restart");
            } else {
                TestLibrary.bomb("Test3a failed: setActivationDesc did not take effect " +
                     "after a restart", null);
            }

            message2 = obj.getProperty("test.message");

            if (message2.equals(MESSAGE2)) {
                System.err.println("Test3b passed: setActivationGroupDesc " +
                                   "takes effect after a restart");
            } else {
                TestLibrary.bomb("Test3b failed: setActivationGroupDesc did not take " +
                     "effect after a restart", null);
            }

            System.err.println("Get activation descriptor");
            ActivationDesc latestDesc = system.getActivationDesc(id);

            if (latestDesc.equals(newDesc)) {
                System.err.println("Test4a passed: desc is same as latest");
            } else {
                TestLibrary.bomb("Test4a failed: there is no way this would happen", null);
            }

            System.err.println("Get activation group descriptor");
            ActivationGroupDesc latestGroupDesc =
                system.getActivationGroupDesc(groupID);

            if (latestGroupDesc.equals(newGroupDesc)) {
                System.err.println("Test4b passed: group desc is same as " +
                                   "latest");
            } else {
                TestLibrary.bomb("Test4b failed: there is no way this would happen", null);
            }

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
