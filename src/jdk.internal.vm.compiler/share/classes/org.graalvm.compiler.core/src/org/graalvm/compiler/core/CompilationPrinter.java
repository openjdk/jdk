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
package org.graalvm.compiler.core;

import static org.graalvm.compiler.core.GraalCompilerOptions.PrintCompilation;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.Management;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Utility for printing an informational line to {@link TTY} upon completion of compiling a method.
 */
public final class CompilationPrinter {

    private final CompilationIdentifier id;
    private final JavaMethod method;
    private final int entryBCI;
    private final long start;
    private final long allocatedBytesBefore;

    /**
     * Gets an object that will report statistics for a compilation if
     * {@link GraalCompilerOptions#PrintCompilation} is enabled and {@link TTY} is not suppressed.
     * This method should be called just before a compilation starts as it captures pre-compilation
     * data for the purpose of {@linkplain #finish(CompilationResult) printing} the post-compilation
     * statistics.
     *
     * @param options used to get the value of {@link GraalCompilerOptions#PrintCompilation}
     * @param id the identifier for the compilation
     * @param method the method for which code is being compiled
     * @param entryBCI the BCI at which compilation starts
     */
    public static CompilationPrinter begin(OptionValues options, CompilationIdentifier id, JavaMethod method, int entryBCI) {
        if (PrintCompilation.getValue(options) && !TTY.isSuppressed()) {
            try {
                Class.forName("java.lang.management.ManagementFactory");
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("PrintCompilation option requires java.management module");
            }
            return new CompilationPrinter(id, method, entryBCI);
        }
        return DISABLED;
    }

    private static final CompilationPrinter DISABLED = new CompilationPrinter();

    private CompilationPrinter() {
        this.method = null;
        this.id = null;
        this.entryBCI = -1;
        this.start = -1;
        this.allocatedBytesBefore = -1;
    }

    private CompilationPrinter(CompilationIdentifier id, JavaMethod method, int entryBCI) {
        this.method = method;
        this.id = id;
        this.entryBCI = entryBCI;

        final long threadId = Thread.currentThread().getId();
        start = System.nanoTime();
        allocatedBytesBefore = getAllocatedBytes(threadId);
    }

    private String getMethodDescription() {
        return String.format("%-30s %-70s %-45s %-50s %s", id.toString(CompilationIdentifier.Verbosity.ID),
                        method.getDeclaringClass().getName(), method.getName(),
                        method.getSignature().toMethodDescriptor(),
                        entryBCI == JVMCICompiler.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + entryBCI + ") ");
    }

    /**
     * Notifies this object that the compilation finished and the informational line should be
     * printed to {@link TTY}.
     */
    public void finish(CompilationResult result) {
        if (id != null) {
            final long threadId = Thread.currentThread().getId();
            final long stop = System.nanoTime();
            final long duration = (stop - start) / 1000000;
            final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
            final int bytecodeSize = result != null ? result.getBytecodeSize() : 0;
            final long allocatedBytesAfter = getAllocatedBytes(threadId);
            final long allocatedKBytes = (allocatedBytesAfter - allocatedBytesBefore) / 1024;

            TTY.println(getMethodDescription() + String.format(" | %4dms %5dB %5dB %5dkB", duration, bytecodeSize, targetCodeSize, allocatedKBytes));
        }
    }

    static com.sun.management.ThreadMXBean threadMXBean;

    static long getAllocatedBytes(long threadId) {
        if (threadMXBean == null) {
            threadMXBean = (com.sun.management.ThreadMXBean) Management.getThreadMXBean();
        }
        return threadMXBean.getThreadAllocatedBytes(threadId);
    }
}
