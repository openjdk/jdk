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

/**
 * @test
 * @bug 8288895
 * @summary LdapContext doesn't honor set referrals limit
 * @library lib /test/lib
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.Context;
import javax.naming.LimitExceededException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchResult;
import jdk.test.lib.net.URIBuilder;

/**
 * <p>Little test for referral limit. The ldap server is configured to
 * always return a referral so LimitExceededException is expected when
 * <em>java.naming.ldap.referral.limit</em> is reached.</p>
 *
 * @author rmartinc
 */
public class ReferralLimitSearchTest {

    // number of referral hops to test
    private static final int MAX_REFERRAL_LIMIT = 4;

    // position of the message-id inside the responses
    private static final int MESSAGE_ID_IDX = 4;

    // success bind response
    private static final byte[] BIND_RESPONSE = {
            0x30, 0x0C, 0x02, 0x01, 0x00, 0x61, 0x07, 0x0A,
            0x01, 0x00, 0x04, 0x00, 0x04, 0x00
    };

    // search res done
    private static final byte[] SEARCH_RESPONSE = {
            0x30, 0x0C, 0x02, 0x01, 0x00, 0x65, 0x07, 0x0A,
            0x01, 0x00, 0x04, 0x00, 0x04, 0x00
    };

    public static void main(String[] args) throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        // Start the LDAP server
        BaseLdapServer ldapServer = new BaseLdapServer() {
            AtomicInteger hops = new AtomicInteger(0);

            @Override
            protected void handleRequest(Socket socket, LdapMessage request,
                    OutputStream out) throws IOException {
                switch (request.getOperation()) {
                    case BIND_REQUEST:
                        byte[] bindResponse = BIND_RESPONSE.clone();
                        bindResponse[MESSAGE_ID_IDX] = (byte) request.getMessageID();
                        out.write(bindResponse);
                        break;
                    case SEARCH_REQUEST:
                        if (hops.incrementAndGet() > MAX_REFERRAL_LIMIT + 1) {
                            throw new IOException("Referral limit not enforced. Number of hops=" + hops);
                        }
                        byte[] referral = new StringBuilder("ldap://")
                                .append(InetAddress.getLoopbackAddress().getHostAddress())
                                .append(":")
                                .append(getPort())
                                .append("/ou=People??sub")
                                .toString()
                                .getBytes(StandardCharsets.UTF_8);
                        out.write(0x30);
                        out.write(referral.length + 7);
                        out.write(new byte[] {0x02, 0x01});
                        out.write(request.getMessageID());
                        out.write(0x73);
                        out.write(referral.length + 2);
                        out.write(0x04);
                        out.write(referral.length);
                        out.write(referral);

                        byte[] searchResponse = SEARCH_RESPONSE.clone();
                        searchResponse[MESSAGE_ID_IDX] = (byte) request.getMessageID();
                        out.write(searchResponse);
                        break;
                    default:
                        break;
                }
            }

            protected void beforeAcceptingConnections() {
                latch.countDown();
            }
        }.start();

        try (ldapServer) {

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("LdapServer not started in time");
            }

            // Setup JNDI parameters
            Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.REFERRAL, "follow");
            env.put("java.naming.ldap.referral.limit", Integer.toString(MAX_REFERRAL_LIMIT));
            env.put(Context.PROVIDER_URL, URIBuilder.newBuilder()
                    .scheme("ldap")
                    .loopback()
                    .port(ldapServer.getPort())
                    .build().toString());

            System.out.println("Client: connecting...");
            DirContext ctx = new InitialDirContext(env);
            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(new String[]{"uid"});
            System.out.println("Client: performing search...");
            NamingEnumeration<SearchResult> ne = ctx.search("ou=People", "(uid=*)", sc);
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                System.out.println("Client: Search result " + sr);
            }
            System.out.println("Client: search done...");
            ctx.close();

            // LimitExceededException expected throw error if this point is reached
            throw new RuntimeException("LimitExceededException expected");

        } catch (LimitExceededException e) {
            System.out.println("Passed: caught the expected Exception - " + e);
        } catch (Exception e) {
            System.err.println("Failed: caught an unexpected Exception - " + e);
            throw e;
        }
    }
}
