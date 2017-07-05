/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

public class PushHandler implements Http2Handler {

    final Path tempFile;
    final int loops;

    public PushHandler(int file_size, int loops) throws Exception {
        tempFile = TestUtil.getAFile(file_size);
        this.loops = loops;
    }

    int invocation = 0;

    public void handle(Http2TestExchange ee) {
        try {
            System.err.println ("Server: handle " + ee);
            invocation++;

            if (ee.serverPushAllowed()) {
                for (int i=0; i<loops; i++) {
                    InputStream is = new FileInputStream(tempFile.toFile());
                    URI u = new URI ("http://www.foo.com/" + Integer.toString(i));
                    HttpHeadersImpl h = new HttpHeadersImpl();
                    h.addHeader("X-foo", "bar");
                    ee.serverPush(u, h, is);
                }
                System.err.println ("Server: sent all pushes");
            }
            ee.sendResponseHeaders(200, 0);
            OutputStream os = ee.getResponseBody();
            InputStream iis = new FileInputStream(tempFile.toFile());
            iis.transferTo(os);
            os.close();
            iis.close();
        } catch (Exception ex) {
            System.err.println ("Server: exception " + ex);
        }
    }
}
