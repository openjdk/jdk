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
 * @bug 4109103
 * @summary rmid should annotate child process output
 *
 * @author Laird Dornin; code borrowed from Ann Wollrath
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID MyRMI CheckAnnotations_Stub
 * @run main/othervm/policy=security.policy/timeout=480 CheckAnnotations
 */

import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.security.CodeSource;
import java.util.Properties;
import java.util.StringTokenizer;


public class CheckAnnotations
    extends Activatable implements MyRMI, Runnable
{

    private static Object dummy = new Object();
    private static MyRMI myRMI = null;

    // buffers to store rmid output.
    private static ByteArrayOutputStream rmidOut = new ByteArrayOutputStream();
    private static ByteArrayOutputStream rmidErr = new ByteArrayOutputStream();

    public static void main(String args[]) {
        /*
         * The following line is required with the JDK 1.2 VM so that the
         * VM can exit gracefully when this test completes.  Otherwise, the
         * conservative garbage collector will find a handle to the server
         * object on the native stack and not clear the weak reference to
         * it in the RMI runtime's object table.
         */
        Object dummy1 = new Object();
        RMID rmid = null;

        System.err.println("\nRegression test for bug/rfe 4109103\n");

        try {

            // Set security manager according to the
            // testlibrary.
            TestLibrary.suggestSecurityManager(TestParams.defaultSecurityManager);

            // start an rmid.
            RMID.removeLog();
            rmid = RMID.createRMID(rmidOut, rmidErr, false);
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

            /* new desc - we will reuse in order to get multiple vms.*/
            System.err.println("Create activation group in this VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            ActivationGroup.createGroup(groupID, groupDesc, 0);

            ActivationDesc desc = new ActivationDesc
                ("CheckAnnotations", null, null);
            myRMI = (MyRMI) Activatable.register(desc);

            /* The test-
             * Loop a bunch of times to force activator to
             * spawn VMs (groups)
             */
            for (int i = 0; i < 3; i++) {

                // object activated in annotation check via method call
                if(!checkAnnotations(i-1)) {
                    TestLibrary.bomb("Test failed: output improperly annotated.");
                }

                /*
                 * Clean up object too.
                 */
                System.err.println
                    ("Deactivate object via method call");
                myRMI.shutdown();
            }
            System.err.println
                ("\nsuccess: CheckAnnotations test passed ");

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
        }
    }

    /**
     * check to make sure that the output from a spawned vm is
     * formatted/annotated properly.
     */
    public static boolean checkAnnotations(int iteration)
        throws IOException
    {
        try {
            Thread.sleep(5000);
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }

        /**
         * cause the spawned vm to generate output that will
         * be checked for proper annotation.  printOut is
         * actually being called on an activated implementation.
         */
        myRMI.printOut("out" + iteration);
        myRMI.printErr("err" + iteration);
        myRMI.printOut("out" + iteration);
        myRMI.printErr("err" + iteration);

        /* we have to wait for output to filter down
         * from children so we can read it before we
         * kill rmid.
         */

        String outString = null;
        String errString = null;

        for (int i = 0 ; i < 5 ; i ++ ) {
            // have to give output from rmid time to trickle down to
            // this process
            try {
                Thread.sleep(4000);
            } catch(InterruptedException e) {
            }

            outString = rmidOut.toString();
            errString = rmidErr.toString();

            if ((!outString.equals("")) &&
                (!errString.equals("")))
            {
                System.err.println("obtained annotations");
                break;
            }
            System.err.println("rmid output not yet received, retrying...");
        }

        rmidOut.reset();
        rmidErr.reset();

        // only test when we are annotating..., first run does not annotate
        if (iteration >= 0) {
            System.err.println("Checking annotations...");
            System.err.println(outString);
            System.err.println(errString);

            StringTokenizer stOut = new StringTokenizer(outString, ":");
            StringTokenizer stErr = new StringTokenizer(errString, ":");

            String execErr = null;
            String execOut = null;
            String destOut = null;
            String destErr = null;
            String outTmp  = null;
            String errTmp  = null;

            while (stOut.hasMoreTokens()) {
                execOut = outTmp;
                outTmp  = destOut;
                destOut = stOut.nextToken();
            }
            while (stErr.hasMoreTokens()) {
                execErr = errTmp;
                errTmp  = destErr;
                destErr = stErr.nextToken();
            }

            if ((execErr == null)||(errTmp == null)||
                (destErr == null)) {
                return false;
            }
            if ((execOut == null)||(outTmp == null)||
                (destOut == null)) {
                return false;
            }

            // just make sure that last two strings are what we expect.
            if (execOut.equals("ExecGroup-" + iteration)
                && (new String(destOut.substring(0,4)).equals("out" +
                                                              iteration))
                && (execErr.equals("ExecGroup-"+iteration))
                && (new String(destErr.substring(0,4)).equals("err" +
                                                              iteration)) ) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    // implementation of MyRMI, make this object activatable.
    public CheckAnnotations
        (ActivationID id, MarshalledObject mo)
     throws RemoteException {

        // register/export anonymously
        super(id,0);
    }

    public void printOut(String toPrint) {
        System.out.println(toPrint);
    }

    public void printErr(String toPrint) {
        System.err.println(toPrint);
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception {
        (new Thread(this,"CheckAnnotations")).start();
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
}
