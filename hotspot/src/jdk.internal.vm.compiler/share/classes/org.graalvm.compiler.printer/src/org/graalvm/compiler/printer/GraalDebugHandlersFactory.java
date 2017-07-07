/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.DebugOptions.PrintGraphHost;
import static org.graalvm.compiler.debug.DebugOptions.PrintXmlGraphPort;
import static org.graalvm.compiler.debug.DebugOptions.ShowDumpFiles;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugHandler;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.UniquePathUtilities;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
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
        if (DebugOptions.PrintGraphFile.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((graph) -> createFilePrinter(graph, options, snippetReflection)));
        } else {
            handlers.add(new GraphPrinterDumpHandler((graph) -> createNetworkPrinter(graph, options, snippetReflection)));
        }
        if (DebugOptions.PrintCanonicalGraphStrings.getValue(options)) {
            handlers.add(new GraphPrinterDumpHandler((graph) -> createStringPrinter(snippetReflection)));
        }
        handlers.add(new NodeDumper());
        if (DebugOptions.PrintCFG.getValue(options) || DebugOptions.PrintBackendCFG.getValue(options)) {
            if (DebugOptions.PrintBinaryGraphs.getValue(options) && DebugOptions.PrintCFG.getValue(options)) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -Dgraal.PrintCFG=false to disable it");
            }
            handlers.add(new CFGPrinterObserver());
        }
        handlers.add(new NoDeadCodeVerifyHandler());
        return handlers;
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(DebugContext debug, Object object, String format, Object... arguments) {
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

    private static CanonicalStringGraphPrinter createStringPrinter(SnippetReflectionProvider snippetReflection) {
        return new CanonicalStringGraphPrinter(snippetReflection);
    }

    public static String sanitizedFileName(String name) {
        try {
            Paths.get(name);
            return name;
        } catch (InvalidPathException e) {
            // fall through
        }
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

    private static GraphPrinter createNetworkPrinter(Graph graph, OptionValues options, SnippetReflectionProvider snippetReflection) throws IOException {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphs.getValue(options) ? PrintBinaryGraphPort.getValue(options) : PrintXmlGraphPort.getValue(options);
        try {
            GraphPrinter printer;
            if (DebugOptions.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(SocketChannel.open(new InetSocketAddress(host, port)), snippetReflection);
            } else {
                printer = new IdealGraphPrinter(new Socket(host, port).getOutputStream(), true, snippetReflection);
            }
            TTY.println("Connected to the IGV on %s:%d", host, port);
            return printer;
        } catch (ClosedByInterruptException | InterruptedIOException e) {
            /*
             * Interrupts should not count as errors because they may be caused by a cancelled Graal
             * compilation. ClosedByInterruptException occurs if the SocketChannel could not be
             * opened. InterruptedIOException occurs if new Socket(..) was interrupted.
             */
            return null;
        } catch (IOException e) {
            if (!DebugOptions.PrintGraphFile.hasBeenSet(options)) {
                return createFilePrinter(graph, options, snippetReflection);
            } else {
                throw new IOException(String.format("Could not connect to the IGV on %s:%d", host, port), e);
            }
        }
    }

    private static final AtomicInteger unknownCompilationId = new AtomicInteger();

    /**
     * Creates a new file or directory for dumping based on a given graph and a file extension.
     *
     * @param graph a base path name is derived from {@code graph}
     * @param extension a suffix which if non-null and non-empty added to the end of the returned
     *            path separated by a {@code "."}
     * @param createDirectory specifies if this is a request to create a directory instead of a file
     * @return the created directory or file
     * @throws IOException if there was an error creating the directory or file
     */
    static Path createDumpPath(OptionValues options, Graph graph, String extension, boolean createDirectory) throws IOException {
        CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        String id = null;
        String label = null;
        if (graph instanceof StructuredGraph) {
            StructuredGraph sgraph = (StructuredGraph) graph;
            label = getGraphName(sgraph);
            compilationId = sgraph.compilationId();
            if (compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
                id = graph.getClass().getSimpleName() + "-" + sgraph.graphId();
            } else {
                id = compilationId.toString(CompilationIdentifier.Verbosity.ID);
            }
        } else {
            label = graph == null ? null : graph.name != null ? graph.name : graph.toString();
            id = "UnknownCompilation-" + unknownCompilationId.incrementAndGet();
        }
        String ext = UniquePathUtilities.formatExtension(extension);
        Path result = createUnique(DebugOptions.getDumpDirectory(options), id, label, ext, createDirectory);
        if (ShowDumpFiles.getValue(options)) {
            TTY.println("Dumping debug output to %s", result.toAbsolutePath().toString());
        }
        return result;
    }

    /**
     * A maximum file name length supported by most file systems. There is no platform independent
     * way to get this in Java.
     */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private static final String ELLIPSIS = "...";

    private static Path createUnique(Path dumpDir, String id, String label, String ext, boolean createDirectory) throws IOException {
        String timestamp = "";
        for (;;) {
            int fileNameLengthWithoutLabel = timestamp.length() + ext.length() + id.length() + "[]".length();
            int labelLengthLimit = MAX_FILE_NAME_LENGTH - fileNameLengthWithoutLabel;
            String fileName;
            if (labelLengthLimit < ELLIPSIS.length()) {
                // This means `id` is very long
                String suffix = timestamp + ext;
                int idLengthLimit = Math.min(MAX_FILE_NAME_LENGTH - suffix.length(), id.length());
                fileName = id.substring(0, idLengthLimit) + suffix;
            } else {
                if (label == null) {
                    fileName = sanitizedFileName(id + timestamp + ext);
                } else {
                    String adjustedLabel = label;
                    if (label.length() > labelLengthLimit) {
                        adjustedLabel = label.substring(0, labelLengthLimit - ELLIPSIS.length()) + ELLIPSIS;
                    }
                    fileName = sanitizedFileName(id + '[' + adjustedLabel + ']' + timestamp + ext);
                }
            }
            Path result = dumpDir.resolve(fileName);
            try {
                if (createDirectory) {
                    return Files.createDirectory(result);
                } else {
                    return Files.createFile(result);
                }
            } catch (FileAlreadyExistsException e) {
                timestamp = "_" + Long.toString(System.currentTimeMillis());
            }
        }
    }

    private static String getGraphName(StructuredGraph graph) {
        if (graph.name != null) {
            return graph.name;
        } else if (graph.method() != null) {
            return graph.method().format("%h.%n(%p)").replace(" ", "");
        } else {
            return graph.toString();
        }
    }

    private static GraphPrinter createFilePrinter(Graph graph, OptionValues options, SnippetReflectionProvider snippetReflection) throws IOException {
        Path path = createDumpPath(options, graph, PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml", false);
        try {
            GraphPrinter printer;
            if (DebugOptions.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(FileChannel.open(path, StandardOpenOption.WRITE), snippetReflection);
            } else {
                printer = new IdealGraphPrinter(Files.newOutputStream(path), true, snippetReflection);
            }
            return printer;
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }
}
