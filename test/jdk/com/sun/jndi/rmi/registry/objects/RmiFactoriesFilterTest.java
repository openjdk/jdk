/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Reference;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.Hashtable;

/*
 * @test
 * @bug 8290368
 * @summary Checks if RMI specific objects factory filter system and security
 *  properties can be used to restrict usage of object factories during
 *  RMI lookup operations.
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @library ../../../../../../java/rmi/testlibrary
 * @build TestLibrary
 * @compile TestFactory.java
 *
 * @run main/othervm RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=*
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.**;!*
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.test.**;!*
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.test.*;!*
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.test.Test*;!*
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=!com.test.**
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=!com.test.TestFactory;com.**
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=!com.test.TestFactory;com.test.*
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=!com.test.Test*
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.*;!*
 *                    RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.test.TestFactor;!*
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=com.test.TestFactoryy;!*
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowRmiFilter.props
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowRmiFilter.props
 *                   -Djdk.jndi.rmi.object.factoriesFilter=com.test.TestFactory
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowRmiFilter.props
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowRmiFilter.props
 *                   -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory
 *                   RmiFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowRmiFilter.props
 *                   -Djdk.jndi.rmi.object.factoriesFilter=!com.test.TestFactory
 *                   RmiFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=.*
 *                   RmiFactoriesFilterTest false false
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=*
 *                   -Djdk.jndi.object.factoriesFilter=.*
 *                   RmiFactoriesFilterTest false false
 *
 * @run main/othervm -Djdk.jndi.rmi.object.factoriesFilter=*
 *                   -Djdk.jndi.object.factoriesFilter=*
 *                   -Djdk.jndi.ldap.object.factoriesFilter=.*
 *                   RmiFactoriesFilterTest true true
 */

public class RmiFactoriesFilterTest {

    public static void main(String[] args) throws Exception {
        boolean classExpectedToLoad = Boolean.parseBoolean(args[0]);
        boolean rmiAndGlobalFiltersValid =
                Boolean.parseBoolean(args[1]);
        int registryPort;
        try {
            Registry registry = TestLibrary.createRegistryOnEphemeralPort();
            registryPort = TestLibrary.getRegistryPort(registry);
            System.out.println("Registry port: " + registryPort);
        } catch (RemoteException re) {
            throw new RuntimeException("Failed to create registry", re);
        }

        Context context = getInitialContext(registryPort);
        // Bind the Reference object
        Reference ref = new Reference("TestObject", "com.test.TestFactory",
                null);
        context.bind("objectTest", ref);

        loadUsingFactoryFromTCCL(registryPort, classExpectedToLoad, rmiAndGlobalFiltersValid);
        if (!rmiAndGlobalFiltersValid) {
            // Check that IAE is set as NamingException cause for malformed RMI or GLOBAL
            // filter values when lookup is called for the second time
            loadUsingFactoryFromTCCL(registryPort, classExpectedToLoad, false);
        }
    }

    private static Context getInitialContext(int port) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.rmi.registry.RegistryContextFactory");
        env.put(Context.PROVIDER_URL, "rmi://127.0.0.1:" + port);
        return new InitialContext(env);
    }

    private static void loadUsingFactoryFromTCCL(int registryPort,
                                                 boolean classExpectedToLoad,
                                                 boolean rmiAndGlobalFiltersValid) {


        try {
            Context context = getInitialContext(registryPort);
            Object object = context.lookup("objectTest");
            System.out.println("Number of getObjectInstance calls:" +
                    com.test.TestFactory.getNumberOfGetInstanceCalls());
            System.out.println("Loaded class type:" + object.getClass().getCanonicalName());
            System.out.println("Loaded class: " + object);
            if (classExpectedToLoad) {
                if (!"TestObject".equals(object)) {
                    throw new AssertionError("Class was expected to get loaded by the factory");
                }
            } else {
                if ("TestObject".equals(object)) {
                    throw new AssertionError("Class was unexpectedly loaded by the factory");
                }
            }
        } catch (NamingException ne) {
            // Only expecting NamingException for cases when RMI or GLOBAL filters are malformed
            if (rmiAndGlobalFiltersValid) {
                throw new AssertionError("Unexpected NamingException observed", ne);
            }
            if (ne.getCause() instanceof IllegalArgumentException iae) {
                // All tests with malformed filters contain wildcards with
                // package name missing, therefore the message is expected
                // to start with "package missing in:"
                System.err.println("Found expected exception: " + iae);
            } else {
                throw new AssertionError("IllegalArgument exception" +
                        " is expected for malformed filter values");
            }
        }
    }
}
