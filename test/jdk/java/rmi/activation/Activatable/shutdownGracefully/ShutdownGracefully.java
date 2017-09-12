/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4183169 8032050
 * @summary Minor problem with the way ReliableLog handles IOExceptions.
 *
 * @author Laird Dornin; code borrowed from Ann Wollrath
 *
 * @library ../../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 *          java.base/sun.nio.ch
 * @build TestLibrary RMID RMIDSelectorProvider
 *     TestSecurityManager RegisteringActivatable ShutdownGracefully_Stub
 * @run main/othervm/policy=security.policy/timeout=700 ShutdownGracefully
 */

import java.rmi.activation.*;
import java.rmi.*;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

/**
 * The test creates an rmid with a special security manager.  After
 * rmid makes two registrations (which is greater than rmid's
 * snapshotInterval) the security manager stops allowing rmid to write
 * to update and snapshot log files in rmid's log directory.  The Test
 * registers an Activatable object twice with different group ids.
 * The second registration will cause rmid to have to write to a
 * LogFile (it causes a snapshot) and the security manager will not
 * allow the file write to happen.  The test makes sure that rmid
 * shuts down in a graceful manner without any explicit request to do
 * so.  The test will not exit for 400 seconds if rmid does not exit
 * (after that time, the test will fail).
 */
public class ShutdownGracefully
    extends Activatable implements RegisteringActivatable
{
    private static RegisteringActivatable registering = null;

    private final static long SHUTDOWN_TIMEOUT = 400 * 1000;

    public static void main(String args[]) {

        RMID rmid = null;

        // Save exception if there is a exception or expected behavior
        Exception exception = null;
        System.err.println("\nRegression test for bug/rfe 4183169\n");

        try {
            TestLibrary.suggestSecurityManager(
                "java.rmi.RMISecurityManager");

            // start an rmid.
            RMID.removeLog();
            rmid = RMID.createRMIDOnEphemeralPort();

            // rmid needs to run with a security manager that
            // simulates a log problem; rmid should also snapshot
            // quickly.
            rmid.addOptions(new String[] {
                "-Djava.security.manager=TestSecurityManager",
                "-Dsun.rmi.activation.snapshotInterval=1"});

            //      rmid.addArguments(new String[] {
            //          "-C-Djava.rmi.server.logCalls=true"});

            rmid.start();

            // Ensure that activation groups run with the correct
            // security manager.
            //
            Properties p = new Properties();
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",
                  "java.lang.SecurityManager");

            System.err.println("activation group will be created " +
                               "in a new VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);

            System.err.println("registering activatable");
            ActivationDesc desc = new ActivationDesc
                (groupID, "ShutdownGracefully", null, null);
            registering = (RegisteringActivatable)
                Activatable.register(desc);

            System.err.println("activate and deactivate object " +
                               "via method call");
            registering.shutdown();

            /*
             * the security manager rmid is running with will stop
             * rmid from writing to its log files; in 1.2.x this would
             * have caused rmid to have thrown a runtime exception and
             * continue running in an unstable state.  With the fix
             * for 4183169, rmid should shutdown gracefully instead.
             */

            /*
             * register another activatable with a new group id; rmid
             * should not recover from this...  I use two
             * registrations to more closely simulate the environment
             * in which the bug was found.  In java versions with out
             * the appropriate bug fix, rmid would hide a
             * NullPointerException in this circumstance.
             */
            p.put("dummyname", "dummyvalue");
            groupDesc = new ActivationGroupDesc(p, null);
            ActivationGroupID secondGroupID =
                system.registerGroup(groupDesc);
            desc = new ActivationDesc(secondGroupID,
                "ShutdownGracefully", null, null);

            /*
             * registration request is expected to be failed. succeeded case
             * should be recorded. And raise error after clean up  rmid.
             */
            try {
                registering = (RegisteringActivatable)
                    Activatable.register(desc);
                System.err.println("The registration request succeeded unexpectedly");
                exception = new RuntimeException("The registration request succeeded unexpectedly");
            } catch (ActivationException e) {
                System.err.println("received exception from registration " +
                                   "call that should have failed...");
                // Need wait rmid process terminates.
                try {
                    int exitCode = rmid.waitFor(SHUTDOWN_TIMEOUT);
                    System.err.println("RMID has exited gracefully with exitcode:" + exitCode);
                    rmid = null;
                } catch (TimeoutException te) {
                    System.err.println("RMID process has not exited in given time");
                    exception = te;
                }
            }
        } catch (Exception e) {
            System.err.println("Exception thrown:" + e);
            exception = e;
        } finally {
            if (rmid != null)
                rmid.cleanup();
        }
        if (exception != null)
            TestLibrary.bomb("\nexception thrown in test: ", exception);
    }

    /**
     * implementation of RegisteringActivatable
     */
    public ShutdownGracefully
        (ActivationID id, MarshalledObject mo) throws RemoteException
    {
        // register/export anonymously
        super(id, 0);
    }

    /**
     * Deactivates the object. We need to unexport forcibly because this call
     * in-progress on this object, which is the same object that we are trying
     * to deactivate.
     */
    public void shutdown() throws Exception {
        Activatable.unexportObject(this, true);
        ActivationLibrary.deactivate(this, getID());
    }
}
