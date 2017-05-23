/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.debug.GraalDebugConfig.asJavaMethod;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.contract.NodeCostUtil;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

//JaCoCo Exclude

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation
 * that can be inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>
 * .
 */
public class GraphPrinterDumpHandler implements DebugDumpHandler {

    private static final int FAILURE_LIMIT = 8;
    private final GraphPrinterSupplier printerSupplier;
    protected GraphPrinter printer;
    private SnippetReflectionProvider snippetReflection;
    private List<String> previousInlineContext;
    private int[] dumpIds = {};
    private int failuresCount;
    private Map<Graph, List<String>> inlineContextMap;
    private final String jvmArguments;
    private final String sunJavaCommand;

    @FunctionalInterface
    public interface GraphPrinterSupplier {
        GraphPrinter get() throws IOException;
    }

    /**
     * Creates a new {@link GraphPrinterDumpHandler}.
     *
     * @param printerSupplier Supplier used to create the GraphPrinter. Should supply an optional or
     *            null in case of failure.
     */
    public GraphPrinterDumpHandler(GraphPrinterSupplier printerSupplier) {
        this.printerSupplier = printerSupplier;
        /* Add the JVM and Java arguments to the graph properties to help identify it. */
        this.jvmArguments = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        this.sunJavaCommand = System.getProperty("sun.java.command");
    }

    private void ensureInitialized() {
        if (printer == null) {
            if (failuresCount >= FAILURE_LIMIT) {
                return;
            }
            previousInlineContext = new ArrayList<>();
            inlineContextMap = new WeakHashMap<>();
            try {
                printer = printerSupplier.get();
                if (snippetReflection != null) {
                    printer.setSnippetReflectionProvider(snippetReflection);
                }
            } catch (IOException e) {
                handleException(e);
            }
        }
    }

    private int nextDumpId() {
        int depth = previousInlineContext.size();
        if (dumpIds.length < depth) {
            dumpIds = Arrays.copyOf(dumpIds, depth);
        }
        return dumpIds[depth - 1]++;
    }

