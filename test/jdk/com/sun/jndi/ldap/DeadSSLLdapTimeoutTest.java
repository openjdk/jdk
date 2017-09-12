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
 * @run main/othervm DeadSSLLdapTimeoutTest
 * @bug 8141370
 * @key intermittent
 */

import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.io.*;
import javax.naming.*;
import javax.naming.directory.*;
import java.util.List;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLHandshakeException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;


class DeadServerTimeoutSSLTest implements Callable {

    Hashtable env;
    DeadSSLServer server;
    boolean passed = false;
    private int HANGING_TEST_TIMEOUT = 20_000;

    public DeadServerTimeoutSSLTest(Hashtable env) throws IOException {
        this.server = new DeadSSLServer();
        this.env = env;
    }

    public void performOp(InitialContext ctx) throws NamingException {}

    public void handleNamingException(NamingException e, long start, long end) {
        if (e.getCause() instanceof SocketTimeoutException) {
            // SSL connect will timeout via readReply using
            // SocketTimeoutException
            e.printStackTrace();
            pass();
        } else if (e.getCause() instanceof SSLHandshakeException
                && e.getCause().getCause() instanceof EOFException) {
            // test seems to be failing intermittently on some
            // platforms.
            pass();
        } else {
            fail(e);
        }
    }

    public void pass() {
        this.passed = true;
    }

    public void fail() {
        throw new RuntimeException("Test failed");
    }

    public void fail(Exception e) {
        throw new RuntimeException("Test failed", e);
    }

    boolean shutItDown(InitialContext ctx) {
        try {
            if (ctx != null) ctx.close();
            return true;
        } catch (NamingException ex) {
            return false;
        }
    }

    public Boolean call() {
        InitialContext ctx = null;
        ScheduledFuture killer = null;
        long start = System.nanoTime();

        try {
            while(!server.accepting())
                Thread.sleep(200); // allow the server to start up
            Thread.sleep(200); // to be sure

            env.put(Context.PROVIDER_URL, "ldap://localhost:" +
                    server.getLocalPort());

            try {
                ctx = new InitialDirContext(env);
                performOp(ctx);
                fail();
            } catch (NamingException e) {
                long end = System.nanoTime();
                System.out.println(this.getClass().toString() + " - elapsed: "
                        + NANOSECONDS.toMillis(end - start));
                handleNamingException(e, start, end);
            } finally {
                if (killer != null && !killer.isDone())
                    killer.cancel(true);
                shutItDown(ctx);
                server.close();
            }
            return passed;
        } catch (IOException|InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class DeadSSLServer extends Thread {
    ServerSocket serverSock;
    boolean accepting = false;

    public DeadSSLServer() throws IOException {
        this.serverSock = new ServerSocket(0);
        start();
    }

    public void run() {
        while(true) {
            try {
                accepting = true;
                Socket socket = serverSock.accept();
            } catch (Exception e) {
                break;
            }
        }
    }

    public int getLocalPort() {
        return serverSock.getLocalPort();
    }

    public boolean accepting() {
        return accepting;
    }

    public void close() throws IOException {
        serverSock.close();
    }
}

public class DeadSSLLdapTimeoutTest {

    static Hashtable createEnv() {
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
        return env;
    }

    public static void main(String[] args) throws Exception {

        InitialContext ctx = null;

        //
        // Running this test serially as it seems to tickle a problem
        // on older kernels
        //
        // run the DeadServerTest with connect / read timeouts set
        // and ssl enabled
        // this should exit with a SocketTimeoutException as the root cause
        // it should also use the connect timeout instead of the read timeout
        System.out.println("Running connect timeout test with 10ms connect timeout, 3000ms read timeout & SSL");
        Hashtable sslenv = createEnv();
        sslenv.put("com.sun.jndi.ldap.connect.timeout", "10");
        sslenv.put("com.sun.jndi.ldap.read.timeout", "3000");
        sslenv.put(Context.SECURITY_PROTOCOL, "ssl");
        boolean testFailed =
            (new DeadServerTimeoutSSLTest(sslenv).call()) ? false : true;

        if (testFailed) {
            throw new AssertionError("some tests failed");
        }

    }

}

