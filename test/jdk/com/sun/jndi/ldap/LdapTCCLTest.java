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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.event.EventContext;
import javax.naming.event.NamingEvent;
import javax.naming.event.NamingExceptionEvent;
import javax.naming.event.ObjectChangeListener;

import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8273874
 * @summary Verify that the system threads that LdapContext creates for its internal
 *          use do not hold on to the application specific context classloaders
 * @comment The test uses ThreadGroup.enumerate() to find live threads and check their
 *          context classsloader, ThreadGroup of virtual threads don't enumerate threads,
 *          so we skip this test when the main thread is a virtual thread.
 * @requires test.thread.factory != "Virtual"
 * @library lib/ /test/lib
 * @build BaseLdapServer
 *        LdapMessage
 *        jdk.test.lib.net.URIBuilder
 * @run junit ${test.main.class}
 */
class LdapTCCLTest {

    private static final String LOOKUP_NAME = "CN=duke";

    private static final byte BER_TYPE_LDAP_SEQUENCE = 0x30;
    private static final byte BER_TYPE_INTEGER = 0x02;
    private static final byte BER_TYPE_OCTET_STRING = 0x04;
    private static final byte BER_TYPE_ENUM = 0x0a;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_ENTRY_OP = 0x64;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP = 0x65;
    private static final byte LDAP_SEARCH_RESULT_DONE_SUCCESS = 0x00;

    private static Server server;
    private static Hashtable<String, String> envProps;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new Server();
        server.start();
        System.err.println("server started at " + server.getInetAddress()
                + ":" + server.getPort());

        final Hashtable<String, String> props = new Hashtable<>();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        final String providerUrl = URIBuilder.newBuilder()
                .scheme("ldap")
                .host(server.getInetAddress().getHostAddress())
                .port(server.getPort())
                .build().toString();
        props.put(Context.PROVIDER_URL, providerUrl);
        // explicitly set LDAP version to 3 to prevent LDAP BIND requests
        // during LdapCtx instantiation
        props.put("java.naming.ldap.version", "3");

