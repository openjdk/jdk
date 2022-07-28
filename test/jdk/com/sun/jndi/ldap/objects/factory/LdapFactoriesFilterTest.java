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
 * @summary test how JDK system properties controls objects reconstruction
 *  from naming references with a local object factory specified
 * @modules java.naming/com.sun.jndi.ldap
 * @library /test/lib ../../lib /javax/naming/module/src/test/test/
 * @build LDAPServer LDAPTestUtils TestFactory
 *
 * @run main/othervm LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=*
 *                    LdapFactoriesFilterTest true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.**;!*
 *                   LdapFactoriesFilterTest true
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.**;!*
 *                   LdapFactoriesFilterTest true
 *
 *  @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.*;!*
 *                    LdapFactoriesFilterTest true
 *
 *  @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.Test*;!*
 *                    LdapFactoriesFilterTest true
 *
 *  @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.**
 *                    LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory;com.**
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory;com.test.*
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=!com.test.Test*
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.*;!*
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactor;!*
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactoryy;!*
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowLdapFilter.props
 *                   LdapFactoriesFilterTest false
 *
 * @run main/othervm -Djava.security.properties=${test.src}/disallowLdapFilter.props
 *                   -Djdk.jndi.ldap.object.factoriesFilter=com.test.TestFactory
 *                   LdapFactoriesFilterTest true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowLdapFilter.props
 *                   LdapFactoriesFilterTest true
 *
 * @run main/othervm -Djava.security.properties=${test.src}/allowLdapFilter.props
 *                   -Djdk.jndi.ldap.object.factoriesFilter=!com.test.TestFactory
 *                   LdapFactoriesFilterTest false
 *
 */
public class LdapFactoriesFilterTest {
    public static void main(String[] args) throws Exception {

        boolean testFactoryAllowed = Boolean.parseBoolean(args[0]);

        // Create unbound server socket
        ServerSocket serverSocket = new ServerSocket();

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

        Hashtable<Object, Object> env;
        // Initialize test environment variables
        env = LDAPTestUtils.initEnv(serverSocket, providerURL,
                LdapFactoriesFilterTest.class.getName(), args, false);
        DirContext ctx = null;

        ctx = new InitialDirContext(env);
        Exception observedException = null;
        Object lookupRes = null;
        try {
            lookupRes = ctx.lookup("Example");
            System.err.println("Lookup results: " + lookupRes.getClass().getCanonicalName());
        } catch (Exception ex) {
            observedException = ex;
        }

        if (testFactoryAllowed) {
            // we expect NamingException with RuntimeException cause here
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
                        throw new AssertionError("Unexpected RuntimeException message observed");
                    }
                } else {
                    throw new AssertionError(
                            "RuntimeException is expected to be thrown by the test object factory");
                }
            } else {
                throw new AssertionError("NamingException was not thrown as expected");
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
