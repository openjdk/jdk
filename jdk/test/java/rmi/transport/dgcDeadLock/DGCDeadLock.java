/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4118056
 *
 * @summary synopsis: Distributed Garbage Collection Deadlock
 * @author Laird Dornin
 *
 * @library ../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @build TestLibrary Test TestImpl TestImpl_Stub
 * @run main/othervm/policy=security.policy/timeout=360 DGCDeadLock
 */

/* This test attempts to cause a deadlock between the rmi leaseChecker
 * thread and a thread that is servicing a dgc clean call. Before the
 * fix for this bug was implemented, deadlock could occur when the
 * leaseChecker held the lock on the lease table and the clean thread
 * held the lock on a target x. The clean thread would attempt to get
 * the lock on the leaseTable to do DGCImpl.unregisterTarget and the
 * leaseChecker would attempt to get the lock on x to do
 * Target.vmidDead.  Each thread held a resource that the other thread
 * was attempting to lock.
 *
 * This test causes the above conditions to occur and waits to see if
 * a given set of remote calls finishes "quickly enough."
 */

import java.rmi.*;
import java.io.*;

public class DGCDeadLock implements Runnable {
    private static final int REGISTRY_PORT = TestLibrary.getUnusedRandomPort();
    final static public int HOLD_TARGET_TIME = 25000;
    public static int TEST_FAIL_TIME = HOLD_TARGET_TIME + 30000;
    public static boolean finished = false;
    static DGCDeadLock test = new DGCDeadLock();

    static {
        System.setProperty("sun.rmi.transport.cleanInterval", "50");
    }

    static public void main(String[] args) {

        JavaVM testImplVM = null;

        System.err.println("\nregression test for 4118056\n");
        TestLibrary.suggestSecurityManager("java.rmi.RMISecurityManager");

        try {
            String options = " -Djava.security.policy=" +
                TestParams.defaultPolicy +
                " -XaddExports:java.rmi/sun.rmi.registry=ALL-UNNAMED" +
                " -XaddExports:java.rmi/sun.rmi.server=ALL-UNNAMED" +
                " -XaddExports:java.rmi/sun.rmi.transport=ALL-UNNAMED" +
                " -XaddExports:java.rmi/sun.rmi.transport.tcp=ALL-UNNAMED" +
                " -Djava.rmi.dgc.leaseValue=500000" +
                "  -Dsun.rmi.dgc.checkInterval=" +
                (HOLD_TARGET_TIME - 5000) +
                "   -Drmi.registry.port=" + REGISTRY_PORT +
                "" ;

            testImplVM = new JavaVM("TestImpl", options, "");
            testImplVM.start();

            synchronized (test) {
                Thread t = new Thread(test);
                t.setDaemon(true);
                t.start();

                // wait for the remote calls to take place
                test.wait(TEST_FAIL_TIME);
            }

            if (!finished) {
                TestLibrary.bomb("Test failed, had exception or exercise" +
                                           " routines took too long to " +
                                           "execute");
            }
            System.err.println("Test passed, exercises " +
                               "finished in time.");

        } catch (Exception e) {
            testImplVM = null;
            TestLibrary.bomb("test failed", e);
        }
    }

    public void run() {
        try {
            String echo = null;

            // give the test remote object time to initialize.
            Thread.currentThread().sleep(8000);

            // create a test client
            Test foo = (Test) Naming.lookup("rmi://:" +
                                            REGISTRY_PORT +
                                            "/Foo");
            echo = foo.echo("Hello world");
            System.err.println("Test object created.");

            /* give TestImpl time to lock the target in the
             * object table and any dirtys finish.
             */
            Thread.currentThread().sleep(5000);

            //unreference "Foo"
            foo = null;

            //garbage collect and finalize foo
            Runtime.getRuntime().gc();
            Runtime.getRuntime().runFinalization();

            //import "Bar"
            Test bar = (Test) Naming.lookup("rmi://:" +
                                            REGISTRY_PORT +
                                            "/Bar");

            /* infinite loop to show the liveness of Client,
             * if we have deadlock remote call will not return
             */
            try {
                for (int i = 0; i < 500; i++) {
                    echo = bar.echo("Remote call" + i);
                    Thread.sleep(10);
                }

                // flag exercises finished
                finished = true;

            } catch (RemoteException e) {
            }

        } catch (Exception e) {
            TestLibrary.bomb("test failed", e);
        } finally {
        }
    }
}
