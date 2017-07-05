/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6299712
 * @library ../../httptest/
 * @build HttpCallback HttpServer ClosedChannelList HttpTransaction
 * @run main/othervm B6299712
 * @summary  NullPointerException in sun.net.www.protocol.http.HttpURLConnection.followRedirect
 */

import java.net.*;
import java.io.*;
import java.util.*;

/*
 * Test Description:
 *      - main thread run as a http client
 *      - another thread runs a http server, which redirect the first call to "/redirect"
 *        and return '200 OK' for the successive call
 *      - a global ResponseCache instance is installed, which return DeployCacheResponse
 *        for url ends with "/redirect", i.e. the url redirected to by our simple http server,
 *        and null for other url.
 *      - the whole result is that the first call will be served by our simple
 *        http server and is redirected to "/redirect". The successive call will be done
 *        automatically by HttpURLConnection, which will be served by DeployCacheResponse.
 *        The NPE will be thrown on the second round if the bug is there.
 */
public class B6299712 {
    static SimpleHttpTransaction httpTrans;
    static HttpServer server;

    public static void main(String[] args) throws Exception {
        ResponseCache.setDefault(new DeployCacheHandler());
        startHttpServer();

        makeHttpCall();
    }

    public static void startHttpServer() {
        try {
            httpTrans = new SimpleHttpTransaction();
            server = new HttpServer(httpTrans, 1, 10, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void makeHttpCall() {
        try {
            System.out.println("http server listen on: " + server.getLocalPort());
            URL url = new URL("http" , InetAddress.getLocalHost().getHostAddress(),
                                server.getLocalPort(), "/");
            HttpURLConnection uc = (HttpURLConnection)url.openConnection();
            System.out.println(uc.getResponseCode());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            server.terminate();
        }
    }
}

class SimpleHttpTransaction implements HttpCallback {
    /*
     * Our http server which simply redirect first call
     */
    public void request(HttpTransaction trans) {
        try {
            String path = trans.getRequestURI().getPath();
            if (path.equals("/")) {
                // the first call, redirect it
                String location = "/redirect";
                trans.addResponseHeader("Location", location);
                trans.sendResponse(302, "Moved Temporarily");
            } else {
                // the second call
                trans.sendResponse(200, "OK");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class DeployCacheHandler extends java.net.ResponseCache {
    private boolean inCacheHandler = false;
    private boolean _downloading = false;

    public synchronized CacheResponse get(final URI uri, String rqstMethod,
            Map requestHeaders) throws IOException {
        System.out.println("get!!!: " + uri);
        try {
            if (!uri.toString().endsWith("redirect")) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new DeployCacheResponse(new EmptyInputStream(), new HashMap());
    }

    public synchronized CacheRequest put(URI uri, URLConnection conn)
    throws IOException {
        URL url = uri.toURL();
        return new DeployCacheRequest(url, conn);

    }
}

class DeployCacheRequest extends java.net.CacheRequest {

    private URL _url;
    private URLConnection _conn;
    private boolean _downloading = false;

    DeployCacheRequest(URL url, URLConnection conn) {
        _url = url;
        _conn = conn;
    }

    public void abort() {

    }

    public OutputStream getBody() throws IOException {

        return null;
    }
}

class DeployCacheResponse extends java.net.CacheResponse {
    protected InputStream is;
    protected Map headers;

    DeployCacheResponse(InputStream is, Map headers) {
        this.is = is;
        this.headers = headers;
    }

    public InputStream getBody() throws IOException {
        return is;
    }

    public Map getHeaders() throws IOException {
        return headers;
    }
}

class EmptyInputStream extends InputStream {
    public EmptyInputStream() {
    }

    public int read()
    throws IOException {
        return -1;
    }
}
