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

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.TRACE;

/**
 * A troubleshooting utility to continuously collect system's network and thread dump information.
 */
public final class SystemDiagnosticsCollector implements AutoCloseable {

    private static final Logger LOGGER =
            Utils.getDebugLogger(SystemDiagnosticsCollector.class.getSimpleName()::toString, Utils.DEBUG);

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();

    private static final String[] NETSTAT_COMMAND = findOsSpecificNetstatCommand();

    private static String[] findOsSpecificNetstatCommand() {
        return Stream
                .of(
                        new String[]{"netstat", "-an"},
                        new String[]{"lsof", "-nPi"})
                .filter(SystemDiagnosticsCollector::commandSucceeds)
                .findFirst()
                .orElseGet(() -> {
                    LOGGER.log(ERROR, "failed to find a command to collect system's network information");
                    return null;
                });
    }

    private static boolean commandSucceeds(String[] command) {
        try {
            LOGGER.log(TRACE, "checking for command: %s", Arrays.asList(command));
            int exitStatus = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();
            return exitStatus == 0;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();     // Restore the interrupt
            return false;
        } catch (IOException _) {
            return false;
        }
    }

    private final Logger logger;

    private final Duration pollDuration;

    private final Thread thread;

    private volatile boolean running = true;

    public SystemDiagnosticsCollector(Class<?> clazz, Duration pollDuration) {
        String className = clazz.getSimpleName();
        this.logger = Utils.getDebugLogger(className::toString, Utils.DEBUG);
        this.pollDuration = pollDuration;
        this.thread = startThread(className);
    }

    private Thread startThread(String className) {
        String threadName = "%s-%s-%d".formatted(
                SystemDiagnosticsCollector.class.getSimpleName(),
                className,
                INSTANCE_COUNTER.incrementAndGet());
        Thread thread = new Thread(this::dumpDiagnosticsContinuously, threadName);
        thread.setDaemon(true);    // Avoid blocking JVM exit
        thread.start();
        return thread;
    }

    private void dumpDiagnosticsContinuously() {
        logger.log("starting diagnostics collector");
        while (running) {
            try {
                dumpDiagnostics();
                LockSupport.parkNanos(pollDuration.toNanos());
            } catch (Exception exception) {
                logger.log(ERROR, "diagnostics collection has failed", exception);
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();     // Restore the interrupt
                    if (running) {
                        logger.log("stopping diagnostics collector due to unexpected interrupt", exception);
                    }
                    return;
                }
            }
        }
    }

    public void dumpDiagnostics() throws IOException, InterruptedException {
        String fileNameTimestamp = Instant.now().toString().replaceAll("[-:]", "");
        logger.log("dumping diagnostics... (fileNameTimestamp=%s)", fileNameTimestamp);
        dumpNetwork(fileNameTimestamp);
        dumpThreads(fileNameTimestamp);
    }

    private void dumpNetwork(String fileNameTimestamp) throws IOException, InterruptedException {
        if (NETSTAT_COMMAND == null) {
            return;
        }
        Path path = createPath("dump-%s-net.txt", fileNameTimestamp);
        new ProcessBuilder(NETSTAT_COMMAND)
                .redirectOutput(path.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();
    }

    private static void dumpThreads(String fileNameTimestamp) throws IOException {
        Path path = createPath("dump-%s-thread.txt", fileNameTimestamp);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            Arrays
                    .stream(ManagementFactory.getThreadMXBean().dumpAllThreads(true, true))
                    .forEach(threadInfo -> {
                        try {
                            writer.write(threadInfo.toString());
                        } catch (IOException ioe) {
                            String message = "failed dumping threads to `%s`".formatted(path);
                            new RuntimeException(message, ioe).printStackTrace(System.err);
                        }
                    });
        }
    }

    private static Path createPath(String fileNameFormatPattern, Object... fileNameFormatArgs) {
        String fileName = fileNameFormatPattern.formatted(fileNameFormatArgs);
        // Using `Path.of(".")` to ensure that the created files will be packaged as a part of test results
        return Path.of(".").resolve(fileName);
    }

    @Override
    public synchronized void close() {
        if (running) {
            running = false;
            thread.interrupt();
        }
    }

}
