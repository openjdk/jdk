/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7183292
 */
import java.net.*;
import java.util.*;
import java.io.*;
import com.sun.net.httpserver.*;

public class IllegalCookieNameTest {
    public static void main(String[] args) throws IOException {
        HttpServer s = null;
        try {
            InetSocketAddress addr = new InetSocketAddress(0);
            s = HttpServer.create(addr, 10);
            s.createContext("/", new HHandler());
            s.start();
            String u = "http://127.0.0.1:" + s.getAddress().getPort() + "/";
            CookieHandler.setDefault(new TestCookieHandler());
            URL url = new URL(u);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.getHeaderFields();
            System.out.println ("OK");
        } finally {
            s.stop(1);
        }
    }
}

class TestCookieHandler extends CookieHandler {
    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
        return new HashMap<String, List<String>>();
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
    }
}

class HHandler implements  HttpHandler {
    public void handle (HttpExchange e) {
        try {
            Headers h = e.getResponseHeaders();
            h.set ("Set-Cookie", "domain=; expires=Mon, 01-Jan-1990 00:00:00 GMT; path=/; domain=.foo.com");
            e.sendResponseHeaders(200, -1);
            e.close();
        } catch (Exception ex) {
            System.out.println (ex);
        }
    }
}
