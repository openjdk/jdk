/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4401485
 * @requires (os.family == "windows")
 * @modules java.base/sun.net.www.protocol.file
 * @library /test/lib
 * @summary  Check that URL.openConnection() doesn't open connection to UNC
 */

import jtreg.SkippedException;
import sun.net.www.protocol.file.FileURLConnection;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

public class UNCTest {
    public static void main(String[] args) throws Exception {
        // Get the "computer name" for this host
        String hostName = InetAddress.getLocalHost().getHostName();

        // UNC path which always exists with Administrative Shares enabled
        String path = "\\\\" + hostName + "\\C$\\Windows";

        // Skip test if Administrative Shares is disabled
        if (! new File(path).exists()) {
            throw new SkippedException("Administrative Shares not enabled");
        }

        // File URL for the UNC path
        URL url = new URI("file://" + hostName + "/C$/Windows").toURL();

        // Should open a FileURLConnection for the UNC path
        URLConnection conn = url.openConnection();

        // Sanity check that the connection is a FileURLConnection
        if (! (conn instanceof FileURLConnection)) {
            throw new Exception("Expected FileURLConnection, instead got "
                    + conn.getClass().getName());
        }

        // Verify that the connection is not in an already connected state
        conn.setRequestProperty( "User-Agent", "Java" );
    }
}
