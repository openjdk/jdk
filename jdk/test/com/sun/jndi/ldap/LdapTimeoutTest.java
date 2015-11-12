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


abstract class LdapTest implements Callable {

    Hashtable env;
    TestServer server;
    ScheduledExecutorService killSwitchPool;
    boolean passed = false;
    private int HANGING_TEST_TIMEOUT = 20_000;

    public LdapTest (TestServer server, Hashtable env) {
        this.server = server;
        this.env = env;
    }

    public LdapTest(TestServer server, Hashtable env,
            ScheduledExecutorService killSwitchPool)
    {
        this(server, env);
        this.killSwitchPool = killSwitchPool;
    }

    public abstract void performOp(InitialContext ctx) throws NamingException;
    public abstract void handleNamingException(
        NamingException e, long start, long end);

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

            // if this is a hanging test, scheduled a thread to
            // interrupt after a certain time
            if (killSwitchPool != null) {
                final Thread current = Thread.currentThread();
                killer = killSwitchPool.schedule(
                    new Callable<Void>() {
                        public Void call() throws Exception {
                            current.interrupt();
                            return null;
                        }
                    }, HANGING_TEST_TIMEOUT, MILLISECONDS);
            }

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

abstract class ReadServerTest extends LdapTest {

    public ReadServerTest(Hashtable env) throws IOException {
        super(new BindableServer(), env);
    }

    public ReadServerTest(Hashtable env,
                          ScheduledExecutorService killSwitchPool)
            throws IOException
    {
        super(new BindableServer(), env, killSwitchPool);
    }

    public void performOp(InitialContext ctx) throws NamingException {
        SearchControls scl = new SearchControls();
        scl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> answer = ((InitialDirContext)ctx)
            .search("ou=People,o=JNDITutorial", "(objectClass=*)", scl);
    }
}

abstract class DeadServerTest extends LdapTest {

    public DeadServerTest(Hashtable env) throws IOException {
        super(new DeadServer(), env);
    }

    public DeadServerTest(Hashtable env,
                          ScheduledExecutorService killSwitchPool)
            throws IOException
    {
        super(new DeadServer(), env, killSwitchPool);
    }

    public void performOp(InitialContext ctx) throws NamingException {}
}

class DeadServerNoTimeoutTest extends DeadServerTest {

    public DeadServerNoTimeoutTest(Hashtable env,
                                   ScheduledExecutorService killSwitchPool)
            throws IOException
    {
        super(env, killSwitchPool);
    }

    public void handleNamingException(NamingException e, long start, long end) {
        if (e instanceof InterruptedNamingException) Thread.interrupted();

        if (NANOSECONDS.toMillis(end - start) < LdapTimeoutTest.MIN_TIMEOUT) {
            System.err.printf("DeadServerNoTimeoutTest fail: timeout should be " +
                              "at least %s ms, actual time is %s ms%n",
                              LdapTimeoutTest.MIN_TIMEOUT,
                              NANOSECONDS.toMillis(end - start));
            fail();
        } else {
            pass();
        }
    }
}

class DeadServerTimeoutTest extends DeadServerTest {

    public DeadServerTimeoutTest(Hashtable env) throws IOException {
        super(env);
    }

    public void handleNamingException(NamingException e, long start, long end)
    {
        // non SSL connect will timeout via readReply using connectTimeout
        if (NANOSECONDS.toMillis(end - start) < 2_900) {
            pass();
        } else {
            System.err.println("Fail: Waited too long");
            fail();
        }
    }
}

class DeadServerTimeoutSSLTest extends DeadServerTest {

    public DeadServerTimeoutSSLTest(Hashtable env) throws IOException {
        super(env);
    }

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
}


class ReadServerNoTimeoutTest extends ReadServerTest {

    public ReadServerNoTimeoutTest(Hashtable env,
                                   ScheduledExecutorService killSwitchPool)
            throws IOException
    {
        super(env, killSwitchPool);
    }

    public void handleNamingException(NamingException e, long start, long end) {
        if (e instanceof InterruptedNamingException) Thread.interrupted();

        if (NANOSECONDS.toMillis(end - start) < LdapTimeoutTest.MIN_TIMEOUT) {
            System.err.printf("ReadServerNoTimeoutTest fail: timeout should be " +
                              "at least %s ms, actual time is %s ms%n",
                              LdapTimeoutTest.MIN_TIMEOUT,
                              NANOSECONDS.toMillis(end - start));
            fail();
        } else {
            pass();
        }
    }
}

class ReadServerTimeoutTest extends ReadServerTest {

    public ReadServerTimeoutTest(Hashtable env) throws IOException {
        super(env);
    }

    public void handleNamingException(NamingException e, long start, long end) {
        System.out.println("ReadServerTimeoutTest: end-start=" + NANOSECONDS.toMillis(end - start));
        if (NANOSECONDS.toMillis(end - start) < 2_500) {
            fail();
        } else {
            pass();
        }
    }
}

class TestServer extends Thread {
    ServerSocket serverSock;
    boolean accepting = false;

