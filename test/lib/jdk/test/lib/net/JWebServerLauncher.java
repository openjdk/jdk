/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.net;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility to launch the {@code jwebserver}. This does the necessary work of finding the tool
 * location and launching the process and waiting for the server to be ready to serve requests.
 */
public class JWebServerLauncher {

    private static final Path JWEBSERVER_TOOL = Path.of(JDKToolFinder.getJDKTool("jwebserver"));
    private static final String EXPECTED_SERVER_STARTUP_LINE = "URL ";

    /**
     * Represents a launched jwebserver
     *
     * @param process       The jwebserver process
     * @param serverAddr    The address on which the server is listening
     * @param processOutput The server's stdout and stderr output
     */
    public record JWebServerProcess(Process process, InetSocketAddress serverAddr,
                                    List<String> processOutput) {

        /**
         * Throws an {@link AssertionError} if the server's output (stdout and stderr)
         * doesn't contain the passed {@code line}
         *
         * @param line The expected line
         */
        public void assertOutputContainsLine(final String line) {
            if (!processOutput.contains(line)) {
                throw new AssertionError("'" + line + "' missing from" +
                        " stdout/stderr of jwebserver");
            }
        }

        /**
         * Throws an {@link AssertionError} if the server's output (stdout and stderr)
         * doesn't contain any line which starts with the passed {@code lineStart}
         *
         * @param lineStart The expected start of a line
         */
        public void assertOutputHasLineStartingWith(final String lineStart) {
            for (final String line : processOutput) {
                if (line.startsWith(lineStart)) {
                    return;
                }
            }
            throw new AssertionError("No line in jwebserver's stdout/stderr " +
                    "starts with '" + lineStart + "'");
        }
    }

    private static InetSocketAddress parseServerAddr(final String urlText) {
        // the line is of the form:
        // URL http://127.0.0.1:8000/
        if (!urlText.startsWith(EXPECTED_SERVER_STARTUP_LINE) ||
                urlText.length() == EXPECTED_SERVER_STARTUP_LINE.length()) {
            // unexpected line
            System.err.println("Unexpected startup line: " + urlText);
            return null;
        }
        final URI serverURL;
        try {
            serverURL = new URI(urlText.substring(EXPECTED_SERVER_STARTUP_LINE.length()));
        } catch (URISyntaxException e) {
            System.err.println("Failed to parse server address from startup line: " + urlText);
            return null;
        }
        return new InetSocketAddress(serverURL.getHost(), serverURL.getPort());
    }

    /**
     * Launches the jwebserver which will serve the current directory contents
     *
     * @return The {@link JWebServerProcess} representing the launched server
     * @throws IOException
     */
    public static JWebServerProcess launch() throws IOException {
        return launch(null);
    }

    /**
     * Launches the jwebserver which will serve the passed {@code dirToServe} directory's content.
     *
     * @param dirToServe The directory to serve. Can be null in which case the current directory's
     *                   content will be served.
     * @return The {@link JWebServerProcess} representing the launched server
     * @throws IOException
     */
    public static JWebServerProcess launch(final Path dirToServe) throws IOException {
        if (Files.notExists(JWEBSERVER_TOOL)) {
            throw new IOException("jwebserver tool is missing");
        }
        final Process process;
        // array of one element just to bypass effective final restriction in lambda usage
        final String[] starupLine = new String[1];
        final List<String> processOutput = new ArrayList<>();
        // starts the process, parses the port and awaits startup line before sending requests
        try {
            process = ProcessTools.startProcess("jwebserver",
                    new ProcessBuilder(JWEBSERVER_TOOL.toString(), "-p", "0")
                            .directory(dirToServe == null ? null : dirToServe.toFile()),
                    line -> {
                        processOutput.add(line);
                    },
                    line -> {
                        if (line.startsWith(EXPECTED_SERVER_STARTUP_LINE)) {
                            starupLine[0] = line;
                            return true;
                        }
                        return false;
                    },
                    30,  // suitably high default timeout, not expected to timeout
                    TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            throw new IOException(e);
        }
        if (starupLine[0] == null) {
            // kill the launched process
            process.destroy();
            throw new IOException("Could not determine server address for jwebserver");
        }
        final InetSocketAddress serverAddr = parseServerAddr(starupLine[0]);
        if (serverAddr == null) {
            process.destroy();
            throw new IOException("Could not parse server address of launched jwebserver");
        }
        System.out.println("Launched jwebserver process pid=" + process.pid() + ", address= " + serverAddr);
        return new JWebServerProcess(process, serverAddr, processOutput);
    }
}
