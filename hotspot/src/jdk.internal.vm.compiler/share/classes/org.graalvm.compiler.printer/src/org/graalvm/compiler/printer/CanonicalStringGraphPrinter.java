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

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.CanonicalGraphStringsCheckConstants;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.CanonicalGraphStringsExcludeVirtuals;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.CanonicalGraphStringsRemoveIdentities;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintCanonicalGraphStringFlavor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CanonicalStringGraphPrinter implements GraphPrinter {
    private static final Pattern IDENTITY_PATTERN = Pattern.compile("([A-Za-z0-9$_]+)@[0-9a-f]+");
    private Path currentDirectory;
    private Path root;
    private final SnippetReflectionProvider snippetReflection;

    public CanonicalStringGraphPrinter(Path directory, SnippetReflectionProvider snippetReflection) {
        this.currentDirectory = directory;
        this.root = directory;
        this.snippetReflection = snippetReflection;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflection;
    }

    protected static String escapeFileName(String name) {
        byte[] bytes = name.getBytes();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '.' || b == '-' || b == '_') {
                sb.append((char) b);
            } else {
                sb.append('%');
                sb.append(Integer.toHexString(b));
            }
        }
        return sb.toString();
    }

    private static String removeIdentities(String str) {
        return IDENTITY_PATTERN.matcher(str).replaceAll("$1");
    }

    protected static void writeCanonicalGraphExpressionString(ValueNode node, boolean checkConstants, boolean removeIdentities, PrintWriter writer) {
        writer.print(node.getClass().getSimpleName());
        writer.print("(");
        Fields properties = node.getNodeClass().getData();
        for (int i = 0; i < properties.getCount(); i++) {
            String dataStr = String.valueOf(properties.get(node, i));
            if (removeIdentities) {
                dataStr = removeIdentities(dataStr);
            }
            writer.print(dataStr);
            if (i + 1 < properties.getCount() || node.inputPositions().iterator().hasNext()) {
                writer.print(", ");
            }
        }
        Iterator<Position> iterator = node.inputPositions().iterator();
        while (iterator.hasNext()) {
            Position position = iterator.next();
            Node input = position.get(node);
            if (checkConstants && input instanceof ConstantNode) {
                ConstantNode constantNode = (ConstantNode) input;
                String valueString = constantNode.getValue().toValueString();
                if (removeIdentities) {
                    valueString = removeIdentities(valueString);
                }
                writer.print(valueString);
            } else if (input instanceof ValueNode && !(input instanceof PhiNode) && !(input instanceof FixedNode)) {
                writeCanonicalGraphExpressionString((ValueNode) input, checkConstants, removeIdentities, writer);
            } else if (input == null) {
                writer.print("null");
            } else {
                writer.print(input.getClass().getSimpleName());
            }
            if (iterator.hasNext()) {
                writer.print(", ");
            }
        }
        writer.print(")");
    }

    protected static void writeCanonicalExpressionCFGString(StructuredGraph graph, boolean checkConstants, boolean removeIdentities, PrintWriter writer) {
        ControlFlowGraph controlFlowGraph = ControlFlowGraph.compute(graph, true, true, false, false);
        for (Block block : controlFlowGraph.getBlocks()) {
            writer.print("Block ");
            writer.print(block);
            writer.print(" ");
            if (block == controlFlowGraph.getStartBlock()) {
                writer.print("* ");
            }
            writer.print("-> ");
            for (Block successor : block.getSuccessors()) {
                writer.print(successor);
                writer.print(" ");
            }
            writer.println();
            FixedNode node = block.getBeginNode();
            while (node != null) {
                writeCanonicalGraphExpressionString(node, checkConstants, removeIdentities, writer);
                writer.println();
                if (node instanceof FixedWithNextNode) {
                    node = ((FixedWithNextNode) node).next();
                } else {
                    node = null;
                }
            }
        }
    }

    protected static void writeCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants, PrintWriter writer) {
        SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        StructuredGraph.ScheduleResult scheduleResult = graph.getLastSchedule();

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        List<String> constantsLines = null;
        if (checkConstants) {
            constantsLines = new ArrayList<>();
        }

        for (Block block : scheduleResult.getCFG().getBlocks()) {
            writer.print("Block ");
            writer.print(block);
            writer.print(" ");
            if (block == scheduleResult.getCFG().getStartBlock()) {
                writer.print("* ");
            }
            writer.print("-> ");
            for (Block successor : block.getSuccessors()) {
                writer.print(successor);
                writer.print(" ");
            }
            writer.println();
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode)) {
                        if (node instanceof ConstantNode) {
                            if (constantsLines != null) {
                                String name = node.toString(Verbosity.Name);
                                String str = name + (excludeVirtual ? "" : "    (" + filteredUsageCount(node) + ")");
                                constantsLines.add(str);
                            }
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            writer.print("  ");
                            writer.print(id);
                            writer.print("|");
                            writer.print(name);
                            if (!excludeVirtual) {
                                writer.print("    (");
                                writer.print(filteredUsageCount(node));
                                writer.print(")");
                            }
                            writer.println();
                        }
                    }
                }
            }
        }
        if (constantsLines != null) {
            writer.print(constantsLines.size());
            writer.println(" constants:");
            Collections.sort(constantsLines);
            for (String s : constantsLines) {
                writer.println(s);
            }
        }
    }

    public static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writeCanonicalGraphString(graph, excludeVirtual, checkConstants, writer);
        writer.flush();
        return stringWriter.toString();
    }

    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    @Override
    public void beginGroup(String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException {
        currentDirectory = currentDirectory.resolve(escapeFileName(name));
    }

    @Override
    public void print(Graph graph, String title, Map<Object, Object> properties) throws IOException {
        if (graph instanceof StructuredGraph) {
            StructuredGraph structuredGraph = (StructuredGraph) graph;
            currentDirectory.toFile().mkdirs();
            if (this.root != null) {
                TTY.println("Dumping string graphs in %s", this.root);
                this.root = null;
            }
            Path filePath = currentDirectory.resolve(escapeFileName(title));
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath.toFile())))) {
                switch (PrintCanonicalGraphStringFlavor.getValue()) {
                    case 1:
                        writeCanonicalExpressionCFGString(structuredGraph, CanonicalGraphStringsCheckConstants.getValue(), CanonicalGraphStringsRemoveIdentities.getValue(), writer);
                        break;
                    case 0:
                    default:
                        writeCanonicalGraphString(structuredGraph, CanonicalGraphStringsExcludeVirtuals.getValue(), CanonicalGraphStringsCheckConstants.getValue(), writer);
                        break;
                }
            }
        }
    }

    @Override
    public void endGroup() throws IOException {
        currentDirectory = currentDirectory.getParent();
        assert currentDirectory != null;
    }

    @Override
    public void close() {
    }
}
