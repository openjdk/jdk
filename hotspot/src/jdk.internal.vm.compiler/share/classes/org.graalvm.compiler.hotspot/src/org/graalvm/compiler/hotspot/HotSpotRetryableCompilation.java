/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.debug.Debug.VERBOSE_LEVEL;
import static org.graalvm.compiler.debug.DelegatingDebugConfig.Feature.DUMP_METHOD;
import static org.graalvm.compiler.debug.DelegatingDebugConfig.Level.DUMP;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Dump;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DumpPath;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.ForceDebugEnable;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintCFGFileName;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphFileName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugRetryableTask;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugConfigCustomizer;

import jdk.vm.ci.code.BailoutException;

/**
 * Utility for retrying a compilation that throws an exception where the retry enables extra logging
 * for subsequently diagnosing the failure.
 */
public abstract class HotSpotRetryableCompilation<T> extends DebugRetryableTask<T> {

    protected final OptionValues originalOptions;
    protected final HotSpotGraalRuntimeProvider runtime;

    public HotSpotRetryableCompilation(HotSpotGraalRuntimeProvider runtime, OptionValues options) {
        this.runtime = runtime;
        this.originalOptions = options;
    }

    /**
     * Gets a value that represents the compilation unit being compiled.
     */
    @Override
    public abstract String toString();

    private static String sanitizedFileName(String name) {
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            try {
                Paths.get(String.valueOf(c));
            } catch (InvalidPathException e) {
                buf.append('_');
            }
            buf.append(c);
        }
        return buf.toString();
    }

    @Override
    protected boolean onRetry(Throwable t) {
        if (t instanceof BailoutException) {
            return false;
        }

        if (!Debug.isEnabled()) {
            TTY.printf("Error while compiling %s due to %s.%nRe-run with -D%s%s=true to capture graph dumps upon a compilation failure.%n", this,
                            t, HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX, ForceDebugEnable.getName());
            return false;
        }

        if (Dump.hasBeenSet(originalOptions)) {
            // If dumping is explicitly enabled, Graal is being debugged
            // so don't interfere with what the user is expecting to see.
            return false;
        }

        String outputDirectory = runtime.getOutputDirectory();
        if (outputDirectory == null) {
            return false;
        }
        String dumpName = sanitizedFileName(toString());
        File dumpPath = new File(outputDirectory, dumpName);
        dumpPath.mkdirs();
        if (!dumpPath.exists()) {
            TTY.println("Warning: could not create dump directory " + dumpPath);
            return false;
        }

        TTY.println("Retrying compilation of " + this + " due to " + t);
        retryLogPath = new File(dumpPath, "retry.log").getPath();
        log("Exception causing retry", t);
        retryDumpHandlers = new ArrayList<>();
        retryOptions = new OptionValues(originalOptions,
                        PrintCFGFileName, dumpName,
                        PrintGraphFileName, dumpName,
                        DumpPath, dumpPath.getPath());
        override(DUMP, VERBOSE_LEVEL).enable(DUMP_METHOD);
        new GraalDebugConfigCustomizer().customize(this);
        return true;
    }

    private Collection<DebugDumpHandler> retryDumpHandlers;
    private OptionValues retryOptions;
    private String retryLogPath;

    /**
     * Prints a message to a retry log file.
     *
     * @param message the message to print
     * @param t if non-{@code null}, the stack trace for this exception is written to the retry log
     *            after {@code message}
     */
    protected void log(String message, Throwable t) {
        if (retryLogPath != null) {
            try (PrintStream retryLog = new PrintStream(new FileOutputStream(retryLogPath), true)) {
                StringBuilder buf = new StringBuilder(Thread.currentThread() + ": " + message);
                if (t != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    t.printStackTrace(new PrintStream(baos));
                    buf.append(System.lineSeparator()).append(baos.toString());
                }
                retryLog.println(buf);
            } catch (FileNotFoundException e) {
                TTY.println("Warning: could not open retry log file " + retryLogPath + " [" + e + "]");
            }
        }
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return retryDumpHandlers;
    }

    @Override
    public OptionValues getOptions() {
        return retryOptions;
    }
}
