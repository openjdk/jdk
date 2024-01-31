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

import jdk.test.lib.net.URIBuilder;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.Hashtable;

/*
 * @test
 * @bug 8290368
 * @summary Checks if LDAP specific objects factory filter system and security
 *  properties can be used to restrict usage of object factories during
 *  LDAP lookup operations.
 * @modules java.naming/com.sun.jndi.ldap
 * @library /test/lib ../../lib /javax/naming/module/src/test/test/
 * @build LDAPServer LDAPTestUtils TestFactory
 *
 * @run main/othervm LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=*
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.**;!*
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.**;!*
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.*;!*
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.Test*;!*
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.**
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory;com.**
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory;com.test.*
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.Test*
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.*;!*
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactor;!*
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactoryy;!*
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowLdapFilter.props
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowLdapFilter.props
 *                   -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactory
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowLdapFilter.props
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowLdapFilter.props
 *                   -Djdk.jndi.rmi.object.factoriesFilter=!com.test.TestFactory
 *                   LdapFactoriesFilterTest true true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowLdapFilter.props
 *                   -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory
 *                   LdapFactoriesFilterTest false true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=.*
 *                   LdapFactoriesFilterTest false false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=*
 *                   -Djdk.jndi.object.factoriesFilter=.*
 *                   LdapFactoriesFilterTest false false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=*
 *                   -Djdk.jndi.object.factoriesFilter=*
 *                   -Djdk.jndi.rmi.object.factoriesFilter=.*
 *                   LdapFactoriesFilterTest true true
 */

public class LdapFactoriesFilterTest {
    public static void main(String[] args) throws Exception {

        boolean testFactoryAllowed = Boolean.parseBoolean(args[0]);
        boolean ldapAndGlobalFiltersValid =
                Boolean.parseBoolean(args[1]);

        // Create unbound server socket
        ServerSocket serverSocket = new ServerSocket();
        try (serverSocket) {
            // Bind it to the loopback address
            SocketAddress sockAddr = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0);
            serverSocket.bind(sockAddr);

            // Construct the provider URL for LDAPTestUtils
            String providerURL = URIBuilder.newBuilder()
                    .scheme("ldap")
                    .loopback()
                    .port(serverSocket.getLocalPort())
                    .buildUnchecked().toString();

            // Create and initialize test environment variables
            Hashtable<Object, Object> env;
            env = LDAPTestUtils.initEnv(serverSocket, providerURL,
                    LdapFactoriesFilterTest.class.getName(), args, false);
            DirContext ctx = new InitialDirContext(env);
            Exception observedException = null;
            Object lookupRes = null;

            // Lookup bound reference
            try {
                lookupRes = ctx.lookup("Example");
                System.err.println("Lookup results: " + lookupRes.getClass().getCanonicalName());
            } catch (Exception ex) {
                observedException = ex;
            }

            // Check lookup operation results
            if (testFactoryAllowed) {
                // NamingException with RuntimeException cause is expected here
                if (observedException instanceof NamingException namingException) {
                    System.err.println("Observed NamingException: " + observedException);
                    Throwable cause = namingException.getCause();
                    System.err.println("NamingException cause: " + cause);
                    // We expect RuntimeException from factory for cases when LDAP factory
                    // filter allows the test factory
                    if (cause instanceof RuntimeException rte) {
                        String rteMessage = rte.getMessage();
                        System.err.println("RuntimeException message: " + rteMessage);
                        if (!com.test.TestFactory.RUNTIME_EXCEPTION_MESSAGE.equals(rteMessage)) {
                            throw new AssertionError(
                                    "Unexpected RuntimeException message observed");
                        }
                    } else {
                        throw new AssertionError(
                                "RuntimeException is expected to be thrown" +
                                            " by the test object factory");
                    }
                } else {
                    throw new AssertionError(
                            "NamingException was not thrown as expected");
                }
            } else if (!ldapAndGlobalFiltersValid) {
                // If LDAP or GLOBAL factories filter are not properly formatted we're expecting to
                // get NamingException with IllegalArgumentException set as a cause that contains
                // formatting error message.
                // If RMI filter is not properly formatted we're not expecting IAE here since
                // this test only performing LDAP lookups
                if (observedException instanceof NamingException ne) {
                    if (ne.getCause() instanceof IllegalArgumentException iae) {
                        // All tests with malformed filters contain wildcards with
                        // package name missing, therefore the message is expected
                        // to start with "package missing in:"
                        System.err.println("Found expected exception: " + iae);
                    } else {
                        throw new AssertionError("IllegalArgumentException" +
                                " is expected for malformed filter values");
                    }
                }
            } else {
                // Object factory is not allowed by the factories filter
                // we expect reference here
                if (lookupRes instanceof Reference ref) {
                    System.err.println("Lookup result is a reference: " +
                            ref.getFactoryClassLocation() + " " + ref.getFactoryClassName());
                } else {
                    new AssertionError("Reference was not returned as a lookup result");
                }
            }
        }
    }
}
