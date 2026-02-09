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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Arrays;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.sun.jndi.ldap.Connection;
import com.sun.jndi.ldap.LdapClient;
import com.sun.jndi.ldap.LdapCtx;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8362268
 * @summary Verify that unexpected exceptions aren't propagated to LdapCtx callers
 *          when the LdapCtx's (internal) connection is closed due to an exception
 *          when reading/writing over the connection's stream
 * @modules java.naming/com.sun.jndi.ldap:+open
 * @library /test/lib lib/
 * @build jdk.test.lib.net.URIBuilder
 *        BaseLdapServer LdapMessage
 * @run junit/othervm ${test.main.class}
 */
class SkipAbandonRequestOnClosedConn {

    private static final String LOOKUP_NAME = "ou=People,o=FooBar";

    private static final class Server extends BaseLdapServer {

        private Server() throws IOException {
            super();
        }

        // handles and responds to the incoming LDAP request
        @Override
        protected void handleRequest(final Socket socket,
                                     final LdapMessage request,
                                     final OutputStream out) throws IOException {
            switch (request.getOperation()) {
                case SEARCH_REQUEST: {
                    System.err.println("responding to SEARCH_REQUEST with id: "
                            + request.getMessageID() + " on socket " + socket);
                    // write out some bytes as a response. it doesn't matter what those
                    // bytes are - in this test they aren't expected to reach
                    // the application code.
                    final byte[] irrelevantResponse = new byte[]{0x42, 0x42, 0x42};
                    System.err.println("Response: " + Arrays.toString(irrelevantResponse));
                    out.write(irrelevantResponse);
                    out.flush();
                    break;
                }
                default: {
                    throw new IOException("unexpected operation type: " + request.getOperation()
                            + ", request: " + request);
                }
            }
        }
    }

    private static Server server;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new Server();
        server.start();
        System.err.println("server started at " + server.getInetAddress()
                + ":" + server.getPort());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.err.println("stopping server " + server.getInetAddress()
                    + ":" + server.getPort());
            server.close();
        }
    }


    /*
     * Creates a com.sun.jndi.ldap.LdapCtx and configures its internal com.sun.jndi.ldap.Connection
     * instance with an InputStream that throws an IOException in its read() methods. The test
     * then initiates a Context.lookup() so that a LDAP request is internally issued and a LDAP
     * response is waited for. Due to the configured InputStream throwing an exception from its
     * read(), the com.sun.jndi.ldap.Connection will get closed (internally) when reading the
     * response and the main thread which was waiting for the LDAP response is woken up and notices
     * the connection closure. This test verifies that a NamingException gets thrown from the
     * lookup() in this scenario.
     */
    @Test
    void testNamingException() throws Exception {
        final Hashtable<String, String> envProps = new Hashtable<>();
        envProps.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        final String providerUrl = URIBuilder.newBuilder()
                .scheme("ldap")
                .host(server.getInetAddress().getHostAddress())
                .port(server.getPort())
                .build().toString();
        envProps.put(Context.PROVIDER_URL, providerUrl);
        // explicitly set LDAP version to 3 to prevent LDAP BIND requests
        // during LdapCtx instantiation
        envProps.put("java.naming.ldap.version", "3");
        // create a (custom) InitialContext which allows us to access the
        // LdapCtx's internal connection instance
        try (final CustomContext ctx = new CustomContext(envProps)) {
            // replace the InputStream and OutputStream of the LdapCtx's
            // internal connection, to allow us to raise exception from
            // the InputStream and OutputStream as necessary
            ctx.replaceStreams();

            System.err.println("issuing ldap request against " + providerUrl
                    + " using context " + ctx);
            // trigger the LDAP SEARCH request through the lookup call. we are not
            // interested in the returned value and are merely interested in the
            // Context.lookup(...) failing with a NamingException
            assertLookupThrowsNamingException(ctx);
        }
    }

    // assert that Context.lookup(...) raises a NamingException
    private static void assertLookupThrowsNamingException(final Context ctx) {
        try {
            final Object result = ctx.lookup(LOOKUP_NAME);
            fail("Context.lookup() was expected to throw NamingException" +
                    " but returned result " + result);
        } catch (NamingException ne) {
            // verify the NamingException is for the right reason
            if (!ne.toString().contains("LDAP connection has been closed")) {
                // unexpected exception, propagate it
                fail("NamingException is missing \"LDAP connection has been closed\" message", ne);
            }
            // expected
            System.err.println("got expected exception: " + ne);
        }
    }

    private static final class CustomContext extends InitialContext implements AutoCloseable {

        private CustomContext(final Hashtable<?, ?> environment) throws NamingException {
            super(environment);
        }

        private LdapCtx getLdapCtx() throws NamingException {
            final Context ctx = getDefaultInitCtx();
            if (ctx instanceof LdapCtx ldapCtx) {
                return ldapCtx;
            }
            throw new IllegalStateException("Not a LdapCtx: " + ctx.getClass().getName());
        }

        // using reflection, return the com.sun.jndi.ldap.Connection instance
        // from within the com.sun.jndi.ldap.LdapCtx
        private Connection getConnection() throws Exception {
            final LdapCtx ldapCtx = getLdapCtx();
            final Field clientField = ldapCtx.getClass().getDeclaredField("clnt");
            clientField.setAccessible(true);
            final LdapClient ldapClient = (LdapClient) clientField.get(ldapCtx);
            final Field connField = ldapClient.getClass().getDeclaredField("conn");
            connField.setAccessible(true);
            return (Connection) connField.get(ldapClient);
        }

        private void replaceStreams() throws Exception {
            final Connection conn = getConnection();
            assertNotNull(conn, "com.sun.jndi.ldap.Connection instance is null");
            final InputStream originalInputStream = conn.inStream;
            final OutputStream originalOutputStream = conn.outStream;
            // replace the connection's streams with our test specific streams
            conn.replaceStreams(new In(originalInputStream), new Out(originalOutputStream));
            System.err.println("replaced streams on connection: " + conn);
        }
    }

    // an OutputStream which intentionally throws a NullPointerException
    // from its write(...) methods if the OutputStream has been closed
    private static final class Out extends FilterOutputStream {
        private volatile boolean closed;

        private Out(final OutputStream underlying) {
            super(underlying);
        }

        @Override
        public void write(final int b) throws IOException {
            if (closed) {
                throw new NullPointerException("OutputStream is closed - intentional" +
                        " NullPointerException instead of IOException");
            }
            super.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (closed) {
                throw new NullPointerException("OutputStream is closed - intentional" +
                        " NullPointerException instead of IOException");
            }
            super.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            System.err.println("closing output stream " + this);
            closed = true;
            super.close();
        }
    }

    // an InputStream which intentionally throws an exception
    // from its read(...) methods.
    private static final class In extends FilterInputStream {

        private In(InputStream underlying) {
            super(underlying);
        }

        @Override
        public int read() throws IOException {
            final int v = super.read();
            System.err.println("read " + v + " from " + in
                    + ", will now intentionally throw an exception");
            throw new IOException("intentional IOException from " + In.class.getName() + ".read()");
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int v = super.read(b, off, len);
            System.err.println("read " + v + " byte(s) from " + in
                    + ", will now intentionally throw an exception");
            throw new IOException("intentional IOException from " + In.class.getName()
                    + ".read(byte[], int, int)");
        }
    }
}
