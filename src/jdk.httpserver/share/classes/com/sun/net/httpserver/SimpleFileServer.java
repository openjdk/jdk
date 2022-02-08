/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.net.httpserver.simpleserver.FileServerHandler;
import sun.net.httpserver.simpleserver.OutputFilter;

/**
 * A simple HTTP file server and its components (intended for testing,
 * development and debugging purposes only).
 *
 * <p> A <a href="#server-impl">simple file server</a> is composed of three
 * components:
 * <ul>
 *   <li> an {@link HttpServer HttpServer} that is bound to a given address, </li>
 *   <li> an {@link HttpHandler HttpHandler} that serves files from a given
 *        directory path, and </li>
 *   <li> an optional {@link Filter Filter} that prints log messages relating to
 *        the exchanges handled by the server. </li>
 * </ul>
 * The individual server components can be retrieved for reuse and extension via
 * the static methods provided.
 *
 * <h2>Simple file server</h2>
 *
 * <p> The {@link #createFileServer(InetSocketAddress,Path,OutputLevel) createFileServer}
 * static factory method returns an {@link HttpServer HttpServer} that is a
 * simple out-of-the-box file server. The server comes with an initial handler
 * that serves files from a given directory path (and its subdirectories).
 * The output level determines what log messages are printed to
 * {@code System.out}, if any.
 *
 * <p> Example of a simple file server:
 * <pre>{@code
 *    var addr = new InetSocketAddress(8080);
 *    var server = SimpleFileServer.createFileServer(addr, Path.of("/some/path"), OutputLevel.INFO);
 *    server.start();
 * }</pre>
 *
 * <h2>File handler</h2>
 *
 * <p> The {@link #createFileHandler(Path) createFileHandler} static factory
 * method returns an {@code HttpHandler} that serves files and directory
 * listings. The handler supports only the <i>HEAD</i> and <i>GET</i> request
 * methods; to handle other request methods, one can either add additional
 * handlers to the server, or complement the file handler by composing a single
 * handler via
 * {@link HttpHandlers#handleOrElse(Predicate, HttpHandler, HttpHandler)}.
 *
 * <p>Example of composing a single handler:
 * <pre>{@code
 *    var handler = HttpHandlers.handleOrElse(
 *        (req) -> req.getRequestMethod().equals("PUT"),
 *        (exchange) -> {
 *            // validate and handle PUT request
 *        },
 *        SimpleFileServer.createFileHandler(Path.of("/some/path")))
 *    );
 * }</pre>
 *
 * <h2>Output filter</h2>
 *
 * <p> The {@link #createOutputFilter(OutputStream, OutputLevel) createOutputFilter}
 * static factory method returns a
 * {@link Filter#afterHandler(String, Consumer) post-processing filter} that
 * prints log messages relating to the exchanges handled by the server. The
 * output format is specified by the {@link OutputLevel outputLevel}.
 *
 * <p> Example of an output filter:
 * <pre>{@code
 *    var filter = SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE);
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/some/path/", new SomeHandler(), filter);
 *    server.start();
 * }</pre>
 *
 * <h2>jwebserver Tool</h2>
 *
 * <p>A simple HTTP file server implementation is provided via the
 * {@code jwebserver} tool.
 *
 * @toolGuide jwebserver
 *
 * @since 18
 */
public final class SimpleFileServer {

    private static final UnaryOperator<String> MIME_TABLE =
            URLConnection.getFileNameMap()::getContentTypeFor;

    private SimpleFileServer() { }

    /**
     * Describes the log message output level produced by the server when
     * processing exchanges.
     *
     * @since 18
     */
    public enum OutputLevel {
        /**
         * Used to specify no log message output level.
         */
        NONE,

        /**
         * Used to specify the informative log message output level.
         *
         * <p> The log message format is based on the
         * <a href='https://www.w3.org/Daemon/User/Config/Logging.html#common-logfile-format'>Common Logfile Format</a>,
         * that includes the following information about an {@code HttpExchange}:
         *
         * <p> {@code remotehost rfc931 authuser [date] "request" status bytes}
         *
         * <p> Example:
         * <pre>{@code
         *    127.0.0.1 - - [22/Jun/2000:13:55:36 -0700] "GET /example.txt HTTP/1.1" 200 -
         * }</pre>
         *
         * @implNote The fields {@code rfc931}, {@code authuser} and {@code bytes}
         * are not captured in the implementation, so are always represented as
         * {@code '-'}.
         */
        INFO,

