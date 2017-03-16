/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.internal.method;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.debug.DebugMethodMetrics;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

/**
 * Interface for printing a collection of method metrics (e.g. during shutdown).
 */
public interface MethodMetricsPrinter {

    class Options {
        // @formatter:off
        @Option(help = "Dump method metrics to stdout on shutdown.", type = OptionType.Debug)
        public static final OptionValue<Boolean> MethodMeterPrintAscii = new OptionValue<>(false);
        @Option(help = "Dump method metrics to the given file in CSV format on shutdown.", type = OptionType.Debug)
        public static final OptionValue<String> MethodMeterFile = new OptionValue<>(null);
        // @formatter:on
    }

    static boolean methodMetricsDumpingEnabled() {
        return MethodMetricsPrinter.Options.MethodMeterPrintAscii.getValue() || MethodMetricsPrinter.Options.MethodMeterFile.getValue() != null;
    }

    /**
     * Prints the metrics to a destination specified by the implementor of this interface.
     *
     * @param metrics the set of collected method metrics during execution as defined by
     *            {@link DebugMethodMetrics}.
     */
    void printMethodMetrics(Collection<DebugMethodMetrics> metrics);

    class MethodMetricsASCIIPrinter implements MethodMetricsPrinter {
        private final OutputStream out;

        public MethodMetricsASCIIPrinter(OutputStream out) {
            this.out = out;
        }

        @Override
        public void printMethodMetrics(Collection<DebugMethodMetrics> metrics) {
            PrintStream p = new PrintStream(out);
            for (DebugMethodMetrics m : metrics) {
                ((MethodMetricsImpl) m).dumpASCII(p);
                p.println();
            }
        }

    }

    class MethodMetricsCompositePrinter implements MethodMetricsPrinter {
        private final List<MethodMetricsPrinter> printers;

        public MethodMetricsCompositePrinter(MethodMetricsPrinter... p) {
            printers = Arrays.asList(p);
        }

        public void registerPrinter(MethodMetricsPrinter printer) {
            printers.add(printer);
        }

        public void unregisterPrinter(MethodMetricsPrinter printer) {
            printers.remove(printer);
        }

        @Override
        public void printMethodMetrics(Collection<DebugMethodMetrics> metrics) {
            for (MethodMetricsPrinter p : printers) {
                p.printMethodMetrics(metrics);
            }
        }

    }

    class MethodMetricsCSVFilePrinter implements MethodMetricsPrinter {
        private FileOutputStream fw;

        public MethodMetricsCSVFilePrinter() {
            try {
                fw = new FileOutputStream(new File(Options.MethodMeterFile.getValue()));
            } catch (IOException e) {
                TTY.println("Cannot create file %s for method metrics dumping:%s", Options.MethodMeterFile.getValue(), e);
                throw new Error(e);
            }
        }

        @Override
        public void printMethodMetrics(Collection<DebugMethodMetrics> metrics) {
            // mm printing creates simple (R-parsable) csv files in long data format
            if (fw != null && metrics != null) {
                try (PrintStream p = new PrintStream(fw)) {
                    for (DebugMethodMetrics m : metrics) {
                        if (m instanceof MethodMetricsImpl) {
                            ((MethodMetricsImpl) m).dumpCSV(p);
                            p.println();
                        }
                    }
                }
                try {
                    fw.close();
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }

    }
}
