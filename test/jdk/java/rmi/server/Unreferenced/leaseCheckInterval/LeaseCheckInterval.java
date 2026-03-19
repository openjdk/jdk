/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4285878
 * @summary When the "java.rmi.dgc.leaseValue" system property is set to a
 * value much lower than its default (10 minutes), then the server-side
 * user-visible detection of DGC lease expiration-- in the form of
 * Unreferenced.unreferenced() invocations and possibly even local garbage
 * collection (including weak reference notification, finalization, etc.)--
 * may be delayed longer than expected.  While this is not a spec violation
 * (because there are no timeliness guarantees for any of these garbage
 * collection-related events), the user might expect that an unreferenced()
 * invocation for an object whose last client has terminated abnormally
 * should occur on relatively the same time order as the lease value
 * granted.
 *
 * @library ../../../testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @build TestLibrary JavaVM LeaseCheckInterval_Stub SelfTerminator
 * @run main/othervm LeaseCheckInterval
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

public class LeaseCheckInterval implements Remote, Unreferenced {

    public static final String BINDING = "LeaseCheckInterval";
    // lease expiry time (milliseconds)
    private static final long LEASE_VALUE = 10000;
    // the maximum allowed duration between the lease expiration and
    // the Unreferenced.unreferenced() callback method to be invoked
    private static final Duration EXPECTED_MAX_DURATION = Duration.ofMinutes(1);

    // will be counted down when Unreferenced.unreferenced() is invoked
    private static final CountDownLatch callbackInvocationLatch = new CountDownLatch(1);

    @Override
    public void unreferenced() {
        System.err.println("[" + Instant.now() + "] unreferenced() method invoked");
        callbackInvocationLatch.countDown();
    }

    public static void main(String[] args) throws Exception {
        /*
         * Set the duration of leases granted to a very small value, so that
         * we can test if expirations are detected in a roughly comparable
         * time.
         */
        System.setProperty("java.rmi.dgc.leaseValue", String.valueOf(LEASE_VALUE));
        System.err.println("running test with java.rmi.dgc.leaseValue set to " + LEASE_VALUE);
        LeaseCheckInterval obj = new LeaseCheckInterval();
        JavaVM jvm = null;

        try {
            UnicastRemoteObject.exportObject(obj);
            System.err.println("exported remote object");

            Registry localRegistry = TestLibrary.createRegistryOnEphemeralPort();
            int registryPort = TestLibrary.getRegistryPort(localRegistry);
            System.err.println("created local registry on port " + registryPort);

            localRegistry.bind(BINDING, obj);
            System.err.println("bound remote object in local registry");

            System.err.println("starting remote client VM...");
            jvm = new JavaVM("SelfTerminator", "-Drmi.registry.port=" + registryPort, "");
            // launch the self terminating java application which will lookup
            // the bound object (thus creating a lease) and then terminate itself (thus
            // creating the condition for a lease expiry).
            jvm.start();
            final Instant startTime = Instant.now();
            System.err.println("waiting for SelfTerminator process to complete");
            final int exitCode = jvm.waitFor();
            if (exitCode != 0) {
                throw new AssertionError("SelfTerminator process exited with" +
                        " a non-zero exit code: " + exitCode);
            }
            System.err.println("SelfTerminator process completed in "
                    + Duration.between(startTime, Instant.now())
                    + ", now waiting for Unreferenced.unreferenced() callback to be invoked");
            callbackInvocationLatch.await();
            final Instant waitEndedAt = Instant.now();
            final Duration waitDuration = assertWithinExpectedTimeLimit(waitEndedAt, startTime);
            System.err.println("TEST PASSED: unreferenced() invoked in timely" +
                    " fashion (duration=" + waitDuration + ")");
        } finally {
            if (jvm != null) {
                jvm.destroy();
            }
            /*
             * When all is said and done, try to unexport the remote object
             * so that the VM has a chance to exit.
             */
            try {
                UnicastRemoteObject.unexportObject(obj, true);
            } catch (RemoteException e) {
            }
        }
    }

    /*
     * Verifies that the duration between the lease expiration and the callback
     * invocation is within the expected limit. Throws an exception if the wait
     * duration is larger than expected limit, else returns the actual wait duration.
     */
    private static Duration assertWithinExpectedTimeLimit(final Instant waitEndedAt,
                                                          final Instant terminationStartedAt) {

        final Duration waitDuration = Duration.between(terminationStartedAt, waitEndedAt);
        System.out.println("wait completed in " + waitDuration);
        if (waitDuration.compareTo(EXPECTED_MAX_DURATION) > 0) {
            throw new RuntimeException("Took unexpectedly long (duration=" +
                    waitDuration + ") to invoke Unreferenced.unreferenced()," +
                    " expected max duration=" + EXPECTED_MAX_DURATION);
        }
        return waitDuration;
    }
}
