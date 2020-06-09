/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.jpackage.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final public class Executor {

    Executor() {
    }

    Executor setOutputConsumer(Consumer<Stream<String>> v) {
        outputConsumer = v;
        return this;
    }

    Executor saveOutput(boolean v) {
        saveOutput = v;
        return this;
    }

    Executor setWriteOutputToFile(boolean v) {
        writeOutputToFile = v;
        return this;
    }

    Executor setProcessBuilder(ProcessBuilder v) {
        pb = v;
        return this;
    }

    Executor setCommandLine(String... cmdline) {
        return setProcessBuilder(new ProcessBuilder(cmdline));
    }

    List<String> getOutput() {
        return output;
    }

    Executor executeExpectSuccess() throws IOException {
        int ret = execute();
        if (0 != ret) {
            throw new IOException(
                    String.format("Command %s exited with %d code",
                            createLogMessage(pb), ret));
        }
        return this;
    }

    int execute() throws IOException {
        output = null;

        boolean needProcessOutput = outputConsumer != null || Log.isVerbose() || saveOutput;
        File outputFile = null;
        if (needProcessOutput) {
            pb.redirectErrorStream(true);
            if (writeOutputToFile) {
                outputFile = File.createTempFile("jpackageOutputTempFile", ".tmp");
                pb.redirectOutput(outputFile);
            }
        } else {
            // We are not going to read process output, so need to notify
            // ProcessBuilder about this. Otherwise some processes might just
            // hang up (`ldconfig -p`).
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }

        Log.verbose(String.format("Running %s", createLogMessage(pb)));
        Process p = pb.start();

        int code = 0;
        if (writeOutputToFile) {
            try {
                code = p.waitFor();
            } catch (InterruptedException ex) {
                Log.verbose(ex);
                throw new RuntimeException(ex);
            }
        }

        if (needProcessOutput) {
            final List<String> savedOutput;
            Supplier<Stream<String>> outputStream;

            if (writeOutputToFile) {
                savedOutput = Files.readAllLines(outputFile.toPath());
                outputFile.delete();
                outputStream = () -> {
                    if (savedOutput != null) {
                        return savedOutput.stream();
                    }
                    return null;
                };

                if (Log.isVerbose()) {
                    outputStream.get().forEach(Log::verbose);
                }

                if (outputConsumer != null) {
                    outputConsumer.accept(outputStream.get());
                }
            } else {
                try (var br = new BufferedReader(new InputStreamReader(
                        p.getInputStream()))) {
                    // Need to save output if explicitely requested (saveOutput=true) or
                    // if will be used used by multiple consumers
                    if ((outputConsumer != null && Log.isVerbose()) || saveOutput) {
                        savedOutput = br.lines().collect(Collectors.toList());
                        if (saveOutput) {
                            output = savedOutput;
                        }
                    } else {
                        savedOutput = null;
                    }

                    outputStream = () -> {
                        if (savedOutput != null) {
                            return savedOutput.stream();
                        }
                        return br.lines();
                    };

                    if (Log.isVerbose()) {
                        outputStream.get().forEach(Log::verbose);
                    }

                    if (outputConsumer != null) {
                        outputConsumer.accept(outputStream.get());
                    }

                    if (savedOutput == null) {
                        // For some processes on Linux if the output stream
                        // of the process is opened but not consumed, the process
                        // would exit with code 141.
                        // It turned out that reading just a single line of process
                        // output fixes the problem, but let's process
                        // all of the output, just in case.
                        br.lines().forEach(x -> {});
                    }
                }
            }
        }

        try {
            if (!writeOutputToFile) {
                code = p.waitFor();
            }
            return code;
        } catch (InterruptedException ex) {
            Log.verbose(ex);
            throw new RuntimeException(ex);
        }
    }

    static Executor of(String... cmdline) {
        return new Executor().setCommandLine(cmdline);
    }

    static Executor of(ProcessBuilder pb) {
        return new Executor().setProcessBuilder(pb);
    }

    private static String createLogMessage(ProcessBuilder pb) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s", pb.command()));
        if (pb.directory() != null) {
            sb.append(String.format("in %s", pb.directory().getAbsolutePath()));
        }
        return sb.toString();
    }

    private ProcessBuilder pb;
    private boolean saveOutput;
    private boolean writeOutputToFile;
    private List<String> output;
    private Consumer<Stream<String>> outputConsumer;
}
