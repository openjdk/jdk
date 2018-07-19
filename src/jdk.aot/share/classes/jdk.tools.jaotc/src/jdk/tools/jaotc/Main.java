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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.hotspot.meta.HotSpotAOTProfilingPlugin.Options.TieredAOT;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.Options.Option;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompilerFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime.HotSpotGC;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.meta.HotSpotInvokeDynamicPlugin;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

public final class Main {

    final Options options = new Options();
    private PrintWriter log;
    LogPrinter printer;
    GraalFilters filters;

    private static final int EXIT_OK = 0;        // No errors.
    private static final int EXIT_CMDERR = 2;    // Bad command-line arguments and/or switches.
    private static final int EXIT_ABNORMAL = 4;  // Terminated abnormally.

    private static final String PROGNAME = "jaotc";

    private static final String JVM_VERSION = System.getProperty("java.runtime.version");

    public static void main(String[] args) throws Exception {
        Main t = new Main();
        final int exitCode = t.run(args);
        System.exit(exitCode);
    }

    private int run(String[] args) {
        log = new PrintWriter(System.out);
        printer = new LogPrinter(this, log);

        try {
            Options.handleOptions(this, args);
            if (options.help) {
                showHelp();
                return EXIT_OK;
            }
            if (options.version) {
                showVersion();
                return EXIT_OK;
            }

            printer.printlnInfo("Compiling " + options.outputName + "...");
            final long start = System.currentTimeMillis();
            if (!run()) {
                return EXIT_ABNORMAL;
            }
            final long end = System.currentTimeMillis();
            printer.printlnInfo("Total time: " + (end - start) + " ms");

            return EXIT_OK;
        } catch (Options.BadArgs e) {
            printer.reportError(e.key, e.args);
            if (e.showUsage) {
                showUsage();
            }
            return EXIT_CMDERR;
        } catch (Exception e) {
            e.printStackTrace();
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    @SuppressWarnings("try")
    private boolean run() throws Exception {
        LogPrinter.openLog();

        try {

            final Linker linker = new Linker(this);
            final String objectFileName = linker.objFile();
            final Collector collector = new Collector(this);
            Set<Class<?>> classesToCompile;

            try (Timer t = new Timer(this, "")) {
                classesToCompile = collector.collectClassesToCompile();
                printer.printInfo(classesToCompile.size() + " classes found");
            }

            OptionValues graalOptions = HotSpotGraalOptionValues.HOTSPOT_OPTIONS;
            // Setting -Dgraal.TieredAOT overrides --compile-for-tiered
            if (!TieredAOT.hasBeenSet(graalOptions)) {
                graalOptions = new OptionValues(graalOptions, TieredAOT, options.tiered);
            }
            graalOptions = new OptionValues(graalOptions, GeneratePIC, true, ImmutableCode, true);
            GraalJVMCICompiler graalCompiler = HotSpotGraalCompilerFactory.createCompiler("JAOTC", JVMCI.getRuntime(), graalOptions, CompilerConfigurationFactory.selectFactory(null, graalOptions));
            HotSpotGraalRuntime runtime = (HotSpotGraalRuntime) graalCompiler.getGraalRuntime();
            HotSpotHostBackend backend = (HotSpotHostBackend) runtime.getCapability(RuntimeProvider.class).getHostBackend();
            MetaAccessProvider metaAccess = backend.getProviders().getMetaAccess();
            filters = new GraalFilters(metaAccess);

            List<AOTCompiledClass> classes;

            try (Timer t = new Timer(this, "")) {
                classes = collector.collectMethodsToCompile(classesToCompile, metaAccess);
            }

            // Free memory!
            try (Timer t = options.verbose ? new Timer(this, "Freeing memory") : null) {
                printer.printMemoryUsage();
                classesToCompile = null;
                System.gc();
            }

            AOTDynamicTypeStore dynoStore = new AOTDynamicTypeStore();
            AOTCompiledClass.setDynamicTypeStore(dynoStore);

            AOTBackend aotBackend = new AOTBackend(this, graalOptions, backend, new HotSpotInvokeDynamicPlugin(dynoStore));
            SnippetReflectionProvider snippetReflection = aotBackend.getProviders().getSnippetReflection();
            AOTCompiler compiler = new AOTCompiler(this, graalOptions, aotBackend, options.threads);
            classes = compiler.compileClasses(classes);

            GraalHotSpotVMConfig graalHotSpotVMConfig = runtime.getVMConfig();
            PhaseSuite<HighTierContext> graphBuilderSuite = aotBackend.getGraphBuilderSuite();
            ListIterator<BasePhase<? super HighTierContext>> iterator = graphBuilderSuite.findPhase(GraphBuilderPhase.class);
            GraphBuilderConfiguration graphBuilderConfig = ((GraphBuilderPhase) iterator.previous()).getGraphBuilderConfig();

            // Free memory!
            try (Timer t = options.verbose ? new Timer(this, "Freeing memory") : null) {
                printer.printMemoryUsage();
                aotBackend = null;
                compiler = null;
                System.gc();
            }

            int gc = runtime.getGarbageCollector().ordinal()+1;
            BinaryContainer binaryContainer = new BinaryContainer(graalOptions, graalHotSpotVMConfig, graphBuilderConfig, gc, JVM_VERSION);
            DataBuilder dataBuilder = new DataBuilder(this, backend, classes, binaryContainer);

            try (DebugContext debug = DebugContext.create(graalOptions, new GraalDebugHandlersFactory(snippetReflection)); Activation a = debug.activate()) {
                dataBuilder.prepareData(debug);
            }

            // Print information about section sizes
            printer.containersInfo(binaryContainer);

            // Free memory!
            try (Timer t = options.verbose ? new Timer(this, "Freeing memory") : null) {
                printer.printMemoryUsage();
                backend = null;
                for (AOTCompiledClass aotCompClass : classes) {
                    aotCompClass.clear();
                }
                classes.clear();
                classes = null;
                dataBuilder = null;
                binaryContainer.freeMemory();
                System.gc();
            }

            try (Timer t = new Timer(this, "Creating binary: " + objectFileName)) {
                binaryContainer.createBinary(objectFileName);
            }

            // Free memory!
            try (Timer t = options.verbose ? new Timer(this, "Freeing memory") : null) {
                printer.printMemoryUsage();
                binaryContainer = null;
                System.gc();
            }

            try (Timer t = new Timer(this, "Creating shared library: " + linker.libFile())) {
                linker.link();
            }

            printer.printVerbose("Final memory  ");
            printer.printMemoryUsage();
            printer.printlnVerbose("");

        } finally {
            LogPrinter.closeLog();
        }
        return true;
    }

    void handleError(ResolvedJavaMethod resolvedMethod, Throwable e, String message) {
        String methodName = JavaMethodInfo.uniqueMethodName(resolvedMethod);

        if (options.debug) {
            printer.printError("Failed compilation: " + methodName + ": " + e);
        }

        // Ignore some exceptions when meta-compiling Graal.
        if (GraalFilters.shouldIgnoreException(e)) {
            return;
        }

        LogPrinter.writeLog("Failed compilation of method " + methodName + message);

        if (!options.debug) {
            printer.printError("Failed compilation: " + methodName + ": " + e);
        }

        if (options.verbose) {
            e.printStackTrace(log);
        }

        if (options.exitOnError) {
            System.exit(1);
        }
    }

    void warning(String key, Object... args) {
        log.println("Warning: " + MessageFormat.format(key, args));
        log.flush();
    }

    private void showUsage() {
        log.println("Usage: " + PROGNAME + " <options> list");
        log.println("use --help for a list of possible options");
        log.flush();
    }

    private void showHelp() {
        log.println("Usage: " + PROGNAME + " <options> list");
        log.println();
        log.println("  list       A : separated list of class names, modules, jar files");
        log.println("             or directories which contain class files.");
        log.println();
        log.println("where options include:");
        for (Option o : Options.recognizedOptions) {
            String name = o.aliases[0].substring(1); // there must always be at least one name
            name = name.charAt(0) == '-' ? name.substring(1) : name;
            if (o.isHidden() || name.equals("h")) {
                continue;
            }
            log.println(o.help);
        }
        log.flush();
    }

    private void showVersion() {
        log.println(PROGNAME + " " + JVM_VERSION);
    }
}
