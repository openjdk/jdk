/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @summary configuring unconnected Socket before passing to implAccept can cause fd leak
 * @bug 6368984
 * @author Edward Wang
 */

import java.io.*;
import java.net.*;

public class AcceptCauseFileDescriptorLeak {
    private static final int REPS = 1000;

    public static void main(String[] args) throws Exception {
        final ServerSocket ss = new ServerSocket(0) {
            public Socket accept() throws IOException {
                Socket s = new Socket() { };
                s.setSoTimeout(10000);
                implAccept(s);
                return s;
            }
        };
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < REPS; i++) {
                        (new Socket("localhost", ss.getLocalPort())).close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        for (int i = 0; i < REPS; i++) {
            ss.accept().close();
        }
        ss.close();
        t.join();

        //
        // The threshold 20 below is a little arbitrary. The point here is that
        // the remaining open file descriptors should be constant independent
        // of REPS.
        //
        if (countOpenFD() > 20) {
            throw new RuntimeException("File descriptor leak detected.");
        }
    }


    /*
     * Actually, this approach to count open file descriptors only
     * works for Solaris/Linux. On Windows platform, this method
     * will simply return zero. So the test will always be passed
     * on Windows, too.
     */
    private static int countOpenFD() {
        File dirOfFD = new File("/proc/self/fd");
        File[] fds = dirOfFD.listFiles();

        if (fds != null)
            return fds.length;
        else
            return 0;
    }
}
