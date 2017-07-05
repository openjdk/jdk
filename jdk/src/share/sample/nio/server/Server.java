/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.security.*;
import javax.net.ssl.*;

/**
 * The main server base class.
 * <P>
 * This class is responsible for setting up most of the server state
 * before the actual server subclasses take over.
 *
 * @author Mark Reinhold
 * @author Brad R. Wetmore
 */
public abstract class Server {

    ServerSocketChannel ssc;
    SSLContext sslContext = null;

    static private int PORT = 8000;
    static private int BACKLOG = 1024;
    static private boolean SECURE = false;

    Server(int port, int backlog,
            boolean secure) throws Exception {

        if (secure) {
            createSSLContext();
        }

        ssc = ServerSocketChannel.open();
        ssc.socket().setReuseAddress(true);
        ssc.socket().bind(new InetSocketAddress(port), backlog);
    }

    /*
     * If this is a secure server, we now setup the SSLContext we'll
     * use for creating the SSLEngines throughout the lifetime of
     * this process.
     */
    private void createSSLContext() throws Exception {

        char[] passphrase = "passphrase".toCharArray();

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("testkeys"), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    }

    abstract void runServer() throws Exception;

    static private void usage() {
        System.out.println(
            "Usage:  Server <type> [options]\n"
                + "     type:\n"
                + "             B1      Blocking/Single-threaded Server\n"
                + "             BN      Blocking/Multi-threaded Server\n"
                + "             BP      Blocking/Pooled-Thread Server\n"
                + "             N1      Nonblocking/Single-threaded Server\n"
                + "             N2      Nonblocking/Dual-threaded Server\n"
                + "\n"
                + "     options:\n"
                + "             -port port              port number\n"
                + "                 default:  " + PORT + "\n"
                + "             -backlog backlog        backlog\n"
                + "                 default:  " + BACKLOG + "\n"
                + "             -secure                 encrypt with SSL/TLS");
        System.exit(1);
    }

    /*
     * Parse the arguments, decide what type of server to run,
     * see if there are any defaults to change.
     */
    static private Server createServer(String args[]) throws Exception {
        if (args.length < 1) {
            usage();
        }

        int port = PORT;
        int backlog = BACKLOG;
        boolean secure = SECURE;

        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-port")) {
                checkArgs(i, args.length);
                port = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-backlog")) {
                checkArgs(i, args.length);
                backlog = Integer.valueOf(args[++i]);
            } else if (args[i].equals("-secure")) {
                secure = true;
            } else {
                usage();
            }
        }

        Server server = null;

        if (args[0].equals("B1")) {
            server = new B1(port, backlog, secure);
        } else if (args[0].equals("BN")) {
            server = new BN(port, backlog, secure);
        } else if (args[0].equals("BP")) {
            server = new BP(port, backlog, secure);
        } else if (args[0].equals("N1")) {
            server = new N1(port, backlog, secure);
        } else if (args[0].equals("N2")) {
            server = new N2(port, backlog, secure);
        }

        return server;
    }

    static private void checkArgs(int i, int len) {
        if ((i + 1) >= len) {
           usage();
        }
    }

    static public void main(String args[]) throws Exception {
        Server server = createServer(args);

        if (server == null) {
            usage();
        }

        System.out.println("Server started.");
        server.runServer();
    }
}
