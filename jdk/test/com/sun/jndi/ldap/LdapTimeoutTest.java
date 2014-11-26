/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm LdapTimeoutTest
 * @bug 7094377 8000487 6176036 7056489
 * @summary Timeout tests for ldap
 */

import com.sun.jndi.ldap.Connection;

import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.io.*;
import javax.naming.*;
import javax.naming.directory.*;
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class LdapTimeoutTest {

    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}

    public static void main(String[] args) throws Exception {
        ServerSocket serverSock = new ServerSocket(0);
        Server s = new Server(serverSock);
        s.start();
        Thread.sleep(200);

        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://localhost:" +
            serverSock.getLocalPort());

        env.put(Context.SECURITY_AUTHENTICATION,"simple");

        env.put(Context.SECURITY_PRINCIPAL, "user");
        env.put(Context.SECURITY_CREDENTIALS, "password");

        InitialContext ctx = null;
        try {
            new LdapTimeoutTest().deadServerNoTimeout(env);

            env.put("com.sun.jndi.ldap.connect.timeout", "10");
            env.put("com.sun.jndi.ldap.read.timeout", "3000");
            new LdapTimeoutTest().ldapReadTimeoutTest(env, false);
            new LdapTimeoutTest().ldapReadTimeoutTest(env, true);
            new LdapTimeoutTest().simpleAuthConnectTest(env);
        } finally {
            s.interrupt();
        }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");
    }

    void ldapReadTimeoutTest(Hashtable env, boolean ssl) {
        InitialContext ctx = null;
        if (ssl) env.put(Context.SECURITY_PROTOCOL, "ssl");
        long start = System.nanoTime();
        try {
            ctx = new InitialDirContext(env);
            SearchControls scl = new SearchControls();
            scl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> answer = ((InitialDirContext)ctx)
                .search("ou=People,o=JNDITutorial", "(objectClass=*)", scl);
            // shouldn't reach here
            fail();
        } catch (NamingException e) {
            if (ssl) {
                if (e.getCause() instanceof SocketTimeoutException) {
                    pass();
                } else if (e.getCause() instanceof InterruptedIOException) {
                    Thread.interrupted();
                    fail();
                }
            } else {
                pass();
            }
        } finally {
            if (!shutItDown(ctx)) fail();
        }
    }

    void simpleAuthConnectTest(Hashtable env) {
        InitialContext ctx = null;
        long start = System.nanoTime();
        try {
            ctx = new InitialDirContext(env);
            // shouldn't reach here
            System.err.println("Fail: InitialDirContext succeeded");
            fail();
        } catch (NamingException e) {
            long end = System.nanoTime();
            if (e.getCause() instanceof SocketTimeoutException) {
                if (NANOSECONDS.toMillis(end - start) < 2_900) {
                    pass();
                } else {
                    System.err.println("Fail: Waited too long");
                    fail();
                }
            } else if (e.getCause() instanceof InterruptedIOException) {
                Thread.interrupted();
                fail();
            } else {
                fail();
            }
        } finally {
            if (!shutItDown(ctx)) fail();
        }
    }

    void deadServerNoTimeout(Hashtable env) {
        InitialContext ctx = null;
        long start = System.currentTimeMillis();
        try {
            ctx = new InitialDirContext(env);
            SearchControls scl = new SearchControls();
            scl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            NamingEnumeration<SearchResult> answer = ((InitialDirContext)ctx)
                .search("ou=People,o=JNDITutorial", "(objectClass=*)", scl);
            // shouldn't reach here
            fail();
        } catch (NamingException e) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < Connection.DEFAULT_READ_TIMEOUT_MILLIS) {
                System.err.printf("fail: timeout should be at least %s ms, " +
                                "actual time is %s ms%n",
                        Connection.DEFAULT_READ_TIMEOUT_MILLIS, elapsed);
                e.printStackTrace();
                fail();
            } else {
                pass();
            }
        } finally {
            if (!shutItDown(ctx)) fail();
        }
    }

    boolean shutItDown(InitialContext ctx) {
        try {
            if (ctx != null) ctx.close();
            return true;
        } catch (NamingException ex) {
            return false;
        }
    }

    static class Server extends Thread {
        final ServerSocket serverSock;

        Server(ServerSocket serverSock) {
            this.serverSock = serverSock;
        }

        public void run() {
            try {
                Socket socket = serverSock.accept();
            } catch (IOException e) {}
        }
    }
}

