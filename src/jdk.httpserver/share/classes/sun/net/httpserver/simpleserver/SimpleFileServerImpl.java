/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

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
 * <li>bind address: 127.0.0.1 or ::1 (loopback)</li>
 * <li>directory: current working directory</li>
 * <li>outputLevel: info</li></ul>
 * <li>port: 8000</li>
 * <p>
 * The implementation is provided via the main entry point of the jdk.httpserver
 * module.
 */
final class SimpleFileServerImpl {
    private static final InetAddress LOOPBACK_ADDR = InetAddress.getLoopbackAddress();
    private static final int DEFAULT_PORT = 8000;
    private static final Path DEFAULT_ROOT = Path.of("").toAbsolutePath();
    private static final OutputLevel DEFAULT_OUTPUT_LEVEL = OutputLevel.INFO;
    private static boolean addrSpecified = false;

    private SimpleFileServerImpl() { throw new AssertionError(); }

    /**
     * Starts a simple HTTP file server created on a directory.
     *
     * @param  writer the writer to which output should be written
     * @param  args the command line options
     * @param  launcher the launcher the server is started from
     * @throws NullPointerException if any of the arguments are {@code null},
     *         or if there are any {@code null} values in the {@code args} array
     * @return startup status code
     */
    static int start(PrintWriter writer, String launcher, String[] args) {
        Objects.requireNonNull(args);
        for (var arg : args) {
            Objects.requireNonNull(arg);
        }
        Out out = new Out(writer);

        InetAddress addr = LOOPBACK_ADDR;
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
                        out.showHelp(launcher);
                        return Startup.OK.statusCode;
                    }
                    case "-version", "--version" -> {
                        out.showVersion(launcher);
                        return Startup.OK.statusCode;
                    }
                    case "-b", "--bind-address" -> {
                        addr = InetAddress.getByName(optionArg = options.next());
                        addrSpecified = true;
                    }
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
            out.showUsage(launcher);
            return Startup.CMDERR.statusCode;
        } catch (NoSuchElementException nsee) {
            out.reportError(ResourceBundleHelper.getMessage("err.missing.arg", option));
            out.showOption(option);
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
            root = realPath(root);
            var socketAddr = new InetSocketAddress(addr, port);
            var server = SimpleFileServer.createFileServer(socketAddr, root, outputLevel);
            server.start();
            out.printStartMessage(root, server);
        } catch (Throwable t) {
            out.reportError(ResourceBundleHelper.getMessage("err.server.config.failed", t.getMessage()));
            return Startup.SYSERR.statusCode;
        } finally {
            out.flush();
        }
        return Startup.OK.statusCode;
    }

    private static Path realPath(Path root) {

        // `toRealPath()` invocation below already checks if file exists, though
        // there is no way to figure out if it fails due to a non-existent file.
        // Hence, checking the existence here first to deliver the user a more
        // descriptive message.
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }

        // Obtain the real path
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Path is invalid: " + root, exception);
        }

    }

    private static final class Out {
        private final PrintWriter writer;
        private Out() { throw new AssertionError(); }

        Out(PrintWriter writer) {
            this.writer = Objects.requireNonNull(writer);
        }

        void printStartMessage(Path root, HttpServer server)
                throws UnknownHostException
        {
            String port = Integer.toString(server.getAddress().getPort());
            var inetAddr = server.getAddress().getAddress();
            var isAnyLocal = inetAddr.isAnyLocalAddress();
            var addr = isAnyLocal ? InetAddress.getLocalHost().getHostAddress() : inetAddr.getHostAddress();
            if (!addrSpecified) {
                writer.println(ResourceBundleHelper.getMessage("loopback.info"));
            }
            if (inetAddr instanceof Inet6Address && addr.contains(":") && !addr.startsWith("[")) {
                // we use the "addr" when printing the URL, so make sure it
                // conforms to RFC-2732, section 2:
                // To use a literal IPv6 address in a URL, the literal
                // address should be enclosed in "[" and "]" characters.
                addr = "[" + addr + "]";
            }
            if (isAnyLocal) {
                writer.println(ResourceBundleHelper.getMessage("msg.start.anylocal", root, addr, port));
            } else {
                writer.println(ResourceBundleHelper.getMessage("msg.start.other", root, addr, port));
            }
        }

        void showUsage(String launcher) {
            writer.println(ResourceBundleHelper.getMessage("usage." + launcher));
        }

        void showVersion(String launcher) {
            writer.println(ResourceBundleHelper.getMessage("version", launcher, System.getProperty("java.version")));
        }

        void showHelp(String launcher) {
            writer.println(ResourceBundleHelper.getMessage("usage." + launcher));
            writer.println(ResourceBundleHelper.getMessage("options", LOOPBACK_ADDR.getHostAddress()));
        }

        void showOption(String option) {
            switch (option) {
                case "-b", "--bind-address" ->
                        writer.println(ResourceBundleHelper.getMessage("opt.bindaddress", LOOPBACK_ADDR.getHostAddress()));
                case "-d", "--directory" ->
                        writer.println(ResourceBundleHelper.getMessage("opt.directory"));
                case "-o", "--output" ->
                        writer.println(ResourceBundleHelper.getMessage("opt.output"));
                case "-p", "--port" ->
                        writer.println(ResourceBundleHelper.getMessage("opt.port"));
            }
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
