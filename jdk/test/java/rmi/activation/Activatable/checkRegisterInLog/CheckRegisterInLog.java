/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4110548
 * @summary activate fails if rmid is restarted
 * @author Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     ActivateMe CheckRegisterInLog_Stub
 * @run main/othervm/policy=security.policy/timeout=240 CheckRegisterInLog
 */

import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import sun.rmi.server.ActivatableRef;
import java.lang.reflect.*;
import java.util.Properties;

public class CheckRegisterInLog
        extends Activatable
        implements ActivateMe, Runnable
{

    public CheckRegisterInLog(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
    {
        super(id, 0);
    }

    public void ping()
    {}

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"CheckRegisterInLog")).start();
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

    public static void main(String[] args)  {
        /*
         * The following line is required with the JDK 1.2 VM so that the
         * VM can exit gracefully when this test completes.  Otherwise, the
         * conservative garbage collector will find a handle to the server
         * object on the native stack and not clear the weak reference to
         * it in the RMI runtime's object table.
         */
        Object dummy = new Object();
        RMID rmid = null;
        ActivateMe obj;

        System.out.println("\nRegression test for bug 4110548\n");

        CheckRegisterInLog server;

        try {
            TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

            /*
             * Start up activation system daemon "rmid".
             */
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
             * Register an activation group and an object
             * in that group.
             */
            System.err.println("Creating group descriptor");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            System.err.println("Registering group");
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);

            System.err.println("Creating descriptor");
            ActivationDesc desc =
                new ActivationDesc(groupID, "CheckRegisterInLog",
                                   null, null);
            System.err.println("Registering descriptor");
            obj = (ActivateMe)Activatable.register(desc);

            /*
             * Restart rmid to force it to read the log file
             */
            rmid.restart();


            /*
             * 4212096: Give rmid time to go away - we want to make
             * sure that an attempt to activate the test object is not made
             * on the ActivationSystem that is about to be shutdown.
             */
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
            }

            /*
             * Activate the object via a method call.
             */
            System.err.println("Activate the object via method call");
            obj.ping();

            /*
             * Clean up object too.
             */
            System.err.println("Deactivate object via method call");
            obj.shutdown();

            System.err.println("\nsuccess: CheckRegisterInLog test passed ");

        } catch (Exception e) {
            System.err.println("\nfailure: unexpected exception " +
                               e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            throw new RuntimeException("CheckRegisterInLog got exception " +
                                       e.getMessage());
        } finally {
            ActivationLibrary.rmidCleanup(rmid);
        }
    }
}