    @Override
    @SuppressWarnings("try")
    public void dump(Object object, final String message) {
        if (object instanceof Graph && Options.PrintGraph.getValue(DebugScope.getConfig().getOptions())) {
            ensureInitialized();
            if (printer == null) {
                return;
            }
            final Graph graph = (Graph) object;

            // Get all current JavaMethod instances in the context.
            List<String> inlineContext = getInlineContext(graph);

            if (inlineContext != previousInlineContext) {
                Map<Object, Object> properties = new HashMap<>();
                properties.put("graph", graph.toString());
                addCompilationId(properties, graph);
                addCFGFileName(properties);
                if (inlineContext.equals(previousInlineContext)) {
                    /*
                     * two different graphs have the same inline context, so make sure they appear
                     * in different folders by closing and reopening the top scope.
                     */
                    int inlineDepth = previousInlineContext.size() - 1;
                    closeScope(inlineDepth);
                    openScope(inlineContext.get(inlineDepth), inlineDepth, properties);
                } else {
                    // Check for method scopes that must be closed since the previous dump.
                    for (int i = 0; i < previousInlineContext.size(); ++i) {
                        if (i >= inlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                            for (int inlineDepth = previousInlineContext.size() - 1; inlineDepth >= i; --inlineDepth) {
                                closeScope(inlineDepth);
                            }
                            break;
                        }
                    }
                    // Check for method scopes that must be opened since the previous dump.
                    for (int i = 0; i < inlineContext.size(); ++i) {
                        if (i >= previousInlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                            for (int inlineDepth = i; inlineDepth < inlineContext.size(); ++inlineDepth) {
                                openScope(inlineContext.get(inlineDepth), inlineDepth, inlineDepth == inlineContext.size() - 1 ? properties : null);
                            }
                            break;
                        }
                    }
                }
            }

            // Save inline context for next dump.
            previousInlineContext = inlineContext;

            try (Scope s = Debug.sandbox("PrintingGraph", null)) {
                // Finally, output the graph.
                Map<Object, Object> properties = new HashMap<>();
                properties.put("graph", graph.toString());
                properties.put("scope", Debug.currentScope());
                if (graph instanceof StructuredGraph) {
                    properties.put("compilationIdentifier", ((StructuredGraph) graph).compilationId());
                    try {
                        int size = NodeCostUtil.computeGraphSize((StructuredGraph) graph);
                        properties.put("node-cost graph size", size);
                    } catch (Throwable t) {
                        properties.put("node-cost-exception", t.getMessage());
                    }
                }
                addCFGFileName(properties);
                printer.print(graph, nextDumpId() + ":" + message, properties);
            } catch (IOException e) {
                handleException(e);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }

    void handleException(IOException e) {
        if (GraalDebugConfig.Options.DumpingErrorsAreFatal.getValue(DebugScope.getConfig().getOptions())) {
            throw new GraalError(e);
        }
        if (e instanceof ClosedByInterruptException) {
            /*
             * The current dumping was aborted by an interrupt so treat this as a transient failure.
             */
            failuresCount = 0;
        } else {
            failuresCount++;
        }
        printer = null;
        if (failuresCount > FAILURE_LIMIT) {
            e.printStackTrace(TTY.out);
            TTY.println("Too many failures with dumping.  Disabling dump in thread " + Thread.currentThread());
        } else {
            TTY.println(e.getMessage());
        }
    }

    private static void addCompilationId(Map<Object, Object> properties, final Graph graph) {
        if (graph instanceof StructuredGraph) {
            properties.put("compilationId", ((StructuredGraph) graph).compilationId());
        }
    }

    private static void addCFGFileName(Map<Object, Object> properties) {
        DebugConfig config = DebugScope.getConfig();
        if (config != null) {
            for (DebugDumpHandler dumpHandler : config.dumpHandlers()) {
                if (dumpHandler instanceof CFGPrinterObserver) {
                    CFGPrinterObserver cfg = (CFGPrinterObserver) dumpHandler;
                    String path = cfg.getDumpPath();
                    if (path != null) {
                        properties.put("PrintCFGFileName", path);
                    }
                    return;
                }
            }
        }
    }

    private List<String> getInlineContext(Graph graph) {
        List<String> result = inlineContextMap.get(graph);
        if (result == null) {
            result = new ArrayList<>();
            Object lastMethodOrGraph = null;
            boolean graphSeen = false;
            for (Object o : Debug.context()) {
                if (o == graph) {
                    graphSeen = true;
                }

                if (o instanceof DebugDumpScope) {
                    DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                    if (debugDumpScope.decorator && !result.isEmpty()) {
                        result.set(result.size() - 1, debugDumpScope.name + ":" + result.get(result.size() - 1));
                    } else {
                        result.add(debugDumpScope.name);
                    }
                } else {
                    addMethodContext(result, o, lastMethodOrGraph);
                }
                if (o instanceof JavaMethod || o instanceof Graph) {
                    lastMethodOrGraph = o;
                }
            }

            if (result.isEmpty()) {
                result.add(graph.toString());
                graphSeen = true;
            }
            // Reverse list such that inner method comes after outer method.
            Collections.reverse(result);
            if (!graphSeen) {
                /*
                 * The graph isn't in any context but is being processed within another graph so add
                 * it to the end of the scopes.
                 */
                if (asJavaMethod(graph) != null) {
                    addMethodContext(result, graph, lastMethodOrGraph);
                } else {
                    result.add(graph.toString());
                }
            }
            inlineContextMap.put(graph, result);
        }
        return result;
    }

    private static void addMethodContext(List<String> result, Object o, Object lastMethodOrGraph) {
        JavaMethod method = asJavaMethod(o);
        if (method != null) {
            /*
             * Include the current method in the context if there was no previous JavaMethod or
             * JavaMethodContext or if the method is different or if the method is the same but it
             * comes from two different graphs. This ensures that recursive call patterns are
             * handled properly.
             */
            if (lastMethodOrGraph == null || asJavaMethod(lastMethodOrGraph) == null || !asJavaMethod(lastMethodOrGraph).equals(method) ||
                            (lastMethodOrGraph != o && lastMethodOrGraph instanceof Graph && o instanceof Graph)) {
                result.add(method.format("%H.%n(%p)"));
            } else {
                /*
                 * This prevents multiple adjacent method context objects for the same method from
                 * resulting in multiple IGV tree levels. This works on the assumption that real
                 * inlining debug scopes will have a graph context object between the inliner and
                 * inlinee context objects.
                 */
            }
        }
    }

    private void openScope(String name, int inlineDepth, Map<Object, Object> properties) {
        try {
            Map<Object, Object> props = properties;
            if (inlineDepth == 0) {
                /* Include some VM specific properties at the root. */
                if (props == null) {
                    props = new HashMap<>();
                }
                props.put("jvmArguments", jvmArguments);
                if (sunJavaCommand != null) {
                    props.put("sun.java.command", sunJavaCommand);
                }
                props.put("date", new Date().toString());
            }
            printer.beginGroup(name, name, Debug.contextLookup(ResolvedJavaMethod.class), -1, props);
        } catch (IOException e) {
            handleException(e);
        }
    }

    private void closeScope(int inlineDepth) {
        dumpIds[inlineDepth] = 0;
        try {
            printer.endGroup();
        } catch (IOException e) {
            handleException(e);
        }
    }

    @Override
    public void close() {
        if (previousInlineContext != null) {
            for (int inlineDepth = 0; inlineDepth < previousInlineContext.size(); inlineDepth++) {
                closeScope(inlineDepth);
            }
        }
        if (printer != null) {
            printer.close();
            printer = null;
        }
    }

    @Override
    public void addCapability(Object capability) {
        if (capability instanceof SnippetReflectionProvider) {
            snippetReflection = (SnippetReflectionProvider) capability;
            if (printer != null && printer.getSnippetReflectionProvider() == null) {
                printer.setSnippetReflectionProvider(snippetReflection);
            }
        }
    }
}
