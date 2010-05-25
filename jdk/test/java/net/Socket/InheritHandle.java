/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
   @bug  6598160
   @summary Windows IPv6 Socket implementation doesn't set the handle to not inherit
   @author Chris Hegarty
 */

import java.net.ServerSocket;
import java.io.File;
import java.io.IOException;

/**
 * This test is only really applicable to Windows machines that are running IPv6, but
 * it should still pass on other platforms so we can just run it.
 */

public class InheritHandle
{
    static String java = System.getProperty("java.home") + File.separator +
                         "bin" + File.separator + "java";

    public static void main(String[] args) {
        if (args.length == 1) {
            doWait();
        } else {
            startTest();
        }

    }

    static void startTest() {
        ServerSocket ss;
        int port;
        Process process;

        // create a ServerSocket listening on any port
        try {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
            System.out.println("First ServerSocket listening on port " + port);
        } catch (IOException e) {
            System.out.println("Cannot create ServerSocket");
            e.printStackTrace();
            return;
        }

        // create another java process that simply waits. If the bug is present this
        // process will inherit the native IPv6 handle for ss and cause the second
        // ServerSocket constructor to throw a BindException
        try {
            process = Runtime.getRuntime().exec(java + " InheritHandle -doWait");
        } catch (IOException ioe) {
            System.out.println("Cannot create process");
            ioe.printStackTrace();
            return;
        }

        // Allow some time for the process to get started
        try {
            System.out.println("waiting...");
            Thread.sleep(2 * 1000);

            System.out.println("Now close the socket and try to create another" +
                               " one listening on the same port");
            ss.close();
            ss = new ServerSocket(port);
            System.out.println("Second ServerSocket created successfully");
            ss.close();

        } catch (InterruptedException ie) {
        } catch (IOException ioe) {
            throw new RuntimeException("Failed: " + ioe);
        } finally {
            process.destroy();
        }

        System.out.println("OK");
    }

    static void doWait() {
        try {
            Thread.sleep(200 * 1000);
        } catch (InterruptedException ie) {
        }
    }
}
