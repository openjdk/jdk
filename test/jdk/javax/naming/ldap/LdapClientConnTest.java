/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jndi.ldap.LdapCtx;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8357708
 * @summary verify that com.sun.jndi.ldap.Connection does not ignore the LDAP replies
 *          that were received before the Connection was closed.
 * @modules java.naming/com.sun.jndi.ldap
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run junit LdapClientConnTest
 */
public class LdapClientConnTest {

    private static final byte BER_TYPE_LDAP_SEQUENCE = 0x30;
    private static final byte BER_TYPE_INTEGER = 0x02;
    private static final byte BER_TYPE_OCTET_STRING = 0x04;
    private static final byte BER_TYPE_ENUM = 0x0a;
    private static final byte BER_TYPE_LDAP_SEARCH_REQUEST_OP = 0x63;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_ENTRY_OP = 0x64;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP = 0x65;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_REFERENCE_OP = 0x73;
    private static final byte LDAP_SEARCH_RESULT_DONE_SUCCESS = 0x00;

    private static final String SEARCH_REQ_DN_PREFIX = "CN=foo-";
    private static final String SEARCH_REQ_DN_SUFFIX = "-bar";

    private static Server server;
    private static final List<Throwable> serverSideFailures =
            Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void beforeAll() throws Exception {
        server = startServer();
        System.out.println("server started " + server.getAddress());
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            System.out.println("stopping server " + server.getAddress());
            server.close();
        }
    }

    /*
     * Launches several concurrent tasks, all of which use a LdapClient of their own to trigger
     * a LDAP SEARCH request. The server side handles the LDAP SEARCH request and writes out the
     * response over the Socket and then after the response is written out, closes the
     * OutputStream of the Socket. The test then verifies that each of these tasks complete
     * normally without any exception being raised.
     */
    @Test
    public void testLdapRepliesNotIgnored() throws Throwable {
        final Map<String, Future<Void>> results = new HashMap<>();
        final int numTasks = 10;
        try (final ExecutorService executor = Executors.newCachedThreadPool()) {
            for (int i = 1; i <= numTasks; i++) {
                final String taskName = "task-" + i;
                results.put(taskName, executor.submit(new LdapRequestsTask(taskName)));
            }
            System.out.println("waiting for " + numTasks + " to complete");
            for (final Map.Entry<String, Future<Void>> entry : results.entrySet()) {
                try {
                    entry.getValue().get();
                } catch (ExecutionException ee) {
                    final Throwable cause = ee.getCause();
                    System.out.println("failed for " + entry.getKey() + ", exception: " + cause);
                    throw cause;
                }
            }
        }
        // verify there weren't any server side failures
        if (!serverSideFailures.isEmpty()) {
            System.err.println("server side failure(s) follow:");
            for (final Throwable t : serverSideFailures) {
                t.printStackTrace();
            }
            fail("unexpected server side failures");
        }
    }

    private static Server startServer() throws IOException {
        final ServerSocket serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        final Server s = new Server(serverSocket);
        s.start();
        return s;
    }

    // accepts connections on a ServerSocket and hands off the request processing
    // to the RequestHandler
    private static final class Server implements Runnable, AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicInteger reqHandlerTid = new AtomicInteger();
        private volatile boolean stop;

        private Server(final ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            System.err.println("Server accepting connections at "
                    + serverSocket.getLocalSocketAddress());
            while (!stop) {
                try {
                    final Socket accepted = serverSocket.accept();
                    System.err.println("Accepted connection from " + accepted);
                    dispatchRequest(accepted);
                } catch (Throwable t) {
                    if (!stop) {
                        System.err.println("Server thread ran into unexpected exception: " + t);
                        t.printStackTrace();
                        // keep track of this failure to eventually fail the test
                        serverSideFailures.add(t);
                        return;
                    }
                }
            }
        }

        private void start() {
            final Thread serverThread = new Thread(this);
            serverThread.setName("server");
            serverThread.setDaemon(true);
            serverThread.start();
        }

        private InetSocketAddress getAddress() {
            return (InetSocketAddress) this.serverSocket.getLocalSocketAddress();
        }

        private void dispatchRequest(final Socket incomingConnection) {
            final RequestHandler handler = new RequestHandler(incomingConnection);
            // handle the request in a separate thread
            final Thread reqHandlerThread = new Thread(handler);
            reqHandlerThread.setName("request-handler-" + reqHandlerTid.incrementAndGet());
            reqHandlerThread.setDaemon(true);
            reqHandlerThread.start();
        }

        @Override
        public void close() {
            this.stop = true;
            try {
                System.err.println("closing server socket " + this.serverSocket);
                this.serverSocket.close();
            } catch (IOException _) {
                // ignore
            }
        }
    }

    // Handles a single request over the Socket and responds back on the same Socket
    private static final class RequestHandler implements Runnable {

        private record SearchRequest(byte msgId, String dn) {
        }

        private final Socket clientSocket;

        private RequestHandler(final Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            final String threadName = Thread.currentThread().getName();
            System.err.println(threadName + " - handling request on socket: " + clientSocket);
            try (InputStream is = clientSocket.getInputStream();
                 OutputStream os = clientSocket.getOutputStream()) {
                final SearchRequest searchRequest = parseLDAPSearchRequest(is);
                // generate a LDAP response
                final byte[] responseBytes = makeResponse(searchRequest.msgId,
                        searchRequest.dn, server.getAddress());
                System.err.println(threadName + " - responding to: " + searchRequest);
                os.write(responseBytes);
            } catch (Throwable t) {
                System.err.println(threadName + " - exception in request handler: " + t);
                t.printStackTrace();
                // keep track of this failure to eventually fail the test
                serverSideFailures.add(t);
            } finally {
                System.err.println(threadName + " - request handler done");
            }
        }

        private static SearchRequest parseLDAPSearchRequest(final InputStream is)
                throws IOException {
            final String threadName = Thread.currentThread().getName();
            final HexFormat hf = HexFormat.of();
            // read the BER elements
            // each BER element is 3 parts:
            // Type, length, value
            final int berType = is.read();
            if (berType != BER_TYPE_LDAP_SEQUENCE) {
                // unexpected content
                throw new IOException(threadName + " - unexpected request, not a LDAP_SEQUENCE: "
                        + hf.formatHex(new byte[]{(byte) berType}));
            }
            // BER element length
            int seqLen = is.read();
            // 0x81, 0x82, 0x84 (and a few others) represent length that is represented
            // in multiple bytes. for this test we only consider length represented in
            // single byte or multiple bytes through 0x81 and 0x82
            if (seqLen == 0x81) {
                seqLen = is.read() & 0xff;
            } else if (seqLen == 0x82) {
                seqLen = (is.read() & 0xff) << 8 + (is.read() & 0xff);
            }
            if (seqLen < 0) {
                // unexpected BER element length
                throw new IOException(threadName + " - unexpected BER element length: " + seqLen);
            }
            // read the BER element value
            final byte[] ldapSeq = new byte[seqLen];
            System.err.println(threadName + " - reading " + seqLen + " bytes from request");
            is.readNBytes(ldapSeq, 0, seqLen);

            final String ldapSeqHex = HexFormat.of().formatHex(ldapSeq); // just for debug logging
            System.err.println(threadName + " - request LDAP sequence: 0x" + ldapSeqHex);

            // read the message id BER element from the LDAP sequence
            final byte msgIdType = ldapSeq[0];
            if (msgIdType != BER_TYPE_INTEGER) {
                // unexpected content
                throw new IOException(threadName + " - unexpected BER type for message id element: "
                        + hf.formatHex(new byte[]{msgIdType}));
            }
            final byte msgIdLen = ldapSeq[1];
            final byte msgId = ldapSeq[2];
            // read LDAP operation type
            final byte ldapOpType = ldapSeq[3];
            if (ldapOpType != BER_TYPE_LDAP_SEARCH_REQUEST_OP) {
                // we only support LDAP search requests in this handler
                throw new IOException(threadName + " - unexpected BER type for LDAP operation: "
                        + hf.formatHex(new byte[]{ldapOpType}));
            }
            final byte searchReqSeqLen = ldapSeq[4];
            if (searchReqSeqLen < 0) {
                // implies the length is represented in multiple bytes. we don't
                // expect that big a search request payload in this test, so fail.
                throw new IOException(threadName + " - unexpected length for SEARCH request: "
                        + hf.formatHex(new byte[]{searchReqSeqLen}));
            }
            // not all characters will be ASCII, but that's OK, this is here merely as a check
            // for unexpected requests
            final String remainingPayload = new String(ldapSeq, 5, (ldapSeq.length - 5));
            final int dnPrefixIndex = remainingPayload.indexOf(SEARCH_REQ_DN_PREFIX);
            final int dnSuffixIndex = remainingPayload.indexOf(SEARCH_REQ_DN_SUFFIX);
            if (dnPrefixIndex < 0 || dnSuffixIndex < 0) {
                throw new IOException(threadName + " - missing expected DN in SEARCH request: "
                        + remainingPayload);
            }
            final String dn = remainingPayload.substring(dnPrefixIndex,
                    dnSuffixIndex + SEARCH_REQ_DN_SUFFIX.length());
            return new SearchRequest(msgId, dn);
        }

        // constructs and returns a byte[] response containing the following (in that order):
        //  - Search Result Reference
        //  - Search Result Entry
        //  - Search Result Done
        private static byte[] makeResponse(final byte msgId, final String origDN,
                                           final InetSocketAddress targetServer)
                throws IOException {
            // construct a URI with a different DN for using as referral URI
            final String newPrefix = SEARCH_REQ_DN_PREFIX + "dummy-referral-";
            final String newDN = origDN.replace(SEARCH_REQ_DN_PREFIX, newPrefix);
            final String referralURI = URIBuilder.newBuilder()
                    .scheme("ldap")
                    .host(targetServer.getAddress().getHostAddress())
                    .port(targetServer.getPort())
                    .path("/" + newDN)
                    .buildUnchecked()
                    .toString();
            final byte msgIdLen = 1;
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            // write the BER elements
            // each BER element is 3 parts:
            // Type, length, value

            // Search Result Reference BER element (refer to LDAPv3 wire format for details)
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            bout.write(referralURI.length() + 7);
            bout.write(new byte[]{BER_TYPE_INTEGER, msgIdLen, msgId});
            bout.write(BER_TYPE_LDAP_SEARCH_RESULT_REFERENCE_OP);
            bout.write(referralURI.length() + 2);
            bout.write(BER_TYPE_OCTET_STRING);
            bout.write(referralURI.length());
            bout.write(referralURI.getBytes(US_ASCII));

            // Search Result Entry BER element (refer to LDAPv3 wire format for details)
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            bout.write(origDN.length() + 9);
            bout.write(new byte[]{BER_TYPE_INTEGER, msgIdLen, msgId});
            bout.write(BER_TYPE_LDAP_SEARCH_RESULT_ENTRY_OP);
            bout.write(origDN.length() + 2);
            bout.write(BER_TYPE_OCTET_STRING);
            bout.write(origDN.length());
            bout.write(origDN.getBytes(US_ASCII));
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            // 0 length for the LDAP sequence, implying no attributes in this Search Result Entry
            bout.write(0);

            // Search Result Done BER element (refer to LDAPv3 wire format for details)
            bout.write(BER_TYPE_LDAP_SEQUENCE);
            bout.write(origDN.length() + 12);
            bout.write(new byte[]{BER_TYPE_INTEGER, msgIdLen, msgId});
            bout.write(BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP);
            bout.write(7);
            bout.write(new byte[]{BER_TYPE_ENUM, 1, LDAP_SEARCH_RESULT_DONE_SUCCESS});
            // the matched DN
            bout.write(BER_TYPE_OCTET_STRING);
            bout.write(origDN.length());
            bout.write(origDN.getBytes(US_ASCII));
            // 0 length implies no diagnostic message
            bout.write(new byte[]{BER_TYPE_OCTET_STRING, 0});
            return bout.toByteArray();
        }
    }

    // a task that triggers LDAP SEARCH request
    private static final class LdapRequestsTask implements Callable<Void> {
        private final String taskName;

        private LdapRequestsTask(final String taskName) {
            this.taskName = taskName;
        }

        @Override
        public Void call() throws Exception {
            LdapCtx ldapCtx = null;
            try {
                final InetSocketAddress serverAddr = server.getAddress();
                final Hashtable<String, String> envProps = new Hashtable<>();
                // explicitly set LDAP version to 3 to prevent LDAP BIND requests
                // during LdapCtx instantiation
                envProps.put("java.naming.ldap.version", "3");
                ldapCtx = new LdapCtx("",
                        serverAddr.getAddress().getHostAddress(),
                        serverAddr.getPort(),
                        envProps, false);
                final String name = SEARCH_REQ_DN_PREFIX + taskName + SEARCH_REQ_DN_SUFFIX;
                // trigger the LDAP SEARCH requests through the lookup call. we are not
                // interested in the returned value and are merely interested in a normal
                // completion of the call.
                final var _ = ldapCtx.lookup(name);
                return null;
            } finally {
                if (ldapCtx != null) {
                    ldapCtx.close();
                }
            }
        }
    }
}
