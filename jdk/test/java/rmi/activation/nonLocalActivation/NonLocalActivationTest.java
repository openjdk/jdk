/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.rmi.AccessException;
import java.rmi.activation.ActivationSystem;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Set;

/*
 * @test
 * @bug 8174770
 * @summary Verify that ActivationSystem rejects non-local access.
 *    The test is manual because the (non-local) host running rmid must be supplied as a property.
 * @run main/manual/othervm -Dactivation.host=rmid-host NonLocalActivationTest
 */

/**
 * Lookup the ActivationSystem on a different host and invoke its remote interface methods.
 * They should all throw an exception, non-local access is prohibited.
 *
 * This test is a manual test and uses rmid running on a *different* host.
 * The default port (1098) for the Activation System is ok and expected.
 * Login or ssh to the different host and invoke {@code $JDK_HOME/bin/rmid}.
 * It will not show any output.
 *
 * On the first host modify the @run command above to replace "rmid-host"
 * with the hostname or IP address of the different host and run the test with jtreg.
 */
public class NonLocalActivationTest
{
    public static void main(String[] args) throws Exception {

        String host = System.getProperty("activation.host");
        if (host == null || host.isEmpty()) {
            throw new RuntimeException("Specify host with system property: -Dactivation.host=<host>");
        }

        // Check if running the test on a local system; it only applies to remote
        String myHostName = InetAddress.getLocalHost().getHostName();
        Set<InetAddress> myAddrs = Set.of(InetAddress.getAllByName(myHostName));
        Set<InetAddress> hostAddrs = Set.of(InetAddress.getAllByName(host));
        if (hostAddrs.stream().anyMatch(i -> myAddrs.contains(i))
                || hostAddrs.stream().anyMatch(h -> h.isLoopbackAddress())) {
            throw new RuntimeException("Error: property 'activation.host' must not be the local host%n");
        }

        // Locate the registry operated by the ActivationSystem
        // Test SystemRegistryImpl
        Registry registry = LocateRegistry.getRegistry(host, ActivationSystem.SYSTEM_PORT);
        try {
            // Verify it is an ActivationSystem registry
            registry.lookup("java.rmi.activation.ActivationSystem");
        } catch (Exception nf) {
            throw new RuntimeException("Not a ActivationSystem registry, does not contain java.rmi.activation.ActivationSystem", nf);
        }

        try {
            registry.bind("foo", null);
            throw new RuntimeException("Remote access should not succeed for method: bind");
        } catch (Exception e) {
            assertIsAccessException(e, "Registry.bind");
        }

        try {
            registry.rebind("foo", null);
            throw new RuntimeException("Remote access should not succeed for method: rebind");
        } catch (Exception e) {
            assertIsAccessException(e, "Registry.rebind");
        }

        try {
            registry.unbind("foo");
            throw new RuntimeException("Remote access should not succeed for method: unbind");
        } catch (Exception e) {
            assertIsAccessException(e, "Registry.unbind");
        }


        // Locate the ActivationSystem on the specified host and default port.
        // Test each of the ActivationSystem methods
        ActivationSystem as = (ActivationSystem) registry.lookup("java.rmi.activation.ActivationSystem");

        // Argument is not material, access check is before arg processing

        try {
            as.registerGroup(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.getActivationDesc(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.getActivationGroupDesc(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.registerObject(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.unregisterGroup(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.unregisterObject(null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.setActivationDesc(null, null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }

        try {
            as.setActivationGroupDesc(null, null);
        } catch (Exception aex) {
            assertIsAccessException(aex, "ActivationSystem.nonLocalAccess");
        }
    }

    /**
     * Check the exception chain for the expected AccessException and message.
     * @param ex the exception from the remote invocation.
     */
    private static void assertIsAccessException(Exception ex, String msg1) {
        Throwable t = ex;
        System.out.println();
        while (!(t instanceof AccessException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof AccessException) {
            String msg = t.getMessage();
            int asIndex = msg.indexOf(msg1);
            int disallowIndex = msg.indexOf("disallowed");
            int nonLocalHostIndex = msg.indexOf("non-local host");
            if (asIndex < 0 ||
                    disallowIndex < 0 ||
                    nonLocalHostIndex < 0 ) {
                throw new RuntimeException("exception message is malformed", t);
            }
            System.out.printf("Found expected AccessException: %s%n", t);
        } else {
            throw new RuntimeException("AccessException did not occur", ex);
        }
    }
}
