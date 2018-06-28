/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.alloc;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;

public final class TraceStatisticsPrinter {
    private static final String SEP = ";";

    @SuppressWarnings("try")
    public static void printTraceStatistics(DebugContext debug, TraceBuilderResult result, String compilationUnitName) {
        try (DebugContext.Scope s = debug.scope("DumpTraceStatistics")) {
            if (debug.isLogEnabled(DebugContext.VERBOSE_LEVEL)) {
                print(debug, result, compilationUnitName);
            }
        } catch (Throwable e) {
            debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    protected static void print(DebugContext debug, TraceBuilderResult result, String compilationUnitName) {
        List<Trace> traces = result.getTraces();
        int numTraces = traces.size();

        try (Indent indent0 = debug.logAndIndent(DebugContext.VERBOSE_LEVEL, "<tracestatistics>")) {
            debug.log(DebugContext.VERBOSE_LEVEL, "<name>%s</name>", compilationUnitName != null ? compilationUnitName : "null");
            try (Indent indent1 = debug.logAndIndent(DebugContext.VERBOSE_LEVEL, "<traces>")) {
                printRawLine(debug, "tracenumber", "total", "min", "max", "numBlocks");
                for (int i = 0; i < numTraces; i++) {
                    AbstractBlockBase<?>[] t = traces.get(i).getBlocks();
                    double total = 0;
                    double max = Double.NEGATIVE_INFINITY;
                    double min = Double.POSITIVE_INFINITY;
                    for (AbstractBlockBase<?> block : t) {
                        double probability = block.probability();
                        total += probability;
                        if (probability < min) {
                            min = probability;
                        }
                        if (probability > max) {
                            max = probability;
                        }
                    }
                    printLine(debug, i, total, min, max, t.length);
                }
            }
            debug.log(DebugContext.VERBOSE_LEVEL, "</traces>");
        }
        debug.log(DebugContext.VERBOSE_LEVEL, "</tracestatistics>");

    }

    private static void printRawLine(DebugContext debug, Object tracenr, Object totalTime, Object minProb, Object maxProb, Object numBlocks) {
        debug.log(DebugContext.VERBOSE_LEVEL, "%s", String.join(SEP, tracenr.toString(), totalTime.toString(), minProb.toString(), maxProb.toString(), numBlocks.toString()));
    }

    private static void printLine(DebugContext debug, int tracenr, double totalTime, double minProb, double maxProb, int numBlocks) {
        printRawLine(debug, tracenr, totalTime, minProb, maxProb, numBlocks);
    }
}
