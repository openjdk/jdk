/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessHandle;

/*
 * @test
 * @bug 8239893
 * @summary Verify that handles for processes that terminate do not accumulate
 * @requires (os.family == "windows")
 * @run main/native CheckHandles
 */
public class CheckHandles {

    // Return the current process handle count
    private static native long getProcessHandleCount();

    public static void main(String[] args) throws Exception {
        System.loadLibrary("CheckHandles");

        System.out.println("mypid: " + ProcessHandle.current().pid());
        long minHandles = Long.MAX_VALUE;
        long maxHandles = 0L;
        int MAX_SPAWN = 50;
        for (int i = 0; i < MAX_SPAWN; i++) {
            try {
                Process testProcess = new ProcessBuilder("cmd", "/c", "dir").start();

                Thread outputConsumer = new Thread(() -> consumeStream(testProcess.pid(), testProcess.getInputStream()));
                outputConsumer.setDaemon(true);
                outputConsumer.start();
                Thread errorConsumer = new Thread(() -> consumeStream(testProcess.pid(), testProcess.getErrorStream()));
                errorConsumer.setDaemon(true);
                errorConsumer.start();

                testProcess.waitFor();
                System.gc();
                outputConsumer.join();
                errorConsumer.join();
                long count = getProcessHandleCount();
                if (count < 0)
                    throw new AssertionError("getProcessHandleCount failed");
                minHandles =  Math.min(minHandles, count);
                maxHandles =  Math.max(maxHandles, count);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                throw e;
            }
        }
        final long ERROR_PERCENT = 10;
        final long ERROR_THRESHOLD = // 10% increase over min to passing max
                minHandles + ((minHandles + ERROR_PERCENT - 1) / ERROR_PERCENT);
        if (maxHandles >= ERROR_THRESHOLD) {
            System.out.println("Processes started: " + MAX_SPAWN);
            System.out.println("minhandles: " + minHandles);
            System.out.println("maxhandles: " + maxHandles);
            throw new AssertionError("Handle use increased by more than " + ERROR_PERCENT + " percent.");
        }
    }

    private static void consumeStream(long pid, InputStream inputStream) {
        BufferedReader reader = null;
        try {
            int lines = 0;
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while (reader.readLine() != null) {
                lines++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
