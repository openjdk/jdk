/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;

/**
 * Implements a basic static EchoHandler for an HTTP server
 */
public class EchoHandler implements HttpHandler {

    byte[] read(InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        byte[] result = new byte[0];

        while (true) {
            int n = is.read(buf);
            if (n > 0) {
                byte[] b1 = new byte[result.length + n];
                System.arraycopy(result, 0, b1, 0, result.length);
                System.arraycopy(buf, 0, b1, result.length, n);
                result = b1;
            } else if (n == -1) {
                return result;
            }
        }
    }

    public void handle (HttpExchange t)
        throws IOException
    {
        InputStream is = t.getRequestBody();
        Headers map = t.getRequestHeaders();
        String fixedrequest = map.getFirst ("XFixed");

        // return the number of bytes received (no echo)
        String summary = map.getFirst ("XSummary");
        if (fixedrequest != null && summary == null)  {
            byte[] in = read(is);
            t.sendResponseHeaders(200, in.length);
            OutputStream os = t.getResponseBody();
            os.write(in);
            close(t, os);
            close(t, is);
        } else {
            OutputStream os = t.getResponseBody();
            byte[] buf = new byte[64 * 1024];
            t.sendResponseHeaders(200, 0);
            int n, count=0;;

            while ((n = is.read(buf)) != -1) {
                if (summary == null) {
                    os.write(buf, 0, n);
                }
                count += n;
            }
            if (summary != null) {
                String s = Integer.toString(count);
                os.write(s.getBytes());
            }
            close(t, os);
            close(t, is);
        }
    }

    protected void close(OutputStream os) throws IOException {
        os.close();
    }
    protected void close(InputStream is) throws IOException {
        is.close();
    }
    protected void close(HttpExchange t, OutputStream os) throws IOException {
        close(os);
    }
    protected void close(HttpExchange t, InputStream is) throws IOException {
        close(is);
    }
}
