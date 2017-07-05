/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * Used to shutdown SimpleApplication (or a subclass). The argument to
 * this class is the name of a file that contains the TCP port number
 * on which SimpleApplication (or a subclass) is listening.
 *
 * Note: When this program returns, the SimpleApplication (or a subclass)
 * may still be running because the application has not yet reached the
 * shutdown check.
 */
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.File;
import java.io.FileInputStream;

public class ShutdownSimpleApplication {
    public static void main(String args[]) throws Exception {

        if (args.length != 1) {
            throw new RuntimeException("Usage: ShutdownSimpleApplication" +
                " port-file");
        }

        // read the (TCP) port number from the given file

        File f = new File(args[0]);
        FileInputStream fis = new FileInputStream(f);
        byte b[] = new byte[8];
        int n = fis.read(b);
        if (n < 1) {
            throw new RuntimeException("Empty port-file");
        }
        fis.close();

        String str = new String(b, 0, n, "UTF-8");
        System.out.println("INFO: Port number of SimpleApplication: " + str);
        int port = Integer.parseInt(str);

        // Now connect to the port (which will shutdown application)

        System.out.println("INFO: Connecting to port " + port +
            " to shutdown SimpleApplication ...");
        System.out.flush();

        Socket s = new Socket();
        s.connect( new InetSocketAddress(port) );
        s.close();

        System.out.println("INFO: done connecting to SimpleApplication.");
        System.out.flush();

        System.exit(0);
    }
}
