/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.net.SocketFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8049619 8049630
 * @summary Verify that if the configured java.naming.ldap.factory.socket
 *          class name doesn't match the expectation of a SocketFactory,
 *          then a NamingException is raised
 *
 * @run junit ${test.main.class}
 */
class LdapSocketFactoryInvalidConfigTest {
    private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String ENV_PROP_SOCKET_FACTORY = "java.naming.ldap.factory.socket";

    /*
     * Verifies that if the class configured for java.naming.ldap.factory.socket
     * is not of type SocketFactory, then a NamingException is thrown.
     */
    @ParameterizedTest
    @ValueSource(classes = {BigDecimal.class, NotSocketFactoryButHasGetDefault.class})
    void testNotSocketFactory(final Class<?> klass) throws Exception {
        final Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(ENV_PROP_SOCKET_FACTORY, klass.getName());
        final NamingException ne = assertThrows(NamingException.class,
                () -> new InitialContext(env));
        if (!ne.toString().contains("not of type javax.net.SocketFactory")) {
            throw ne; // propagate the unexpected exception
        }
    }

    /*
     * Verifies that if the class configured for java.naming.ldap.factory.socket
     * does not declare a "public static SocketFactory getDefault()" method,
     * then a NamingException is thrown.
     */
    @Test
    void testNoGetDefault() throws Exception {
        final Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(ENV_PROP_SOCKET_FACTORY, NoGetDefaultSocketFactory.class.getName());
        final NamingException ne = assertThrows(NamingException.class,
                () -> new InitialContext(env));
        if (!ne.toString().contains("missing a public static getDefault() method")) {
            throw ne; // propagate the unexpected exception
        }
    }

    public static class NotSocketFactoryButHasGetDefault {
        public static SocketFactory getDefault() {
            return new SocketFactory() {
                @Override
                public Socket createSocket(String host, int port) {
                    throw new UnsupportedOperationException("wasn't expected to be invoked");
                }

                @Override
                public Socket createSocket(String host, int port,
                                           InetAddress localHost, int localPort) {
                    throw new UnsupportedOperationException("wasn't expected to be invoked");
                }

                @Override
                public Socket createSocket(InetAddress host, int port) {
                    throw new UnsupportedOperationException("wasn't expected to be invoked");
                }

                @Override
                public Socket createSocket(InetAddress address, int port,
                                           InetAddress localAddress, int localPort) {
                    throw new UnsupportedOperationException("wasn't expected to be invoked");
                }
            };
        }
    }

    public static class NoGetDefaultSocketFactory extends SocketFactory {

        @Override
        public Socket createSocket(String host, int port) {
            throw new UnsupportedOperationException("wasn't expected to be invoked");
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            throw new UnsupportedOperationException("wasn't expected to be invoked");
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            throw new UnsupportedOperationException("wasn't expected to be invoked");
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                                   int localPort) {
            throw new UnsupportedOperationException("wasn't expected to be invoked");
        }
    }
}
