/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7094377 8000487 6176036 7056489
 * @summary Timeout tests for ldap
 */

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
import java.util.concurrent.TimeUnit;

public class LdapTimeoutTest {
    private static final ScheduledExecutorService pool =
        Executors.newScheduledThreadPool(1);
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

        env.put("com.sun.jndi.ldap.connect.timeout", "10");
        env.put("com.sun.jndi.ldap.read.timeout", "3000");

        InitialContext ctx = null;
        try {
            new LdapTimeoutTest().ldapReadTimeoutTest(env, false);
            new LdapTimeoutTest().ldapReadTimeoutTest(env, true);
            new LdapTimeoutTest().simpleAuthConnectTest(env);
        } finally {
            s.interrupt();
            LdapTimeoutTest.pool.shutdown();
        }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");
    }

    void ldapReadTimeoutTest(Hashtable env, boolean ssl) {
        InitialContext ctx = null;
        if (ssl) env.put(Context.SECURITY_PROTOCOL, "ssl");
        ScheduledFuture killer = killSwitch();
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
            if (!shutItDown(killer, ctx)) fail();
        }
    }

    void simpleAuthConnectTest(Hashtable env) {
        InitialContext ctx = null;
        ScheduledFuture killer = killSwitch();
        long start = System.nanoTime();
        try {
            ctx = new InitialDirContext(env);
            // shouldn't reach here
            System.err.println("Fail: InitialDirContext succeeded");
            fail();
        } catch (NamingException e) {
            long end = System.nanoTime();
            if (e.getCause() instanceof SocketTimeoutException) {
                if (TimeUnit.NANOSECONDS.toMillis(end - start) < 2900) {
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
            if (!shutItDown(killer, ctx)) fail();
        }
    }

    boolean shutItDown(ScheduledFuture killer, InitialContext ctx) {
        killer.cancel(true);
        try {
            if (ctx != null) ctx.close();
            return true;
        } catch (NamingException ex) {
            return false;
        }
    }

    ScheduledFuture killSwitch() {
        final Thread current = Thread.currentThread();
        return LdapTimeoutTest.pool.schedule(new Callable<Void>() {
            public Void call() throws Exception {
                System.err.println("Fail: killSwitch()");
                current.interrupt();
                return null;
            }
        }, 5000, TimeUnit.MILLISECONDS);
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

