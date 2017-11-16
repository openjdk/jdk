/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.printer;

import static org.graalvm.compiler.debug.DebugOptions.PrintCFG;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DisassemblerProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo;
import org.graalvm.compiler.lir.debug.IntervalDumper;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Observes compilation events and uses {@link CFGPrinter} to produce a control flow graph for the
 * <a href="http://java.net/projects/c1visualizer/">C1 Visualizer</a>.
 */
public class CFGPrinterObserver implements DebugDumpHandler {

    private CFGPrinter cfgPrinter;
    private File cfgFile;
    private JavaMethod curMethod;
    private CompilationIdentifier curCompilation;
    private List<String> curDecorators = Collections.emptyList();

    @Override
    public void dump(DebugContext debug, Object object, String format, Object... arguments) {
        String message = String.format(format, arguments);
        try {
            dumpSandboxed(debug, object, message);
        } catch (Throwable ex) {
            TTY.println("CFGPrinter: Exception during output of " + message + ": " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Looks for the outer most method and its {@link DebugDumpScope#decorator}s in the current
     * debug scope and opens a new compilation scope if this pair does not match the current method
     * and decorator pair.
     */
    private boolean checkMethodScope(DebugContext debug) {
        JavaMethod method = null;
        CompilationIdentifier compilation = null;
        ArrayList<String> decorators = new ArrayList<>();
        for (Object o : debug.context()) {
            if (o instanceof JavaMethod) {
                method = (JavaMethod) o;
                decorators.clear();
            } else if (o instanceof StructuredGraph) {
                StructuredGraph graph = (StructuredGraph) o;
                if (graph.method() != null) {
                    method = graph.method();
                    decorators.clear();
                    compilation = graph.compilationId();
                }
            } else if (o instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                if (debugDumpScope.decorator) {
                    decorators.add(debugDumpScope.name);
                }
            } else if (o instanceof CompilationResult) {
                CompilationResult compilationResult = (CompilationResult) o;
                compilation = compilationResult.getCompilationId();
            }
        }

        if (method == null && compilation == null) {
            return false;
        }

        if (compilation != null) {
            if (!compilation.equals(curCompilation) || !curDecorators.equals(decorators)) {
                cfgPrinter.printCompilation(compilation);
            }
        } else {
            if (!method.equals(curMethod) || !curDecorators.equals(decorators)) {
                cfgPrinter.printCompilation(method);
            }
        }
        curCompilation = compilation;
        curMethod = method;
        curDecorators = decorators;
        return true;
    }

    private static boolean isFrontendObject(Object object) {
        return object instanceof Graph || object instanceof BciBlockMapping;
    }

    private LIR lastLIR = null;
    private IntervalDumper delayedIntervals = null;

    public void dumpSandboxed(DebugContext debug, Object object, String message) {
        OptionValues options = debug.getOptions();
        boolean dumpFrontend = PrintCFG.getValue(options);
        if (!dumpFrontend && isFrontendObject(object)) {
            return;
        }

        if (cfgPrinter == null) {
            try {
                Path dumpFile = debug.getDumpPath(".cfg", false);
                cfgFile = dumpFile.toFile();
                OutputStream out = new BufferedOutputStream(new FileOutputStream(cfgFile));
                cfgPrinter = new CFGPrinter(out);
            } catch (IOException e) {
                throw (GraalError) new GraalError("Could not open %s", cfgFile == null ? "[null]" : cfgFile.getAbsolutePath()).initCause(e);
            }
        }

        if (!checkMethodScope(debug)) {
            return;
        }
        try {
            if (curMethod instanceof ResolvedJavaMethod) {
                cfgPrinter.method = (ResolvedJavaMethod) curMethod;
            }

            if (object instanceof LIR) {
                cfgPrinter.lir = (LIR) object;
            } else {
                cfgPrinter.lir = debug.contextLookup(LIR.class);
            }
            cfgPrinter.nodeLirGenerator = debug.contextLookup(NodeLIRBuilder.class);
            cfgPrinter.livenessInfo = debug.contextLookup(GlobalLivenessInfo.class);
            cfgPrinter.res = debug.contextLookup(LIRGenerationResult.class);
            if (cfgPrinter.nodeLirGenerator != null) {
                cfgPrinter.target = cfgPrinter.nodeLirGenerator.getLIRGeneratorTool().target();
            }
            if (cfgPrinter.lir != null && cfgPrinter.lir.getControlFlowGraph() instanceof ControlFlowGraph) {
                cfgPrinter.cfg = (ControlFlowGraph) cfgPrinter.lir.getControlFlowGraph();
            }

            CodeCacheProvider codeCache = debug.contextLookup(CodeCacheProvider.class);
            if (codeCache != null) {
                cfgPrinter.target = codeCache.getTarget();
            }

            if (object instanceof BciBlockMapping) {
                BciBlockMapping blockMap = (BciBlockMapping) object;
                cfgPrinter.printCFG(message, blockMap);
                if (blockMap.code.getCode() != null) {
                    cfgPrinter.printBytecodes(new BytecodeDisassembler(false).disassemble(blockMap.code));
                }

            } else if (object instanceof LIR) {
                // Currently no node printing for lir
                cfgPrinter.printCFG(message, cfgPrinter.lir.codeEmittingOrder(), false);
                lastLIR = (LIR) object;
                if (delayedIntervals != null) {
                    cfgPrinter.printIntervals(message, delayedIntervals);
                    delayedIntervals = null;
                }
            } else if (object instanceof ScheduleResult) {
                cfgPrinter.printSchedule(message, (ScheduleResult) object);
            } else if (object instanceof StructuredGraph) {
                if (cfgPrinter.cfg == null) {
                    StructuredGraph graph = (StructuredGraph) object;
                    cfgPrinter.cfg = ControlFlowGraph.compute(graph, true, true, true, false);
                    cfgPrinter.printCFG(message, cfgPrinter.cfg.getBlocks(), true);
                } else {
                    cfgPrinter.printCFG(message, cfgPrinter.cfg.getBlocks(), true);
                }

            } else if (object instanceof CompilationResult) {
                final CompilationResult compResult = (CompilationResult) object;
                cfgPrinter.printMachineCode(disassemble(codeCache, compResult, null), message);
            } else if (object instanceof InstalledCode) {
                CompilationResult compResult = debug.contextLookup(CompilationResult.class);
                if (compResult != null) {
                    cfgPrinter.printMachineCode(disassemble(codeCache, compResult, (InstalledCode) object), message);
                }
            } else if (object instanceof IntervalDumper) {
                if (lastLIR == cfgPrinter.lir) {
                    cfgPrinter.printIntervals(message, (IntervalDumper) object);
                } else {
                    if (delayedIntervals != null) {
                        debug.log("Some delayed intervals were dropped (%s)", delayedIntervals);
                    }
                    delayedIntervals = (IntervalDumper) object;
                }
            } else if (object instanceof AbstractBlockBase<?>[]) {
                cfgPrinter.printCFG(message, (AbstractBlockBase<?>[]) object, false);
            } else if (object instanceof Trace) {
                cfgPrinter.printCFG(message, ((Trace) object).getBlocks(), false);
            } else if (object instanceof TraceBuilderResult) {
                cfgPrinter.printTraces(message, (TraceBuilderResult) object);
            }
        } finally {
            cfgPrinter.target = null;
            cfgPrinter.lir = null;
            cfgPrinter.res = null;
            cfgPrinter.nodeLirGenerator = null;
            cfgPrinter.livenessInfo = null;
            cfgPrinter.cfg = null;
            cfgPrinter.flush();
        }
    }

    /** Lazy initialization to delay service lookup until disassembler is actually needed. */
    static class DisassemblerHolder {
        private static final DisassemblerProvider disassembler;

        static {
            DisassemblerProvider selected = null;
            for (DisassemblerProvider d : GraalServices.load(DisassemblerProvider.class)) {
                String name = d.getName().toLowerCase();
                if (name.contains("hcf") || name.contains("hexcodefile")) {
                    selected = d;
                    break;
                }
            }
            if (selected == null) {
                selected = new DisassemblerProvider() {
                    @Override
                    public String getName() {
                        return "nop";
                    }
                };
            }
            disassembler = selected;
        }
    }

    private static String disassemble(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        DisassemblerProvider dis = DisassemblerHolder.disassembler;
        if (installedCode != null) {
            return dis.disassembleInstalledCode(codeCache, compResult, installedCode);
        }
        return dis.disassembleCompiledCode(codeCache, compResult);
    }

    @Override
    public void close() {
        if (cfgPrinter != null) {
            cfgPrinter.close();
            cfgPrinter = null;
            curDecorators = Collections.emptyList();
            curMethod = null;
            curCompilation = null;
        }
    }

    public String getDumpPath() {
        if (cfgFile != null) {
            return cfgFile.getAbsolutePath();
        }
        return null;
    }
}
