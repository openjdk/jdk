/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * Tests that a thread blocked in DatagramSocket.receive
 * throws a SocketException if the socket is asynchronously closed.
 */
import java.net.*;

public class DatagramSocket_receive extends AsyncCloseTest implements Runnable {
    DatagramSocket s;
    int timeout = 0;

    public DatagramSocket_receive() {
    }

    public DatagramSocket_receive(int timeout) {
        this.timeout = timeout;
    }

    public String description() {
        String s = "DatagramSocket.receive";
        if (timeout > 0) {
            s += " (timeout specified)";
        }
        return s;
    }

    public void run() {
        DatagramPacket p;
        try {

            byte b[] = new byte[1024];
            p  = new DatagramPacket(b, b.length);

            if (timeout > 0) {
                s.setSoTimeout(timeout);
            }
        } catch (Exception e) {
            failed(e.getMessage());
            return;
        }

        try {
            s.receive(p);
        } catch (SocketException se) {
            closed();
        } catch (Exception e) {
            failed(e.getMessage());
        }
    }

    public boolean go() throws Exception {
        s = new DatagramSocket();

        Thread thr = new Thread(this);
        thr.start();

        Thread.currentThread().sleep(1000);

        s.close();

        Thread.currentThread().sleep(1000);

        if (isClosed()) {
            return true;
        } else {
            failed("DatagramSocket.receive wasn't preempted");
            return false;
        }
    }
}
