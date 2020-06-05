/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugHandler;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugHandlersFactory.class)
public class GraalDebugHandlersFactory implements DebugHandlersFactory {

    private final SnippetReflectionProvider snippetReflection;

    public GraalDebugHandlersFactory() {
        this.snippetReflection = null;
    }

    public GraalDebugHandlersFactory(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public List<DebugHandler> createHandlers(OptionValues options) {
        List<DebugHandler> handlers = new ArrayList<>();
        handlers.add(new GraphPrinterDumpHandler((debug, graph) -> new BinaryGraphPrinter(debug, snippetReflection)));
        if (DebugOptions.PrintCanonicalGraphStrings.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((debug, graph) -> createStringPrinter(snippetReflection)));
        }
        handlers.add(new NodeDumper());
        if (DebugOptions.PrintCFG.getValue(options) || DebugOptions.PrintBackendCFG.getValue(options)) {
            handlers.add(new CFGPrinterObserver());
        }
        handlers.add(new NoDeadCodeVerifyHandler());
        if (DebugOptions.PrintBlockMapping.getValue(options)) {
            handlers.add(new BciBlockMappingDumpHandler());
        }
        return handlers;
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(DebugContext debug, Object object, String format, Object... arguments) {
            if (debug.isLogEnabled()) {
                if (object instanceof Node) {
                    Node node = (Node) object;
                    String location = GraphUtil.approxSourceLocation(node);
                    String nodeName = node.toString(Verbosity.Debugger);
                    if (location != null) {
                        debug.log("Context obj %s (approx. location: %s)", nodeName, location);
                    } else {
                        debug.log("Context obj %s", nodeName);
                    }
                }
            }
        }
    }

    private static CanonicalStringGraphPrinter createStringPrinter(SnippetReflectionProvider snippetReflection) {
        return new CanonicalStringGraphPrinter(snippetReflection);
    }

    @SuppressWarnings("try")
    static ScheduleResult tryGetSchedule(DebugContext debug, StructuredGraph graph) {
        ScheduleResult scheduleResult = graph.getLastSchedule();
        if (scheduleResult == null) {
            // Also provide a schedule when an error occurs
            if (DebugOptions.PrintGraphWithSchedule.getValue(graph.getOptions()) || debug.contextLookup(Throwable.class) != null) {
                try (DebugCloseable noIntercept = debug.disableIntercept()) {
                    SchedulePhase schedule = new SchedulePhase(graph.getOptions());
                    schedule.apply(graph);
                    scheduleResult = graph.getLastSchedule();
                } catch (Throwable t) {
                }
            }
        }
        return scheduleResult;
    }
}
