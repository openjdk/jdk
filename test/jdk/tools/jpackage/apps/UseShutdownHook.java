/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class UseShutdownHook {

    public static void main(String[] args) throws InterruptedException {
        trace("Started");

        var outputFile = Path.of(args[0]);
        trace(String.format("Write output in [%s] file", outputFile));

        var shutdownTimeoutSeconds = Integer.parseInt(args[1]);
        trace(String.format("Automatically shutdown the app in %ss", shutdownTimeoutSeconds));

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                output(outputFile, "shutdown hook executed");
            }
        });

        var startTime = System.currentTimeMillis();
        var lock = new Object();
        do {
            synchronized (lock) {
                lock.wait(shutdownTimeoutSeconds * 1000);
            }
        } while ((System.currentTimeMillis() - startTime) < (shutdownTimeoutSeconds * 1000));

        output(outputFile, "exit");
    }

    private static void output(Path outputFilePath, String msg) {

        trace(String.format("Writing [%s] into [%s]", msg, outputFilePath));

        try {
            Files.createDirectories(outputFilePath.getParent());
            Files.writeString(outputFilePath, msg, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void trace(String msg) {
        Date time = new Date(System.currentTimeMillis());
        msg = String.format("UseShutdownHook [%s]: %s", SDF.format(time), msg);
        System.out.println(msg);
        try {
            Files.write(traceFile, List.of(msg), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private static final Path traceFile = Path.of(System.getProperty("jpackage.test.trace-file"));
}
