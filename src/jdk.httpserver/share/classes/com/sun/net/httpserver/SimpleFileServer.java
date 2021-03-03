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

package com.sun.net.httpserver;

import sun.net.httpserver.FileServerHandler;
import sun.net.httpserver.OutputFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A simple HTTP file server and its components (intended for testing,
 * development and debugging purposes only).
 * <p>
 * The simple file server is composed of: <ul>
 * <li>an {@link HttpServer HttpServer} that is bound to a given address,</li>
 * <li>an {@link HttpHandler HttpHandler} that displays the static content of
 * a given directory in HTML,</li>
 * <li>an optional {@link Filter Filter} that outputs information about an
 * {@link HttpExchange HttpExchange}. The output format is specified by a
 * {@link OutputLevel OutputLevel}.</li></ul>
 * <p>
 * The components can be retrieved for reuse and extension via the static
 * methods provided.
 * <p><b>Simple file server</b><p>
 * {@link #createFileServer(InetSocketAddress, Path, OutputLevel)} returns an
 * {@link HttpServer HttpServer} that is a simple out-of-the-box file server.
 * It comes with a handler that displays the static content of the given
 * directory in HTML, and an optional filter that prints output about the
 * {@code HttpExchange} to {@code System.out}.
 * <p>
 * Example of a simple file server:
 * <pre>    {@code var server = SimpleFileServer.createFileServer(new InetSocketAddress(8080), Path.of("/some/path"), OutputLevel.DEFAULT);
 *    server.start();}</pre>
 * <p><b>File server handler</b><p>
 * {@link #createFileHandler(Path)} returns an {@code HttpHandler} that
 * displays the static content of the given directory in HTML. The handler can
 * serve directory listings and files, the content type of a file is determined
 * on a {@linkplain #createFileHandler(Path) best-guess} basis. The handler
 * supports only HEAD and GET requests; to handle request methods other than
 * HEAD and GET, the handler can be complemented, either by adding additional
 * handlers to the server, or by composing a single handler via
 * {@link HttpHandler#handleOrElse(Predicate, HttpHandler)}.
 * <p>Example of adding multiple handlers to a server:
 * <pre>    {@code class PutHandler implements HttpHandler {
 *        @Override
 *        public void handle(HttpExchange exchange) throws IOException {
 *            // handle PUT request
 *        }
 *    }
 *    ...
 *    var handler = SimpleFileServer.createFileHandler(Path.of("/some/path"));
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/browse/", handler);
 *    server.createContext("/store/", new PutHandler());
 *    server.start();
 *    }</pre>
 * <p>
 * Example of composing a single handler:
 * <pre>    {@code var handler = SimpleFileServer.createFileHandler(Path.of("/some/path"))
 *                         .handleOrElse(request -> request.getRequestMethod().equals("PUT"), new PutHandler());
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/some/context/", handler);
 *    server.start();
 *    }</pre>
 * <p><b>Output filter</b><p>
 * {@link #createOutputFilter(OutputStream, OutputLevel)} returns a {@code Filter}
 * that prints output about an {@code HttpExchange} to the given
 * {@code OutputStream}. The output format is specified by the
 * {@link OutputLevel outputLevel}.
 * <p>
 * Example of an output filter:
 * <pre>    {@code var filter = SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE);
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/store/", new PutHandler(), filter);
 *    server.start();}</pre>
 * <p>
 * A default implementation of the simple HTTP file server is provided via the
 * main entry point of the {@code jdk.httpserver} module, which can be used on
 * the command line as such:
 * <p>
 * <pre>    {@code java -m jdk.httpserver [-b bind address] [-d directory] [-h to show help message] [-o none|default|verbose] [-p port]}</pre>
 *
 * @since 17
 */
public final class SimpleFileServer {

    private static final Function<String, String> MIME_TABLE =
            s -> URLConnection.getFileNameMap().getContentTypeFor(s);

    private SimpleFileServer() {
    }

    /**
     * Describes the output produced by an {@code HttpExchange}.
     */
    public enum OutputLevel {
        /**
         * Used to specify no output.
         */
        NONE,
        /**
         * Used to specify output in the default format.
         * <p>
         * The default format is based on the <a href='https://www.w3.org/Daemon/User/Config/Logging.html#common-logfile-format'>Common Logfile Format</a>.
         * and includes the following information about an {@code HttpExchange}:
         * <p>
         * {@code remotehost rfc931 authuser [date] "request" status bytes}
         * <p>
         * Example:
         * <p>
         * {@code 127.0.0.1 - - [22/Jun/2000:13:55:36 -0700] "GET /example.txt HTTP/1.0" 200 -}
         * <p>
         * Note: The fields {@code rfc931}, {@code authuser} and {@code bytes}
         * are not captured in the implementation and are always represented as
         * {@code '-'}.
         */
        DEFAULT,
        /**
         * Used to specify output in the verbose format.
         * <p>
         * Additional to the information provided by the
         * {@linkplain OutputLevel#DEFAULT default} format, the verbose format
         * includes the request and response headers of the {@code HttpExchange}
         * and the absolute path of the resource requested.
         */
        VERBOSE
    }

    /**
     * Creates an {@code HttpServer} with an {@code HttpHandler} that displays
     * the static content of the given directory in HTML.
     * <p>
     * The server is bound to the given address. The handler is mapped to the
     * URI path "/" via an {@code HttpContext}. It only supports HEAD and GET
     * requests and serves directory listings, html and text files.
     * Other MIME types are supported on a best-guess basis.
     * <p>
     * An optional {@code Filter} that prints information about the
     * {@code HttpExchange} to {@code System.out} can be specified via the
     * {@linkplain OutputLevel outputLevel} argument. If
     * {@link OutputLevel#NONE OutputLevel.NONE} is passed, no {@code Filter} is
     * added. Otherwise a {@code Filter} is added with either
     * {@linkplain OutputLevel#DEFAULT default} or
     * {@linkplain OutputLevel#VERBOSE verbose} output format.
     *
     * @param addr        the address to listen on
     * @param root        the root directory to be served, must be an absolute path
     * @param outputLevel the output about an http exchange
     * @return an HttpServer
     * @throws UncheckedIOException if an I/O error occurs
     * @throws NullPointerException if any argument is null
     */
    public static HttpServer createFileServer(InetSocketAddress addr,
                                              Path root,
                                              OutputLevel outputLevel) {
        Objects.requireNonNull(addr);
        Objects.requireNonNull(root);
        Objects.requireNonNull(outputLevel);
        try {
            return outputLevel.equals(OutputLevel.NONE)
                    ? HttpServer.create(addr, 0, "/",
                    FileServerHandler.create(root, MIME_TABLE))
                    : HttpServer.create(addr, 0, "/",
                    FileServerHandler.create(root, MIME_TABLE),
                    new OutputFilter(System.out, outputLevel));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Creates an {@code HttpHandler} that displays the static content of the
     * given directory in HTML.
     * <p>
     * The handler supports only HEAD and GET requests and can serve directory
     * listings and files. Content types are supported on a best-guess basis.
     *
     * @implNote
     * The content type of a file is guessed by calling
     * {@link java.net.FileNameMap#getContentTypeFor(String)} on the
     * {@link URLConnection#getFileNameMap() mimeTable} found.
     *
     * @param root the root directory to be served, must be an absolute path
     * @return an HttpHandler
     * @throws UncheckedIOException if an I/O error occurs
     * @throws NullPointerException if the argument is null
     */
    public static HttpHandler createFileHandler(Path root) {
        Objects.requireNonNull(root);
        try {
            return FileServerHandler.create(root, MIME_TABLE);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Creates a {@code Filter} that prints output about an {@code HttpExchange}
     * to the given {@code OutputStream}.
     * <p>
     * The output format is specified by the {@link OutputLevel outputLevel}.
     *
     * @implNote
     * An {@link IllegalArgumentException} is thrown if
     * {@link OutputLevel#NONE OutputLevel.NONE} is passed. It is recommended to
     * not use a filter in this case.
     *
     * @param out         the OutputStream to print to
     * @param outputLevel the output about an http exchange
     * @return a Filter
     * @throws IllegalArgumentException if an invalid outputLevel is passed
     * @throws NullPointerException     if any argument is null
     */
    public static Filter createOutputFilter(OutputStream out,
                                            OutputLevel outputLevel) {
        Objects.requireNonNull(out);
        Objects.requireNonNull(outputLevel);
        return new OutputFilter(out, outputLevel);
    }
}
