/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6358532
 * @run main/othervm AsyncDisconnect
 * @summary HttpURLConnection.disconnect doesn't really do the job
 */

import java.net.*;
import java.util.*;
import java.io.*;
import com.sun.net.httpserver.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class AsyncDisconnect implements Runnable
{
    com.sun.net.httpserver.HttpServer httpServer;
    MyHandler httpHandler;
    ExecutorService executorService;
    HttpURLConnection uc;

    public static void main(String[] args) {
        new AsyncDisconnect();
    }

    public AsyncDisconnect() {
        try {
            startHttpServer();
            doClient();
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }

    void doClient() {
        try {
            InetSocketAddress address = httpServer.getAddress();
            URL url = new URL("http://" + address.getHostName() + ":" + address.getPort() + "/test/");
            uc = (HttpURLConnection)url.openConnection();

            // create a thread that will disconnect the connection
            (new Thread(this)).start();

            uc.getInputStream();

            // if we reach here then we have failed
            throw new RuntimeException("Failed: We Expect a SocketException to be thrown");

        } catch (SocketException se) {
            // this is what we expect to happen and is OK.
            //System.out.println(se);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            httpServer.stop(1);
            executorService.shutdown();
        }
    }

    public void run() {
        // wait for the request to be sent to the server before calling disconnect
        try { Thread.sleep(2000); }
        catch (Exception e) {}

        uc.disconnect();
    }

    /**
     * Http Server
     */
    public void startHttpServer() throws IOException {
        httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        httpHandler = new MyHandler();

        HttpContext ctx = httpServer.createContext("/test/", httpHandler);

        executorService = Executors.newCachedThreadPool();
        httpServer.setExecutor(executorService);
        httpServer.start();
    }

    class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            // give the other thread a chance to close the connection
            try { Thread.sleep(4000); }
            catch (Exception e) {}

            t.sendResponseHeaders(400, -1);
            t.close();
        }
    }

}
