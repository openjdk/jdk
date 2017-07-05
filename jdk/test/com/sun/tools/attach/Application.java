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
 * A simple "Application" used by the Attach API unit tests. This application is
 * launched by the test. It binds to a random port and shuts down when somebody
 * connects to that port.
 * Used port and pid are written both to stdout and to a specified file.
 */
import java.net.Socket;
import java.net.ServerSocket;
import java.io.PrintWriter;
import jdk.testlibrary.ProcessTools;

public class Application {
    public static void main(String args[]) throws Exception {
        // bind to a random port
        if (args.length < 1) {
            System.err.println("First argument should be path to output file.");
        }
        String outFileName = args[0];

        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        int pid = ProcessTools.getProcessId();

        System.out.println("shutdownPort=" + port);
        System.out.println("pid=" + pid);
        System.out.flush();

        try (PrintWriter writer = new PrintWriter(outFileName)) {
            writer.println("shutdownPort=" + port);
            writer.println("pid=" + pid);
            writer.println("done");
            writer.flush();
        }

        // wait for test harness to connect
        Socket s = ss.accept();
        s.close();
        ss.close();
    }
}
