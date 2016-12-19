/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.function.Supplier;
import jdk.incubator.http.internal.common.HttpHeadersImpl;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class RedirectHandler implements Http2Handler {

    final Supplier<String> supplier;

    public RedirectHandler(Supplier<String> redirectSupplier) {
        supplier = redirectSupplier;
    }

    static String consume(InputStream is) throws IOException {
        byte[] b = new byte[1024];
        int i;
        StringBuilder sb = new StringBuilder();

        while ((i=is.read(b)) != -1) {
            sb.append(new String(b, 0, i, ISO_8859_1));
        }
        is.close();
        return sb.toString();
    }

    @Override
    public void handle(Http2TestExchange t) throws IOException {
        try {
            consume(t.getRequestBody());
            String location = supplier.get();
            System.err.println("RedirectHandler received request to " + t.getRequestURI());
            System.err.println("Redirecting to: " + location);
            HttpHeadersImpl map1 = t.getResponseHeaders();
            map1.addHeader("Location", location);
            t.sendResponseHeaders(301, 0);
            // return the number of bytes received (no echo)
            t.close();
        } catch (Throwable e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }
}
