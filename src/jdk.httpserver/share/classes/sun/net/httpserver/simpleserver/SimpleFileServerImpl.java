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
 * <p>The server is an HttpServer bound to a given address. It comes with an
 * HttpHandler that serves files from a given directory path
 * (and its subdirectories) on the file-system, and an optional Filter that
 * prints log messages related to the exchanges handled by the server to a given
 * output stream.
 *
 * <p>Unless specified as arguments, the default values are:<ul>
 * <li>address: wildcard address (all interfaces)</li>
 * <li>port: 8000</li>
 * <li>directory: current working directory</li>
 * <li>outputLevel: default</li></ul>
 * <p>
 * The implementation is provided via the main entry point of the jdk.httpserver
 * module.
 */
final class SimpleFileServerImpl {
    private static final InetAddress ADDR = null;
    private static final int PORT = 8000;
    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final OutputLevel OUTPUT_LEVEL = OutputLevel.DEFAULT;
    private static PrintWriter out;

    /**
     * Starts a simple HTTP file server created on a directory.
     *
     * @param  writer the writer to which output should be written
     * @param  args the command line options
     * @throws NullPointerException if any of the arguments is null
     */
    static int start(PrintWriter writer, String[] args) {
        Objects.requireNonNull(writer);
        Objects.requireNonNull(args);
        out = writer;
        InetAddress addr = ADDR;
        int port = PORT;
        Path root = ROOT;
        OutputLevel outputLevel = OUTPUT_LEVEL;

        // parse args
        Iterator<String> options = Arrays.asList(args).iterator();
        String option = null;
        try {
            while (options.hasNext()) {
                option = options.next();
                switch (option) {
                    case "-h" -> {
                        showHelp();
                        return Result.OK.statusCode;
                    }
                    case "-b" -> addr = InetAddress.getByName(options.next());
                    case "-p" -> port = Integer.parseInt(options.next());
                    case "-d" -> root = Path.of(options.next());
                    case "-o" -> outputLevel = Enum.valueOf(OutputLevel.class,
                            options.next().toUpperCase(Locale.ROOT));
                    default -> throw new AssertionError();
                }
            }
        } catch (AssertionError ae) {
            reportError(getMessage("err.unknown.option", option));
            showUsage();
            return Result.CMDERR.statusCode;
        } catch (NoSuchElementException nsee) {
            reportError(getMessage("err.missing.arg", option));
            return Result.CMDERR.statusCode;
        } catch (Exception e) {
            reportError(getMessage("err.invalid.arg", option, e.getMessage()));
            e.printStackTrace(out);
            return Result.CMDERR.statusCode;
        } finally {
            out.flush();
        }

        // configure and start server
        try {
            var socketAddr = new InetSocketAddress(addr, port);
            var server = SimpleFileServer.createFileServer(socketAddr, root, outputLevel);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            out.printf("Serving %s and subdirectories on http://%s:%d/ ...\n",
                    root, socketAddr.getHostString(), port);
        } catch (Exception e) {
            reportError(getMessage("err.server.config.failed", e.getMessage()));
            e.printStackTrace(out);
            return Result.SYSERR.statusCode;
        } finally {
            out.flush();
        }
        return Result.OK.statusCode;
    }

    // TODO: WHICH OUTPUT DO WE WANT TO SHOW, AND WHEN?
    private static void showUsage() {
        out.println(getMessage("usage")); }

    private static void showSummary() {
        out.println(getMessage("usage.summary"));
    }

    private static void showHelp() {
        out.println(getMessage("usage"));
        out.println(getMessage("options"));
    }

    private static void reportError(String message) {
        out.println(getMessage("error.prefix") + " " + message);
    }

    private static String getMessage(String key, Object... args) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private static class ResourceBundleHelper {
        static final ResourceBundle bundle;

        static {
            try {
                bundle = ResourceBundle.getBundle("sun.net.httpserver.simpleserver.resources.simpleserver");
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find simpleserver resource bundle for locale " + Locale.getDefault());
            }
        }
    }

    // TODO: WHICH STATUS CODES DO WE WANT TO RETURN?
    enum Result {
        /** Completed with no errors */
        OK(0),
        /** Completed with reported errors */
        ERROR(1),
        /** Bad command-line arguments */
        CMDERR(2),
        /** System error or resource exhaustion */
        SYSERR(3),
        /** Terminated abnormally */
        ABNORMAL(4),
        /** IO exception */
        IOEX(5);

        Result(int statusCode) {
            this.statusCode = statusCode;
        }

        public final int statusCode;

        @Override
        public String toString() {
            return name() + '(' + statusCode + ')';
        }
    }
}
