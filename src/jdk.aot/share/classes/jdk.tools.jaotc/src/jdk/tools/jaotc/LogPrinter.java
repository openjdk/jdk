/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Date;

import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.BinaryContainer;

final class LogPrinter {

    private static FileWriter logFile = null;
    private final Options options;
    private final PrintWriter log;

    LogPrinter(Main main, PrintWriter log) {
        this.options = main.options;
        this.log = log;
    }

    void printInfo(String message) {
        if (options.info) {
            log.print(message);
            log.flush();
        }
    }

    void printlnInfo(String message) {
        if (options.info) {
            log.println(message);
            log.flush();
        }
    }

    void printVerbose(String message) {
        if (options.verbose) {
            log.print(message);
            log.flush();
        }
    }

    void printlnVerbose(String message) {
        if (options.verbose) {
            log.println(message);
            log.flush();
        }
    }

    void printDebug(String message) {
        if (options.debug) {
            log.print(message);
            log.flush();
        }
    }

    void printlnDebug(String message) {
        if (options.debug) {
            log.println(message);
            log.flush();
        }
    }

    void printError(String message) {
        log.println("Error: " + message);
        log.flush();
    }

    void reportError(Throwable e) {
        log.println("Error: " + e.getMessage());
        if (options.info) {
            e.printStackTrace(log);
        }
        log.flush();
    }

    void reportError(String key, Object... args) {
        printError(MessageFormat.format(key, args));
    }

    private static String humanReadableByteCount(long bytes) {
        int unit = 1024;

        if (bytes < unit) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(unit, exp), pre);
    }

    void printMemoryUsage() {
        if (options.verbose) {
            MemoryUsage memusage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            float freeratio = 1f - (float) memusage.getUsed() / memusage.getCommitted();
            log.format(" [used: %-7s, comm: %-7s, freeRatio ~= %.1f%%]",
                            humanReadableByteCount(memusage.getUsed()),
                            humanReadableByteCount(memusage.getCommitted()),
                            freeratio * 100);
        }
    }

    private void printContainerInfo(ByteContainer container) {
        printlnVerbose(container.getContainerName() + ": " + container.getByteStreamSize() + " bytes");
    }

    void containersInfo(BinaryContainer binaryContainer) {
        printContainerInfo(binaryContainer.getHeaderContainer().getContainer());
        printContainerInfo(binaryContainer.getConfigContainer());
        printContainerInfo(binaryContainer.getKlassesOffsetsContainer());
        printContainerInfo(binaryContainer.getMethodsOffsetsContainer());
        printContainerInfo(binaryContainer.getKlassesDependenciesContainer());
        printContainerInfo(binaryContainer.getStubsOffsetsContainer());
        printContainerInfo(binaryContainer.getMethodMetadataContainer());
        printContainerInfo(binaryContainer.getCodeContainer());
        printContainerInfo(binaryContainer.getCodeSegmentsContainer());
        printContainerInfo(binaryContainer.getConstantDataContainer());
        printContainerInfo(binaryContainer.getKlassesGotContainer());
        printContainerInfo(binaryContainer.getCountersGotContainer());
        printContainerInfo(binaryContainer.getMetadataGotContainer());
        printContainerInfo(binaryContainer.getMethodStateContainer());
        printContainerInfo(binaryContainer.getOopGotContainer());
        printContainerInfo(binaryContainer.getMetaspaceNamesContainer());
    }

    static void openLog() {
        int v = Integer.getInteger("jdk.tools.jaotc.logCompilation", 0);
        if (v == 0) {
            logFile = null;
            return;
        }
        // Create log file in current directory
        String fileName = "aot_compilation" + new Date().getTime() + ".log";
        Path logFilePath = Paths.get("./", fileName);
        String logFileName = logFilePath.toString();
        try {
            // Create file to which we do not append
            logFile = new FileWriter(logFileName, false);
        } catch (IOException e) {
            System.out.println("Unable to open logfile :" + logFileName + "\nNo logs will be created");
            logFile = null;
        }
    }

    static void writeLog(String str) {
        if (logFile != null) {
            try {
                logFile.write(str + "\n");
                logFile.flush();
            } catch (IOException e) {
                // Print to console
                System.out.println(str + "\n");
            }
        }
    }

    static void closeLog() {
        if (logFile != null) {
            try {
                logFile.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

}
