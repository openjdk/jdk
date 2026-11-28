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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.LdapName;

import com.sun.jndi.ldap.BerEncoder;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8396120
 * @summary verify the behaviour of DirContext.getSchema() against a LDAP context
 * @modules java.naming/com.sun.jndi.ldap
 * @library /test/lib lib/
 * @build jdk.test.lib.net.URIBuilder
 *        BaseLdapServer LdapMessage
 * @run junit ${test.main.class}
 */
class LdapCtxGetSchemaTest {

    private static final String DN = "CN=NameDoesNotMatter";
    // the attribute name that's looked up by LdapCtx.getSchema()
    private static final String ATTR_NAME_SUB_SCHEMA_SUB_ENTRY = "subschemasubentry";

    // standard byte values for LDAP messages
    private static final byte SEQUENCE = 0x30;
    private static final byte SET = 0x31;
    private static final byte ENUM = 0x0a;
    private static final byte OCTET_STRING = 0x04;
    private static final byte SEARCH_RESULT_ENTRY_OP = 0x64;
    private static final byte SEARCH_RESULT_DONE_OP = 0x65;

    private static Server server;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new Server();
        server.start();
        System.err.println("Started server at " + server.getAddress());
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            System.err.println("Stopping server " + server);
            server.close();
        }
    }

    private static Hashtable<String, String> createEnvProps(final String baseDN) throws Exception {
        final Hashtable<String, String> envProps = new Hashtable<>();
        envProps.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        final String providerUrl = URIBuilder.newBuilder()
                .scheme("ldap")
                .host(server.getInetAddress().getHostAddress())
                .port(server.getPort())
                .path("/" + baseDN)
                .build().toString();
        envProps.put(Context.PROVIDER_URL, providerUrl);
        // explicitly set LDAP version to 3 to prevent LDAP BIND requests
        // during LdapCtx instantiation
        envProps.put("java.naming.ldap.version", "3");
        return envProps;
    }

    private static List<String> baseNames() {
        return List.of(
                // empty base DN
                "",
                // arbitrary non-empty base DN
                "CN=fooBarBaseDN"
        );
    }

    /*
     * Calls DirContext.getSchema(Name). The server side is implemented to return zero values
     * for the "subschemasubentry" attribute. This test verifies that in that case, a
     * OperationNotSupportedException is thrown from DirContext.getSchema(Name), as specified
     * by that method.
     */
    @ParameterizedTest
    @MethodSource("baseNames")
    void testGetSchema(final String baseDN) throws Throwable {
        final Hashtable<String, String> envProps = createEnvProps(baseDN);
        final DirContext dirContext = new InitialDirContext(envProps);
        Throwable testFailure = null;
        try {
            assertThrows(OperationNotSupportedException.class,
                    () -> dirContext.getSchema(new LdapName(DN)));
        } catch (Throwable t) {
            testFailure = t;
        } finally {
            closeDirContext(dirContext, testFailure);
        }
    }

    /*
     * Calls DirContext.getSchema(String). The server side is implemented to return zero values
     * for the "subschemasubentry" attribute. This test verifies that in that case, a
     * OperationNotSupportedException is thrown from DirContext.getSchema(String), as specified
     * by that method.
     */
    @ParameterizedTest
    @MethodSource("baseNames")
    void testGetSchemaString(final String baseDN) throws Throwable {
        final Hashtable<String, String> envProps = createEnvProps(baseDN);
        final DirContext dirContext = new InitialDirContext(envProps);
        Throwable testFailure = null;
        try {
            assertThrows(OperationNotSupportedException.class,
                    () -> dirContext.getSchema(DN));
        } catch (Throwable t) {
            testFailure = t;
        } finally {
            closeDirContext(dirContext, testFailure);
        }
    }

    // construct the SearchResultEntry for the given message id
    private static void makeSearchResultEntry(final BerEncoder ber,
                                              final byte msgId) throws IOException {

        //  SearchResultEntry ::= [APPLICATION 4] SEQUENCE {
        //      objectName      LDAPDN,
        //      attributes      PartialAttributeList
        //  }
        ber.beginSeq(SEQUENCE);
        ber.encodeInt(msgId);
        {
            ber.beginSeq(SEARCH_RESULT_ENTRY_OP);
            {
                ber.encodeOctetString(DN.getBytes(US_ASCII), OCTET_STRING);
                // write out the attribute(s)
                //  PartialAttributeList ::= SEQUENCE OF
                //      partialAttribute PartialAttribute
                ber.beginSeq(SEQUENCE);
                makeAttributeWithNoValue(ber, ATTR_NAME_SUB_SCHEMA_SUB_ENTRY);
                ber.endSeq();
            }
            ber.endSeq();
        }
        ber.endSeq();
    }

    // construct an attribute with zero values for the given attribute name
    private static void makeAttributeWithNoValue(final BerEncoder ber,
                                                 final String attrName) throws IOException {

        //  PartialAttribute ::= SEQUENCE {
        //      type       AttributeDescription,
        //      vals       SET OF value AttributeValue
        //  }
        ber.beginSeq(SEQUENCE);
        {
            ber.encodeOctetString(attrName.getBytes(US_ASCII), OCTET_STRING);
            // no attribute values
            ber.beginSeq(SET);
            ber.endSeq();
        }
        ber.endSeq();
    }

    // construct a SearchResultDone LDAP message for the given message id
    private static void makeSearchResultDone(final BerEncoder ber,
                                             final byte msgId) throws IOException {
        // SearchResultDone ::= [APPLICATION 5] LDAPResult
        //
        // LDAPResult ::= SEQUENCE {
        //      resultCode         ENUMERATED {
        //           success                      (0),
        //           ...
        //      },
        //      matchedDN          LDAPDN,
        //      diagnosticMessage  LDAPString,
        //      referral           [3] Referral OPTIONAL
        // }
        ber.beginSeq(SEQUENCE);
        ber.encodeInt(msgId);
        {
            ber.beginSeq(SEARCH_RESULT_DONE_OP);
            ber.encodeInt(0, ENUM); // resultCode = 0 == success
            ber.encodeString("", false); // matchedDN == empty string
            ber.encodeString("", false); // diagnosticMessage == empty string
            ber.endSeq();
        }
        ber.endSeq();
    }

    private static String toHexString(final byte[] bytes) {
        final HexFormat formatter = HexFormat.of();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i != 0) {
                sb.append(" ");
            }
            sb.append("0x").append(formatter.formatHex(bytes, i, i + 1));
        }
        return sb.toString();
    }

    /**
     * Closes the {@code ctx}. The method returns normally if {@code testFailure} is null and
     * there was no exception when closing the {@code ctx}.
     * <p>
     * If the {@code testFailure} is not null, then this method rethrows it.
     * If there was an exception when closing the {@code ctx}, then that exception is added
     * as a suppressed exception to the {@code testFailure}.
     * <p>
     * If the {@code testFailure} is null and if there was an exception when closing the {@code ctx}
     * then that exception is rethrown.
     */
    private static void closeDirContext(final DirContext ctx, final Throwable testFailure)
            throws Throwable {
        Throwable toThrow = testFailure;
        try {
            System.err.println("closing " + ctx);
            ctx.close();
        } catch (NamingException ne) {
            if (toThrow == null) {
                toThrow = ne;
            } else if (toThrow != ne) {
                // add the failure to close the DirContext
                // as a suppressed exception to the original test failure
                toThrow.addSuppressed(ne);
            }
        }
        if (toThrow != null) {
            throw toThrow;
        }
    }

    // the LDAP server used in the test
    private static final class Server extends BaseLdapServer implements AutoCloseable {

        private Server() throws IOException {
            super(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()));
        }

        private InetSocketAddress getAddress() {
            return new InetSocketAddress(this.getInetAddress(), this.getPort());
        }

        // handles and responds to the incoming LDAP request
        @Override
        protected void handleRequestEx(final Socket socket,
                                       final LdapMessage request,
                                       final OutputStream out,
                                       final BaseLdapServer.ConnWrapper connWrapper)
                throws IOException {

            final byte msgId = (byte) request.getMessageID();
            if (request.getOperation() == LdapMessage.Operation.SEARCH_REQUEST) {
                System.err.println("handling SEARCH_REQUEST with id: " + msgId + " from " + socket);
                // create the response
                final BerEncoder ber = new BerEncoder();
                makeSearchResultEntry(ber, msgId);
                makeSearchResultDone(ber, msgId);
                final byte[] resp = ber.getTrimmedBuf();
                System.err.println("response for message id " + msgId + ": " + toHexString(resp));
                // write out a search response
                out.write(resp);
                out.flush();
                return;
            }
            // other request types aren't of interest to this test, let the common implementation
            // in the BaseLdapServer handle it
            System.err.println("ignoring request " + request + " with msg id " + msgId
                    + " on socket " + socket);
            super.handleRequestEx(socket, request, out, connWrapper);
        }
    }
}
