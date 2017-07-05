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

/* @test
 * @bug 4512028
 * @summary Check the tolerance on read timeouts.
 */
import java.net.*;
import java.io.*;

public class AccurateTimeout {

    static final int TOLERANCE = 100;

    static boolean skipTest() {
        String os = System.getProperty("os.name");
        if (os.equals("Windows 95") ||
            os.equals("Windows 98") ||
            os.equals("Windows Me")) {

            System.out.println("Due to an OS bug timeout tolerance cannot be tested on this OS");
            return true;
        }
        return false;
    }

    public static void main(String args[]) throws Exception {

        if (skipTest()) {
            return;
        }

        int failures = 0;
        int timeout;

        System.out.println("");
        System.out.println("Testing Socket.getInputStream().read() ...");
        System.out.println("");

        ServerSocket ss = new ServerSocket(0);
        Socket s1 = new Socket(InetAddress.getLocalHost(), ss.getLocalPort());
        Socket s2 = ss.accept();

        InputStream in = s1.getInputStream();

        timeout = 100;
        while (timeout < 2500) {
            s1.setSoTimeout(timeout);

            long startTime = System.currentTimeMillis();
            try {
                in.read();
            } catch (SocketTimeoutException e) {
            }
            long actual = System.currentTimeMillis() - startTime;

            System.out.print("excepted: " + timeout + " actual: " + actual);

            if (Math.abs(actual-timeout) > TOLERANCE) {
                System.out.print(" *** FAIL: outside tolerance");
                failures++;
            } else {
                System.out.print(" PASS.");
            }

            System.out.println("");
            timeout += 200;
        }

        s1.close();
        s2.close();
        ss.close();


        // ----------


        System.out.println("");
        System.out.println("Testing DatagramSocket.receive ...");
        System.out.println("");

        byte b[] = new byte[8];
        DatagramPacket p = new DatagramPacket(b, b.length);

        DatagramSocket ds = new DatagramSocket();

        timeout = 100;
        while (timeout < 2500) {
            ds.setSoTimeout(timeout);

            long startTime = System.currentTimeMillis();
            try {
                ds.receive(p);
            } catch (SocketTimeoutException e) {
            }
            long actual = System.currentTimeMillis() - startTime;

            System.out.print("excepted: " + timeout + " actual: " + actual);

            if (Math.abs(actual-timeout) > TOLERANCE) {
                System.out.print(" *** FAIL: outside tolerance");
                failures++;
            } else {
                System.out.print(" PASS.");
            }

            System.out.println("");
            timeout += 200;
        }

        ds.close();

        System.out.println("");

        // ---------

        if (failures > 0) {
            throw new Exception("Test failed: " + failures +
                " test(s) outside tolerance");
        }

    }

}
