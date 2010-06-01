/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4620362
 * @run main/othervm TunnelThroughProxy
 * @summary JSSE not returning proper exception on unknown host
 */

import java.net.*;
import java.io.*;

public class TunnelThroughProxy {
    public static void main(String[] args) throws Exception {
        try {
            setupProxy();
            URL u = new URL("https://www.nonexistent-site.com/");
            URLConnection uc = u.openConnection();
            InputStream is = uc.getInputStream();
            is.close();
        } catch (Exception e) {
            if (!e.getMessage().matches(".*HTTP\\/.*500.*")) {
                throw new RuntimeException(e);
            }
        }
    }
    static void setupProxy() throws IOException {
        ProxyTunnelServer pserver = new ProxyTunnelServer();

        // disable proxy authentication
        pserver.needUserAuth(false);
        pserver.start();
        System.setProperty("https.proxyHost", "localhost");
        System.setProperty("https.proxyPort", String.valueOf(
                                        pserver.getPort()));
    }
}
