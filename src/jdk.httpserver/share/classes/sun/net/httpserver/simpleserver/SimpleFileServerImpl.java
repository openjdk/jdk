/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver.simpleserver;

import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

/**
 * A class that provides a simple HTTP file server to serve the content of
 * a given directory.
 *
 * <p> The server is an HttpServer bound to a given address. It comes with an
 * HttpHandler that serves files from a given directory path
 * (and its subdirectories) on the default file system, and an optional Filter
 * that prints log messages related to the exchanges handled by the server to
 * a given output stream.
 *
 * <p> Unless specified as arguments, the default values are:<ul>
 * <li>bind address: wildcard address (all interfaces)</li>
 * <li>directory: current working directory</li>
 * <li>outputLevel: info</li></ul>
 * <li>port: 8000</li>
 * <p>
 * The implementation is provided via the main entry point of the jdk.httpserver
 * module.
 */
final class SimpleFileServerImpl {
    private static final InetAddress DEFAULT_ADDR = null;
    private static final int DEFAULT_PORT = 8000;
    private static final Path DEFAULT_ROOT = Path.of("").toAbsolutePath();
    private static final OutputLevel DEFAULT_OUTPUT_LEVEL = OutputLevel.INFO;

    private SimpleFileServerImpl() { throw new AssertionError(); }

    /**
     * Starts a simple HTTP file server created on a directory.
     *
     * @param  writer the writer to which output should be written
     * @param  args the command line options
     * @throws NullPointerException if any of the arguments are {@code null},
     *         or if there are any {@code null} values in the {@code args} array
     * @return startup status code
     */
    static int start(PrintWriter writer, String[] args) {
        Objects.requireNonNull(args);
        for (var arg : args) {
            Objects.requireNonNull(arg);
        }
        Out out = new Out(writer);

        InetAddress addr = DEFAULT_ADDR;
        int port = DEFAULT_PORT;
        Path root = DEFAULT_ROOT;
        OutputLevel outputLevel = DEFAULT_OUTPUT_LEVEL;

        // parse options
        Iterator<String> options = Arrays.asList(args).iterator();
        String option = null;
        String optionArg = null;
        try {
            while (options.hasNext()) {
                option = options.next();
                switch (option) {
                    case "-h", "-?", "--help" -> {
                        out.showHelp();
                        return Startup.OK.statusCode;
                    }
                    case "-b", "--bind-address" ->
                        addr = InetAddress.getByName(optionArg = options.next());
                    case "-d", "--directory" ->
                        root = Path.of(optionArg = options.next());
                    case "-o", "--output" ->
                        outputLevel = Enum.valueOf(OutputLevel.class,
                                (optionArg = options.next()).toUpperCase(Locale.ROOT));
                    case "-p", "--port" ->
                        port = Integer.parseInt(optionArg = options.next());
                    default -> throw new AssertionError();
                }
            }
        } catch (AssertionError ae) {
            out.reportError(ResourceBundleHelper.getMessage("err.unknown.option", option));
            out.showUsage();
            return Startup.CMDERR.statusCode;
        } catch (NoSuchElementException nsee) {
            out.reportError(ResourceBundleHelper.getMessage("err.missing.arg", option));
            return Startup.CMDERR.statusCode;
        } catch (Exception e) {
            out.reportError(ResourceBundleHelper.getMessage("err.invalid.arg", option, optionArg));
            e.printStackTrace(out.writer);
            return Startup.CMDERR.statusCode;
        } finally {
            out.flush();
        }

        // configure and start server
        try {
            var socketAddr = new InetSocketAddress(addr, port);
            var server = SimpleFileServer.createFileServer(socketAddr, root, outputLevel);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            out.printStartMessage(root, server.getAddress().getAddress(), server.getAddress().getPort());
        } catch (Throwable t) {
            out.reportError(ResourceBundleHelper.getMessage("err.server.config.failed", t.getMessage()));
            return Startup.SYSERR.statusCode;
        } finally {
            out.flush();
        }
        return Startup.OK.statusCode;
    }

    private final static class Out {
        private final PrintWriter writer;
        private Out() { throw new AssertionError(); }

        Out(PrintWriter writer) {
            this.writer = Objects.requireNonNull(writer);
        }

        void printStartMessage(Path root, InetAddress inetAddr, int port)
                throws UnknownHostException {
            var isAnyLocal = inetAddr.isAnyLocalAddress();
            var addr = isAnyLocal ? InetAddress.getLocalHost().getHostAddress() : inetAddr.getHostAddress();
            if (isAnyLocal) {
                writer.printf("""
                        Serving %s and subdirectories on 0.0.0.0:%d
                        http://%s:%d/ ...
                        """, root, port, addr, port);
            } else {
                writer.printf("""
                    Serving %s and subdirectories on
                    http://%s:%d/ ...
                    """, root, addr, port);
            }
        }

        void showUsage() {
            writer.println(ResourceBundleHelper.getMessage("usage"));
        }

        void showHelp() {
            writer.println(ResourceBundleHelper.getMessage("usage"));
            writer.println(ResourceBundleHelper.getMessage("options"));
        }

        void reportError(String message) {
            writer.println(ResourceBundleHelper.getMessage("error.prefix") + " " + message);
        }

        void flush() {
            writer.flush();
        }
    }

    private enum Startup {
        /** Started with no errors */
        OK(0),
        /** Not started, bad command-line arguments */
        CMDERR(1),
        /** Not started, system error or resource exhaustion */
        SYSERR(2);

        Startup(int statusCode) {
            this.statusCode = statusCode;
        }
        public final int statusCode;
    }
}