    public TestServer() throws IOException {
        this.serverSock = new ServerSocket(0);
        start();
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

class BindableServer extends TestServer {

    public BindableServer() throws IOException {
        super();
    }

    private byte[] bindResponse = {
        0x30, 0x0C, 0x02, 0x01, 0x01, 0x61, 0x07, 0x0A,
        0x01, 0x00, 0x04, 0x00, 0x04, 0x00
    };

    public void run() {
        try {
            accepting = true;
            Socket socket = serverSock.accept();
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Read the LDAP BindRequest
            while (in.read() != -1) {
                in.skip(in.available());
                break;
            }

            // Write an LDAP BindResponse
            out.write(bindResponse);
            out.flush();
        } catch (IOException e) {
            // ignore
        }
    }
}

class DeadServer extends TestServer {

    public DeadServer() throws IOException {
        super();
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
}

public class LdapTimeoutTest {

    private static final ExecutorService testPool =
        Executors.newFixedThreadPool(3);
    private static final ScheduledExecutorService killSwitchPool =
        Executors.newScheduledThreadPool(3);
    public static int MIN_TIMEOUT = 18_000;

    static Hashtable createEnv() {
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
        return env;
    }

    public static void main(String[] args) throws Exception {

        InitialContext ctx = null;
        List<Future> results = new ArrayList<>();

        try {
            // run the DeadServerTest with no timeouts set
            // this should get stuck indefinitely, so we need to kill
            // it after a timeout
            System.out.println("Running connect timeout test with 20s kill switch");
            Hashtable env = createEnv();
            results.add(
                    testPool.submit(new DeadServerNoTimeoutTest(env, killSwitchPool)));

            // run the ReadServerTest with connect timeout set
            // this should get stuck indefinitely so we need to kill
            // it after a timeout
            System.out.println("Running read timeout test with 10ms connect timeout & 20s kill switch");
            Hashtable env1 = createEnv();
            env1.put("com.sun.jndi.ldap.connect.timeout", "10");
            results.add(testPool.submit(
                    new ReadServerNoTimeoutTest(env1, killSwitchPool)));

            // run the ReadServerTest with no timeouts set
            // this should get stuck indefinitely, so we need to kill
            // it after a timeout
            System.out.println("Running read timeout test with 20s kill switch");
            Hashtable env2 = createEnv();
            results.add(testPool.submit(
                    new ReadServerNoTimeoutTest(env2, killSwitchPool)));

            // run the DeadServerTest with connect / read timeouts set
            // this should exit after the connect timeout expires
            System.out.println("Running connect timeout test with 10ms connect timeout, 3000ms read timeout");
            Hashtable env3 = createEnv();
            env3.put("com.sun.jndi.ldap.connect.timeout", "10");
            env3.put("com.sun.jndi.ldap.read.timeout", "3000");
            results.add(testPool.submit(new DeadServerTimeoutTest(env3)));


            // run the ReadServerTest with connect / read timeouts set
            // this should exit after the connect timeout expires
            //
            // NOTE: commenting this test out as it is failing intermittently.
            //
            // System.out.println("Running read timeout test with 10ms connect timeout, 3000ms read timeout");
            // Hashtable env4 = createEnv();
            // env4.put("com.sun.jndi.ldap.connect.timeout", "10");
            // env4.put("com.sun.jndi.ldap.read.timeout", "3000");
            // results.add(testPool.submit(new ReadServerTimeoutTest(env4)));

            // run the DeadServerTest with connect timeout set
            // this should exit after the connect timeout expires
            System.out.println("Running connect timeout test with 10ms connect timeout");
            Hashtable env5 = createEnv();
            env5.put("com.sun.jndi.ldap.connect.timeout", "10");
            results.add(testPool.submit(new DeadServerTimeoutTest(env5)));

            // 8000487: Java JNDI connection library on ldap conn is
            // not honoring configured timeout
            System.out.println("Running simple auth connection test");
            Hashtable env6 = createEnv();
            env6.put("com.sun.jndi.ldap.connect.timeout", "10");
            env6.put("com.sun.jndi.ldap.read.timeout", "3000");
            env6.put(Context.SECURITY_AUTHENTICATION, "simple");
            env6.put(Context.SECURITY_PRINCIPAL, "user");
            env6.put(Context.SECURITY_CREDENTIALS, "password");
            results.add(testPool.submit(new DeadServerTimeoutTest(env6)));

            boolean testFailed = false;
            for (Future test : results) {
                while (!test.isDone()) {
                    if ((Boolean) test.get() == false)
                        testFailed = true;
                }
            }

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
            testFailed = (new DeadServerTimeoutSSLTest(sslenv).call()) ? false : true;

            if (testFailed) {
                throw new AssertionError("some tests failed");
            }

        } finally {
            LdapTimeoutTest.killSwitchPool.shutdown();
            LdapTimeoutTest.testPool.shutdown();
        }
    }

}

