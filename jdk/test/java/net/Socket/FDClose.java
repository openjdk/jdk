/*
 * Copyright 1998-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 4152799
 * @summary  test to see if interrupting a socket accept closes fd0
 */
import java.net.*;
import java.io.*;
import java.util.*;

public class FDClose {

    static boolean isServerReady = false;

    public static void main(String[] args) throws Exception {

        Thread me = Thread.currentThread();

        // Put a thread waiting on SocketServer.Accept
        AReader test = new AReader();
        Thread readerThread = new Thread(test);
        readerThread.start();

        // wait for the server socket to be ready
        while (!isServerReady) {
            me.sleep(100);
        }

        // Interrupt the waiting thread
        readerThread.interrupt();

        // Wait another moment
        me.sleep(100);

        // Check to see if fd0 is closed
        System.in.available();
    }

    public static class AReader implements Runnable {
        public void run() {
            try {
                ServerSocket sock = new ServerSocket(0);
                isServerReady = true;
                sock.accept();
            } catch (Exception e) {
            }
        }
    }
}
