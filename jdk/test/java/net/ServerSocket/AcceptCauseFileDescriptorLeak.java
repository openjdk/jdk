/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Test run from script, AcceptCauseFileDescriptorLeak.sh
 * author Edward Wang
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class AcceptCauseFileDescriptorLeak {
    private static final int REPS = 2048;

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
    }
}