        /**
         * Used to specify the verbose log message output level.
         *
         * <p> Additional to the information provided by the
         * {@linkplain OutputLevel#INFO info} level, the verbose level
         * includes the request and response headers of the {@code HttpExchange}
         * and the absolute path of the resource served up.
         */
        VERBOSE
    }

    /**
     * Creates a <i>file server</i> that serves files from a given path.
     *
     * <p> The server is configured with an initial context that maps the
     * URI {@code path} to a <i>file handler</i>. The <i>file handler</i> is
     * created as if by an invocation of
     * {@link #createFileHandler(Path) createFileHandler(rootDirectory)}, and is
     * associated to a context created as if by an invocation of
     * {@link HttpServer#createContext(String) createContext("/")}. The returned
     * server is not started.
     *
     * <p> An output level can be given to print log messages relating to the
     * exchanges handled by the server. The log messages, if any, are printed to
     * {@code System.out}. If {@link OutputLevel#NONE OutputLevel.NONE} is
     * given, no log messages are printed.
     *
     * @param addr          the address to listen on
     * @param rootDirectory the root directory to be served, must be an absolute path
     * @param outputLevel   the log message output level
     * @return an HttpServer
     * @throws IllegalArgumentException if root does not exist, is not absolute,
     *         is not a directory, or is not readable
     * @throws UncheckedIOException if an I/O error occurs
     * @throws NullPointerException if any argument is null
     * @throws SecurityException if a security manager is installed and a
     *         recursive {@link java.io.FilePermission} "{@code read}" of the
     *         rootDirectory is denied
     */
    public static HttpServer createFileServer(InetSocketAddress addr,
                                              Path rootDirectory,
                                              OutputLevel outputLevel) {
        Objects.requireNonNull(addr);
        Objects.requireNonNull(rootDirectory);
        Objects.requireNonNull(outputLevel);
        try {
            var handler = FileServerHandler.create(rootDirectory, MIME_TABLE);
            if (outputLevel.equals(OutputLevel.NONE))
                return HttpServer.create(addr, 0, "/", handler);
            else
                return HttpServer.create(addr, 0, "/", handler, OutputFilter.create(System.out, outputLevel));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Creates a <i>file handler</i> that serves files from a given directory
     * path (and its subdirectories).
     *
     * <p> The file handler resolves the request URI against the given
     * {@code rootDirectory} path to determine the path {@code p} on the
     * associated file system to serve the response. If the path {@code p} is
     * a directory, then the response contains a directory listing, formatted in
     * HTML, as the response body. If the path {@code p} is a file, then the
     * response contains a "Content-Type" header based on the best-guess
     * content type, as determined by an invocation of
     * {@linkplain java.net.FileNameMap#getContentTypeFor(String) getContentTypeFor},
     * on the system-wide {@link URLConnection#getFileNameMap() mimeTable}, as
     * well as the contents of the file as the response body.
     *
     * <p> The handler supports only requests with the <i>HEAD</i> or <i>GET</i>
     * method, and will reply with a {@code 405} response code for requests with
     * any other method.
     *
     * @param rootDirectory the root directory to be served, must be an absolute path
     * @return a file handler
     * @throws IllegalArgumentException if rootDirectory does not exist,
     *         is not absolute, is not a directory, or is not readable
     * @throws NullPointerException if the argument is null
     * @throws SecurityException if a security manager is installed and a
     *         recursive {@link java.io.FilePermission} "{@code read}" of the
     *         rootDirectory is denied
     */
    public static HttpHandler createFileHandler(Path rootDirectory) {
        Objects.requireNonNull(rootDirectory);
        return FileServerHandler.create(rootDirectory, MIME_TABLE);
    }

    /**
     * Creates a {@linkplain Filter#afterHandler(String, Consumer)
     * post-processing Filter} that prints log messages about
     * {@linkplain HttpExchange exchanges}. The log messages are printed to
     * the given {@code OutputStream} in {@code UTF-8} encoding.
     *
     * @apiNote
     * To not output any log messages it is recommended to not use a filter.
     *
     * @param out         the stream to print to
     * @param outputLevel the output level
     * @return a post-processing filter
     * @throws IllegalArgumentException if {@link OutputLevel#NONE OutputLevel.NONE}
     *                                  is given
     * @throws NullPointerException     if any argument is null
     */
    public static Filter createOutputFilter(OutputStream out,
                                            OutputLevel outputLevel) {
        Objects.requireNonNull(out);
        Objects.requireNonNull(outputLevel);
        return OutputFilter.create(out, outputLevel);
    }
}
