/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ProcessRunner {

    private static boolean waitForProcess(Process process, long timeoutSeconds) {
        long now = System.currentTimeMillis();
        long end = now + timeoutSeconds * 1000;
        do {
            try {
                if (process.waitFor(end - now, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException ignored) {
            }
            now = System.currentTimeMillis();
        } while (process.isAlive() && (now < end));

        process.destroyForcibly();
        return false;
    }

    private static String escapeString(String src) {
        StringBuilder sb = new StringBuilder();

        src.chars().forEachOrdered(code -> {
            if  ((code >= 32) && (code <= 126) && (code != 92) || (code == 9)) {
                // From space to ~ and tabs, excluding \ which is needed for escapes
                sb.append((char) code);
            } else {
                // All unreadable, encoding-dependent and \ are escaped
                sb.append("\\u" + String.format("%04X", code));
            }
        });

        return sb.toString();
    }

    private static void escapeRawFileContents(String name) throws IOException {
        Path rawPath = Path.of(name + ".utf8");
        Path escPath = Path.of(name);

        try (BufferedWriter writer = Files.newBufferedWriter(
                    escPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Stream<String> lines = Files.lines(rawPath)) {
            lines.map(ProcessRunner::escapeString).forEachOrdered(line -> {
                try {
                    writer.write(line);
                    writer.newLine();
                } catch (IOException e) {
                    throw new Error("Can't write file " + escPath);
                }
            });
            writer.flush();
        }
    }

    public static int runProcess(ProcessBuilder pb, String name, Phase phase)
            throws IOException, InterruptedException {
        final String nameAndPhase = name + "." + phase.suffix;
        pb.redirectError(new File(nameAndPhase + ".err.utf8"));
        pb.redirectOutput(new File(nameAndPhase + ".out.utf8"));
        Process process = pb.start();

        int result = -1;
        if (waitForProcess(process, phase.timeoutSeconds)) {
            try (FileWriter file = new FileWriter(nameAndPhase + ".exit")) {
                file.write(Integer.toString(process.exitValue()));
            }
            result = process.exitValue();
        } else {
            try (FileWriter file = new FileWriter(nameAndPhase + ".exit")) {
                file.write("TIMEOUT");
            }
        }

        if (phase == Phase.RUN || phase == Phase.GOLD_RUN) {
            escapeRawFileContents(nameAndPhase + ".err");
            escapeRawFileContents(nameAndPhase + ".out");
        }

        return result;
    }

}
