/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8022584
 * @summary Some NetworkInterface methods can leak native memory
 * @run main/othervm/timeout=700 MemLeakTest
 */

/* Note: the test can cause a memory leak that's why othervm option is required
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;

public class MemLeakTest {

    /**
     * Memory leak is assumed, if application consumes more than specified amount of memory during its execution.
     * The number is given in Kb.
     */
    private static final long MEM_LEAK_THRESHOLD = 32 * 1024; // 32Mb

    public static void main(String[] args)
            throws Exception {

        if (!System.getProperty("os.name").equals("Linux")) {
            System.out.println("Test only runs on Linux");
            return;
        }

        // warm up
        accessNetInterfaces(3);

        long vMemBefore = getVMemSize();
        accessNetInterfaces(500_000);
        long vMemAfter = getVMemSize();

        long vMemDelta = vMemAfter - vMemBefore;
        if (vMemDelta > MEM_LEAK_THRESHOLD) {
            throw new Exception("FAIL: Virtual memory usage increased by " + vMemDelta + "Kb " +
                    "(greater than " + MEM_LEAK_THRESHOLD + "Kb)");
        }

        System.out.println("PASS: Virtual memory usage increased by " + vMemDelta + "Kb " +
                "(not greater than " + MEM_LEAK_THRESHOLD + "Kb)");
    }

    private static void accessNetInterfaces(int times) {
        try {
            Collection<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (int i = 0; i != times; ++i) {
                for (NetworkInterface netInterface : interfaces) {
                    netInterface.getMTU();
                    netInterface.isLoopback();
                    netInterface.isUp();
                    netInterface.isPointToPoint();
                    netInterface.supportsMulticast();
                }
            }
        } catch (SocketException ignore) {}
    }

    /**
     * Returns size of virtual memory allocated to the process in Kb.
     * Linux specific. On other platforms and in case of any errors returns 0.
     */
    private static long getVMemSize() {

        // Refer to the Linux proc(5) man page for details about /proc/self/stat file
        //
        // In short, this file contains status information about the current process
        // written in one line. The fields are separated with spaces.
        // The 23rd field is defined as 'vsize %lu   Virtual memory size in bytes'

        try (FileReader fileReader = new FileReader("/proc/self/stat");
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line = bufferedReader.readLine();
            return Long.parseLong(line.split(" ")[22]) / 1024;
        } catch (Exception ignore) {}
        return 0;
    }
}
