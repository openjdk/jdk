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

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DumpPath;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintCanonicalGraphStringsDirectory;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphHost;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphFileName;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintXmlGraphPort;

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

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugConfigCustomizer;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.UniquePathUtilities;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugConfigCustomizer.class)
public class GraalDebugConfigCustomizer implements DebugConfigCustomizer {

    @Override
    public void customize(DebugConfig config) {
        OptionValues options = config.getOptions();
        if (Options.PrintGraphFile.getValue(options)) {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(() -> createFilePrinter(options)));
        } else {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(() -> createNetworkPrinter(options)));
        }
        if (Options.PrintCanonicalGraphStrings.getValue(options)) {
            config.dumpHandlers().add(new GraphPrinterDumpHandler(() -> createStringPrinter(options)));
        }
        config.dumpHandlers().add(new NodeDumper());
        if (Options.PrintCFG.getValue(options) || Options.PrintBackendCFG.getValue(options)) {
            if (Options.PrintBinaryGraphs.getValue(options) && Options.PrintCFG.getValue(options)) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -Dgraal.PrintCFG=false to disable it");
            }
            config.dumpHandlers().add(new CFGPrinterObserver(Options.PrintCFG.getValue(options)));
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

        @Override
        public void addCapability(Object capability) {
        }
    }

    private static CanonicalStringGraphPrinter createStringPrinter(OptionValues options) {
        // Construct the path to the directory.
        Path path = UniquePathUtilities.getPath(options, PrintCanonicalGraphStringsDirectory, Options.DumpPath, "");
        return new CanonicalStringGraphPrinter(path);
    }

    private static GraphPrinter createNetworkPrinter(OptionValues options) throws IOException {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphs.getValue(options) ? PrintBinaryGraphPort.getValue(options) : PrintXmlGraphPort.getValue(options);
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(SocketChannel.open(new InetSocketAddress(host, port)));
            } else {
                printer = new IdealGraphPrinter(new Socket(host, port).getOutputStream(), true);
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
            if (!Options.PrintGraphFile.hasBeenSet(options)) {
                TTY.println(String.format("Could not connect to the IGV on %s:%d - falling back to file dumping...", host, port));
                return createFilePrinter(options);
            } else {
                throw new IOException(String.format("Could not connect to the IGV on %s:%d", host, port), e);
            }
        }
    }

    private static Path getFilePrinterPath(OptionValues options) {
        // Construct the path to the file.
        return UniquePathUtilities.getPath(options, PrintGraphFileName, DumpPath, PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml");
    }

    private static GraphPrinter createFilePrinter(OptionValues options) throws IOException {
        Path path = getFilePrinterPath(options);
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW));
            } else {
                printer = new IdealGraphPrinter(Files.newOutputStream(path), true);
            }
            TTY.println("Dumping IGV graphs to %s", path.toAbsolutePath().toString());
            return printer;
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }
}
