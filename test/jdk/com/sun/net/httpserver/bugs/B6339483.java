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

/**
 * @test
 * @bug 6339483
 * @summary  NullPointerException when creating a HttpContext with no handler
 */

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

public class B6339483 {

    public static void main (String[] args) throws Exception {
        InetSocketAddress addr = new InetSocketAddress (0);
        HttpServer server = HttpServer.create (addr, 0);
        HttpContext ctx = server.createContext ("/test");
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor (executor);
        server.start ();

        URL url = new URL ("http://localhost:"+server.getAddress().getPort()+"/test/foo.html");
        HttpURLConnection urlc = (HttpURLConnection)url.openConnection ();
        try {
            InputStream is = urlc.getInputStream();
            int c = 0;
            while (is.read()!= -1) {
                c ++;
            }
        } catch (IOException e) {
            server.stop(2);
            executor.shutdown();
            System.out.println ("OK");
        }
    }
}