        envProps = props;
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
     * Sets a test specific Thread context classloader and then creates a InitialContext
     * backed by a LdapCtxFactory and looks up an arbitrary name. The test then verifies
     * that none of the live Threads (including any created for the internal LDAP connection
     * management) have their context classloader set to the test specific classloader.
     */
    @Test
    void testLdapCtxCreation() throws Exception {
        try (final URLClassLoader urlc = new URLClassLoader(new URL[0])) {
            final ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Context ctx = null;
            try {
                // switch the TCCL to a test specific one
                Thread.currentThread().setContextClassLoader(urlc);

                // create the LDAP Context and initiate a lookup()
                // to allow for the underlying LDAP connection management
                // infrastructure to create the necessary Thread(s)
                ctx = new InitialContext(envProps);

                System.err.println("issuing ldap request against "
                        + envProps.get(Context.PROVIDER_URL) + " using context " + ctx);
                final Object result = ctx.lookup(LOOKUP_NAME);
                System.err.println("lookup returned " + result);
                assertNotNull(result, "Context.lookup() returned null");

                // verify that none of the live Thread(s) other than the current Thread
                // have their TCCL set to the one set by the test. i.e. verify that the
                // context classloader hasn't leaked into Thread(s) that may have been
                // created by the LDAP connection management code.
                assertTCCL(urlc, Collections.singleton(Thread.currentThread()));
            } finally {
                if (ctx != null) {
                    ctx.close();
                }
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    /*
     * Sets a test specific Thread context classloader and then creates a InitialContext
     * backed by a LdapCtxFactory and adds a NamingListener. The test then verifies
     * that none of the live Threads (including any newly created ones during the
     * NamingListener registration) have their context classloader set to the test
     * specific classloader.
     */
    @Test
    void testAddNamingListener() throws Exception {
        try (final URLClassLoader urlc = new URLClassLoader(new URL[0])) {
            final ClassLoader previous = Thread.currentThread().getContextClassLoader();
            EventContext ctx = null;
            try {
                // switch the TCCL to a test specific one
                Thread.currentThread().setContextClassLoader(urlc);

                ctx = (EventContext) (new InitialContext(envProps).lookup(LOOKUP_NAME));
                // add a NamingListener to exercise the Thread creation in the internals
                // of LdapCtx
                ctx.addNamingListener(LOOKUP_NAME, EventContext.OBJECT_SCOPE, new Listener());
                // verify that none of the live Thread(s) other than the current Thread
                // have their TCCL set to the one set by the test. i.e. verify that the
                // context classloader hasn't leaked into Thread(s) that may have been
                // created by the LDAP naming listener code.
                assertTCCL(urlc, Collections.singleton(Thread.currentThread()));
            } finally {
                if (ctx != null) {
                    ctx.close();
                }
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    /*
     * Verifies that none of the live threads have their context classloader set to
     * the given "notExpected" classloader.
     */
    private static void assertTCCL(final ClassLoader notExpected,
                                   final Collection<Thread> threadsToIgnore) {
        ThreadGroup topMostThreadGroup = Thread.currentThread().getThreadGroup();
        assertNotNull(topMostThreadGroup,
                "ThreadGroup for current thread " + Thread.currentThread() + " was null");
        while (topMostThreadGroup.getParent() != null) {
            topMostThreadGroup = topMostThreadGroup.getParent();
        }
        // recursively enumerate the threads in the top most thread group
        final Thread[] threads = new Thread[1024];
        final int numThreads = topMostThreadGroup.enumerate(threads);
        // verify the threads
        int numFailedThreads = 0;
        final StringBuilder diagnosticMsg = new StringBuilder();
        for (int i = 0; i < numThreads; i++) {
            final Thread t = threads[i];
            if (threadsToIgnore.contains(t)) {
                continue; // skip verification of the thread
            }
            System.err.println("verifying " + t);
            if (t.getContextClassLoader() == notExpected) {
                // Thread has an unexpected context classloader
                numFailedThreads++;
                // for debugging track the stacktrace of the thread
                // that failed the check
                diagnosticMsg.append("FAILED - ").append(t)
                        .append(" is using unexpected context classloader: ")
                        .append(notExpected)
                        .append(", its current activity is:\n");
                for (StackTraceElement ste : t.getStackTrace()) {
                    diagnosticMsg.append("\t").append(ste).append("\n");
                }
            }
        }
        if (numFailedThreads > 0) {
            // for debugging print out the stacktrace of the
            // Thread(s) that failed the check
            System.err.println(diagnosticMsg);
            fail(numFailedThreads + " Thread(s) had unexpected context classloader "
                    + notExpected);
        }
    }

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
                    // write out a search response
                    final byte[] rsp = makeSearchResponse((byte) request.getMessageID(), LOOKUP_NAME);
                    out.write(rsp);
                    out.flush();
                    System.err.println("wrote response for message: " + request.getMessageID());
                    break;
                }
                default: {
                    throw new IOException("unexpected operation type: " + request.getOperation()
                            + ", request: " + request);
                }
            }
        }

        // constructs and returns a byte[] response containing the following (in that order):
        //  - Search Result Entry
        //  - Search Result Done
        private static byte[] makeSearchResponse(final byte msgId, final String dn)
                throws IOException {
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            final byte msgIdLen = 1;

            // write the BER elements
            // each BER element is 3 parts:
            // Type, length, value

            // Search Result Entry BER element (refer to LDAPv3 wire format for details)
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            bout.write(dn.length() + 9);
            bout.write(new byte[]{BER_TYPE_INTEGER, msgIdLen, msgId});
            bout.write(BER_TYPE_LDAP_SEARCH_RESULT_ENTRY_OP);
            bout.write(dn.length() + 2);
            bout.write(BER_TYPE_OCTET_STRING);
            bout.write(dn.length());
            bout.write(dn.getBytes(US_ASCII));
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            // 0 length for the LDAP sequence, implying no attributes in this Search Result Entry
            bout.write(0);

            bout.write(makeSearchResultDone(msgId));

            return bout.toByteArray();
        }

        // Search Result Done BER element (refer to LDAPv3 wire format for details)
        private static byte[] makeSearchResultDone(final byte msgId) throws IOException {
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            final byte msgIdLen = 1;
            final String matchedDN = "";
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            bout.write(matchedDN.length() + 12);
            bout.write(new byte[]{BER_TYPE_INTEGER, msgIdLen, msgId});
            bout.write(BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP);
            bout.write(7);
            bout.write(new byte[]{BER_TYPE_ENUM, 1, LDAP_SEARCH_RESULT_DONE_SUCCESS});
            // the matched DN
            bout.write(BER_TYPE_OCTET_STRING);
            bout.write(matchedDN.length());
            bout.write(matchedDN.getBytes(US_ASCII));
            // 0 length implies no diagnostic message
            bout.write(new byte[]{BER_TYPE_OCTET_STRING, 0});
            return bout.toByteArray();
        }
    }

    private static final class Listener implements ObjectChangeListener {

        @Override
        public void namingExceptionThrown(final NamingExceptionEvent evt) {
            System.err.println("namingExceptionThrown() called for " + evt);
        }

        @Override
        public void objectChanged(final NamingEvent evt) {
            System.err.println("objectChanged() called for " + evt);
        }
    }
}
