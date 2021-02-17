/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * @test
 * @bug 8252883
 * @summary tests the handling of AccessDeniedException due to delay in Windows deletion.
 * @modules java.logging/java.util.logging:open
 * @run main/othervm FileHandlerAccessTest process 20
 * @run main/othervm FileHandlerAccessTest thread 20
 * @author evwhelan
 */

public class FileHandlerAccessTest {
    public static void main(String[] args) {
        if (!(args.length == 2 || args.length == 1)) {
            throw new IllegalArgumentException("Usage error: expects java FileHandlerAccessTest [process/thread] <count>");
        } else if (args.length == 2) {
            var type = args[0];
            var count = Integer.parseInt(args[1]);

            for (var i = 0; i < count; i++) {
                System.out.println("Testing with arguments: type=" + type + ", count="+count);
                if (type.equals("process")) {
                    new Thread(FileHandlerAccessTest::accessProcess).start();
                }
                else if (type.equals("thread")) {
                    new Thread(FileHandlerAccessTest::access).start();
                }
            }
        } else {
            access();
        }
    }

    private static void access() {
        try {
            var handler = new FileHandler("sample%g.log", 1048576, 2, true);
            handler.publish(new LogRecord(Level.SEVERE, "TEST"));
            handler.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void accessProcess() {
        final var javaHome = System.getProperty("java.home");
        var name = Thread.currentThread().getName();
        BufferedReader bufferedReader = null;
        Process childProcess = null;
        final String className = new Object(){}.getClass().getEnclosingClass().getName();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(javaHome + File.separator + "bin" + File.separator + "java", "-cp", ".", className, "doProcess");
            processBuilder.redirectErrorStream(true);
            childProcess = processBuilder.start();

            bufferedReader = new BufferedReader(new InputStreamReader(childProcess.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                System.out.println(name + "\t|" + line);
            }

            int exitCode = childProcess.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("An error occured in the child process.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (childProcess != null) {
                childProcess.destroy();
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
