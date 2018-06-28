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


package org.graalvm.compiler.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * Options that configure a {@link DebugContext} and related functionality.
 */
public class DebugOptions {
    static class DeprecatedOptionKey<T> extends OptionKey<T> {
        private final OptionKey<T> replacement;

        DeprecatedOptionKey(OptionKey<T> replacement) {
            super(replacement.getDefaultValue());
            this.replacement = replacement;
        }

        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T oldValue, T newValue) {
            // Ideally we'd use TTY here but it may not yet be initialized.
            System.err.printf("Warning: the %s option is deprecated - use %s instead%n", getName(), replacement.getName());
            replacement.update(values, newValue);
        }
    }

    // @formatter:off
    @Option(help = "Comma separated names of timers that are enabled irrespective of the value for Time option. " +
                   "An empty value enables all timers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Timers = new OptionKey<>(null);
    @Option(help = "Comma separated names of counters that are enabled irrespective of the value for Count option. " +
                   "An empty value enables all counters unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Counters = new OptionKey<>(null);
    @Option(help = "Comma separated names of memory usage trackers that are enabled irrespective of the value for TrackMemUse option. " +
                   "An empty value enables all memory usage trackers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> MemUseTrackers = new OptionKey<>(null);

    @Option(help = "Pattern for specifying scopes in which counters are enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all counters unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Count = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which memory use tracking is enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all memory use trackers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> TrackMemUse = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which timing is enabled. " +
                   "See the Dump option for the pattern syntax. " +
                   "An empty value enables all timers unconditionally.", type = OptionType.Debug)
    public static final OptionKey<String> Time = new OptionKey<>(null);

    @Option(help = "Pattern for specifying scopes in which logging is enabled. " +
                   "See the Dump option for the pattern syntax.", type = OptionType.Debug)
    public static final OptionKey<String> Verify = new OptionKey<>(null);
    @Option(help = "file:doc-files/DumpHelp.txt", type = OptionType.Debug)
    public static final OptionKey<String> Dump = new OptionKey<>(null);
    @Option(help = "Pattern for specifying scopes in which logging is enabled. " +
                   "See the Dump option for the pattern syntax.", type = OptionType.Debug)
    public static final OptionKey<String> Log = new OptionKey<>(null);
    @Option(help = "file:doc-files/MethodFilterHelp.txt")
    public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
    @Option(help = "Only check MethodFilter against the root method in the context if true, otherwise check all methods", type = OptionType.Debug)
    public static final OptionKey<Boolean> MethodFilterRootOnly = new OptionKey<>(false);
    @Option(help = "Dump a before and after graph if the named phase changes the graph.%n" +
                   "The argument is substring matched against the simple name of the phase class", type = OptionType.Debug)
    public static final OptionKey<String> DumpOnPhaseChange = new OptionKey<>(null);

    @Option(help = "Lists on the console at VM shutdown the metric names available to the Timers, Counters and MemUseTrackers options. " +
                   "Note that this only lists the metrics that were initialized during the VM execution and so " +
                   "will not include metrics for compiler code that is not executed.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ListMetrics = new OptionKey<>(false);
    @Option(help = "file:doc-files/MetricsFileHelp.txt", type = OptionType.Debug)
     public static final OptionKey<String> MetricsFile = new OptionKey<>(null);
    @Option(help = "File to which aggregated metrics are dumped at shutdown. A CSV format is used if the file ends with .csv " +
                    "otherwise a more human readable format is used. If not specified, metrics are dumped to the console.", type = OptionType.Debug)
    public static final OptionKey<String> AggregatedMetricsFile = new OptionKey<>(null);

    @Option(help = "Only report metrics for threads whose name matches the regular expression.", type = OptionType.Debug)
    public static final OptionKey<String> MetricsThreadFilter = new OptionKey<>(null);
    @Option(help = "Enable debug output for stub code generation and snippet preparation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DebugStubsAndSnippets = new OptionKey<>(false);
    @Option(help = "Send Graal compiler IR to dump handlers on error.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DumpOnError = new OptionKey<>(false);
    @Option(help = "Intercept also bailout exceptions", type = OptionType.Debug)
    public static final OptionKey<Boolean> InterceptBailout = new OptionKey<>(false);
    @Option(help = "Enable more verbose log output when available", type = OptionType.Debug)
    public static final OptionKey<Boolean> LogVerbose = new OptionKey<>(false);

    @Option(help = "The directory where various Graal dump files are written.")
    public static final OptionKey<String> DumpPath = new OptionKey<>("dumps");
    @Option(help = "Print the name of each dump file path as it's created.")
    public static final OptionKey<Boolean> ShowDumpFiles = new OptionKey<>(false);

    @Option(help = "Enable dumping to the C1Visualizer. Enabling this option implies PrintBackendCFG.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintCFG = new OptionKey<>(false);
    @Option(help = "Enable dumping LIR, register allocation and code generation info to the C1Visualizer.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintBackendCFG = new OptionKey<>(true);

    @Option(help = "Output probabilities for fixed nodes during binary graph dumping.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraphProbabilities = new OptionKey<>(false);
    @Option(help = "Enable dumping to the IdealGraphVisualizer.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraph = new OptionKey<>(true);
    @Option(help = "Print graphs to files instead of sending them over the network.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraphFile = new OptionKey<>(false);

    @Option(help = "Host part of the address to which graphs are dumped.", type = OptionType.Debug)
    public static final OptionKey<String> PrintGraphHost = new OptionKey<>("127.0.0.1");
    @Option(help = "Port part of the address to which graphs are dumped in binary format.", type = OptionType.Debug)
    public static final OptionKey<Integer> PrintBinaryGraphPort = new OptionKey<>(4445);
    @Option(help = "Schedule graphs as they are dumped.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintGraphWithSchedule = new OptionKey<>(false);

    @Option(help = "Enable dumping Truffle ASTs to the IdealGraphVisualizer.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintTruffleTrees = new OptionKey<>(true);

    @Option(help = "Treat any exceptions during dumping as fatal.", type = OptionType.Debug)
    public static final OptionKey<Boolean> DumpingErrorsAreFatal = new OptionKey<>(false);

    @Option(help = "Enable dumping canonical text from for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintCanonicalGraphStrings = new OptionKey<>(false);
    @Option(help = "Choose format used when dumping canonical text for graphs: " +
            "0 gives a scheduled graph (better for spotting changes involving the schedule) " +
            "while 1 gives a CFG containing expressions rooted at fixed nodes (better for spotting small structure differences)", type = OptionType.Debug)
    public static final OptionKey<Integer> PrintCanonicalGraphStringFlavor = new OptionKey<>(0);
    @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsExcludeVirtuals = new OptionKey<>(true);
    @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsCheckConstants = new OptionKey<>(false);
    @Option(help = "Attempts to remove object identity hashes when dumping canonical text for graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanonicalGraphStringsRemoveIdentities = new OptionKey<>(true);

    @Option(help = "Clear the debug metrics after bootstrap.", type = OptionType.Debug)
    public static final OptionKey<Boolean> ClearMetricsAfterBootstrap = new OptionKey<>(false);
    @Option(help = "Do not compile anything on bootstrap but just initialize the compiler.", type = OptionType.Debug)
    public static final OptionKey<Boolean> BootstrapInitializeOnly = new OptionKey<>(false);

    /**
     * Gets the directory in which {@link DebugDumpHandler}s can generate output. This will be the
     * directory specified by {@link #DumpPath} if it has been set otherwise it will be derived from
     * the default value of {@link #DumpPath} and {@link GraalServices#getGlobalTimeStamp()}.
     *
     * This method will ensure the returned directory exists, printing a message to {@link TTY} if
     * it creates it.
     *
     * @return a path as described above whose directories are guaranteed to exist
     * @throws IOException if there was an error in {@link Files#createDirectories}
     */
    public static Path getDumpDirectory(OptionValues options) throws IOException {
        Path dumpDir;
        if (DumpPath.hasBeenSet(options)) {
            dumpDir = Paths.get(DumpPath.getValue(options));
        } else {
            dumpDir = Paths.get(DumpPath.getValue(options), String.valueOf(GraalServices.getGlobalTimeStamp()));
        }
        dumpDir = dumpDir.toAbsolutePath();
        if (!Files.exists(dumpDir)) {
            synchronized (DebugConfigImpl.class) {
                if (!Files.exists(dumpDir)) {
                    Files.createDirectories(dumpDir);
                    if (ShowDumpFiles.getValue(options)) {
                        TTY.println("Dumping debug output in %s", dumpDir.toString());
                    }
                }
            }
        }
        return dumpDir;
    }
}
