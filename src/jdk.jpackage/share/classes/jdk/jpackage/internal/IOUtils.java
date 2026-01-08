/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import jdk.jpackage.internal.model.PackagerException;

/**
 * IOUtils
 *
 * A collection of static utility methods.
 */
final class IOUtils {

    public static void copyFile(Path sourceFile, Path destFile)
            throws IOException {
        Files.createDirectories(destFile.getParent());

        Files.copy(sourceFile, destFile,
                   StandardCopyOption.REPLACE_EXISTING,
                   StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static void exec(ProcessBuilder pb)
            throws IOException {
        exec(pb, false, null, false, Executor.INFINITE_TIMEOUT);
    }

    // timeout in seconds. -1 will be return if process timeouts.
    public static void exec(ProcessBuilder pb, long timeout)
            throws IOException {
        exec(pb, false, null, false, timeout);
    }

    static void exec(ProcessBuilder pb, boolean testForPresenceOnly,
            PrintStream consumer, boolean writeOutputToFile, long timeout)
            throws IOException {
        exec(pb, testForPresenceOnly, consumer, writeOutputToFile,
                timeout, false);
    }

    static void exec(ProcessBuilder pb, boolean testForPresenceOnly,
            PrintStream consumer, boolean writeOutputToFile,
            long timeout, boolean quiet) throws IOException {
        List<String> output = new ArrayList<>();
        Executor exec = Executor.of(pb)
                .setWriteOutputToFile(writeOutputToFile)
                .setTimeout(timeout)
                .setQuiet(quiet)
                .setOutputConsumer(lines -> {
                    lines.forEach(output::add);
                    if (consumer != null) {
                        output.forEach(consumer::println);
                    }
                });

        if (testForPresenceOnly) {
            exec.execute();
        } else {
            exec.executeExpectSuccess();
        }
    }

    static void writableOutputDir(Path outdir) throws PackagerException {
        if (!Files.isDirectory(outdir)) {
            try {
                Files.createDirectories(outdir);
            } catch (IOException ex) {
                throw new PackagerException("error.cannot-create-output-dir",
                    outdir.toAbsolutePath().toString());
            }
        }

        if (!Files.isWritable(outdir)) {
            throw new PackagerException("error.cannot-write-to-output-dir",
                    outdir.toAbsolutePath().toString());
        }
    }

    public static long getPID(Process p) {
        try {
            return p.pid();
        } catch (UnsupportedOperationException ex) {
            Log.verbose(ex); // Just log exception and ignore it. This method
                             // is used for verbose output, so not a problem
                             // if unsupported.
            return -1;
        }
    }
}
