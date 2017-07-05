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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugConfigCustomizer;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.UniquePathUtilities;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugConfigCustomizer.class)
public class GraalDebugConfigCustomizer implements DebugConfigCustomizer {

    private SnippetReflectionProvider snippetReflection;

    @Override
    public void customize(DebugConfig config, Object... extraArgs) {
        snippetReflection = DebugConfigCustomizer.lookupArg(SnippetReflectionProvider.class, extraArgs);
        if (Options.PrintIdealGraphFile.getValue()) {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(this::createFilePrinter));
        } else {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(this::createNetworkPrinter));
        }
        if (Options.PrintCanonicalGraphStrings.getValue()) {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(this::createStringPrinter));
        }
        config.dumpHandlers().add(new NodeDumper());
        if (Options.PrintCFG.getValue() || Options.PrintBackendCFG.getValue()) {
            if (Options.PrintBinaryGraphs.getValue() && Options.PrintCFG.getValue()) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -Dgraal.PrintCFG=false to disable it");
            }
            config.dumpHandlers().add(new CFGPrinterObserver(Options.PrintCFG.getValue()));
        }
        config.verifyHandlers().add(new NoDeadCodeVerifyHandler());
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(Object object, String message) {
            if (object instanceof Node) {
                String location = GraphUtil.approxSourceLocation((Node) object);
                String node = ((Node) object).toString(Verbosity.Debugger);
                if (location != null) {
                    Debug.log("Context obj %s (approx. location: %s)", node, location);
                } else {
                    Debug.log("Context obj %s", node);
                }
            }
        }

        @Override
        public void close() {
        }
    }

    private CanonicalStringGraphPrinter createStringPrinter() {
        // Construct the path to the directory.
        Path path = UniquePathUtilities.getPath(Options.PrintCanonicalGraphStringsDirectory, Options.DumpPath, "");
        return new CanonicalStringGraphPrinter(path, snippetReflection);
    }

    private GraphPrinter createNetworkPrinter() throws IOException {
        String host = Options.PrintIdealGraphAddress.getValue();
        int port = Options.PrintBinaryGraphs.getValue() ? Options.PrintBinaryGraphPort.getValue() : Options.PrintIdealGraphPort.getValue();
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue()) {
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
            throw new IOException(String.format("Could not connect to the IGV on %s:%d", host, port), e);
        }
    }

    private static Path getFilePrinterPath() {
        // Construct the path to the file.
        return UniquePathUtilities.getPath(Options.PrintIdealGraphFileName, Options.DumpPath, Options.PrintBinaryGraphs.getValue() ? "bgv" : "gv.xml");
    }

    private GraphPrinter createFilePrinter() throws IOException {
        Path path = getFilePrinterPath();
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue()) {
                printer = new BinaryGraphPrinter(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW), snippetReflection);
            } else {
                printer = new IdealGraphPrinter(Files.newOutputStream(path), true, snippetReflection);
            }
            TTY.println("Dumping IGV graphs to %s", path.toString());
            return printer;
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }
}
