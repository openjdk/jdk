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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import sun.net.httpserver.FileServerHandler;
import sun.net.httpserver.OutputFilter;

/**
 * A simple HTTP file server and its components (intended for testing,
 * development and debugging purposes only).
 *
 * <p> A simple file server is composed of three of components:
 * <ul>
 *   <li> an {@link HttpServer HttpServer} that is bound to a given address, </li>
 *   <li> an {@link HttpHandler HttpHandler} that serves files from a given
 *        path on the file-system, and </li>
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
 * that serves files from a given directory path (and its subdirectories) on the
 * file-system. The output level determines what log messages are printed to
 * {@code System.out}, if any.
 *
 * <p> Example of a simple file server:
 * <pre>    {@code var addr = new InetSocketAddress(8080);
 *    var server = SimpleFileServer.createFileServer(addr, Path.of("/some/path"), OutputLevel.DEFAULT);
 *    server.start();}</pre>
 *
 * <h2>File handler</h2>
 *
 * <p> The {@link #createFileHandler(Path) createFileHandler} static factory
 * method returns an {@code HttpHandler} that serves files and directory
 * listings. The handler supports only the <i>HEAD</i> and <i>GET</i> request
 * methods; to handle other request methods, one can either a) add additional
 * handlers to the server, or b) complement the file handler by composing a
 * single handler via {@link HttpHandler#complement(Predicate,HttpHandler)}.
 *
 * <p> Example of adding multiple handlers to a server:
 * <pre>    {@code class PutHandler implements HttpHandler {
 *        @Override
 *        public void handle(HttpExchange exchange) throws IOException {
 *            // handle PUT request
 *        }
 *    }
 *    ...
 *    var handler = SimpleFileServer.createFileHandler(Path.of("/some/path"));
 *    var server = HttpServer.create(addr, 10, "/browse/", handler);
 *    server.createContext("/store/", new PutHandler());
 *    server.start();
 *    }</pre>
 *
 * <p> Example of composing a single handler:
 * <pre>    {@code var handler = SimpleFileServer.createFileHandler(Path.of("/some/path"))
 *                 .complement(request -> request.getRequestMethod().equals("PUT"), new PutHandler());
 *    var server = HttpServer.create(addr, 10, "/some/context/", handler);
 *    server.start();
 *    }</pre>
 *
 * <h2>Output filter</h2>
 *
 * <p> The {@link #createOutputFilter(OutputStream, OutputLevel) createOutputFilter}
 * static factory method returns a {@code Filter} that prints log messages
 * relating to the exchanges handled by the server. The output format is
 * specified by the {@link OutputLevel outputLevel}.
 *
 * <p> Example of an output filter:
 * <pre>    {@code var filter = SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE);
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/store/", new PutHandler(), filter);
 *    server.start();}</pre>
 *
 * <h2>Main entry point</h2>
 *
 * <p> The simple HTTP file server is provided via the main entry point of the
 * {@code jdk.httpserver} module, which can be used on the command line as such:
 *
 * <pre>    {@code java -m jdk.httpserver [-b bind address] [-d directory] [-h to show help message] [-o none|default|verbose] [-p port]}</pre>
 *
 * @since 17
 */
public final class SimpleFileServer {

    private static final Function<String, String> MIME_TABLE =
            s -> URLConnection.getFileNameMap().getContentTypeFor(s);

    private SimpleFileServer() { }

    /**
     * Describes the log message output level produced by the server when
     * processing exchanges.
     */
    public enum OutputLevel {
        /**
         * Used to specify no log message output level.
         */
        NONE,

        /**
         * Used to specify the default log message output level.
         *
         * <p> The default log message format is based on the
         * <a href='https://www.w3.org/Daemon/User/Config/Logging.html#common-logfile-format'>Common Logfile Format</a>,
         * that includes the following information about an {@code HttpExchange}:
         *
         * <p> {@code remotehost rfc931 authuser [date] "request" status bytes}
         *
         * <p> Example:
         * <pre>{@code 127.0.0.1 - - [22/Jun/2000:13:55:36 -0700] "GET /example.txt HTTP/1.1" 200 -}</pre>
         *
         * @implNote The fields {@code rfc931}, {@code authuser} and {@code bytes}
         * are not captured in the implementation, so are always represented as
         * {@code '-'}.
         */
        DEFAULT,

        /**
         * Used to specify the verbose log message output level.
         *
         * <p> Additional to the information provided by the
         * {@linkplain OutputLevel#DEFAULT default} level, the verbose level
         * includes the request and response headers of the {@code HttpExchange}
         * and the absolute path of the resource served up.
         */
        VERBOSE
    }

    /**
     * Creates a <i>file server</i> the serves files from a given path on the
     * file-system.
     *
     * <p> The server is configured with an initial handler that maps the
     * URI path "/" to a <i>file handler</i>. The initial handler is a <i>file
     * handler</i> created as if by an invocation of
     * {@link #createFileHandler(Path) createFileHandler(root)}.
     *
     * <p> An output level can be given to print log messages relating to the
     * exchanges handled by the server. The log messages, if any, are printed to
     * {@code System.out}. If {@link OutputLevel#NONE OutputLevel.NONE} is
     * given, no log messages are printed.
     *
     * @param addr        the address to listen on
     * @param root        the root of the directory to be served, must be an absolute path
     * @param outputLevel the log message output level
     * @return an HttpServer
     * @throws IllegalArgumentException if root is not absolute, not a directory,
     *         does not exist, or is not readable
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
            var handler = FileServerHandler.create(root, MIME_TABLE);
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
     * path (and it subdirectories) on the file-system.
     *
     * <p> The file handler resolves the request URI against the given
     * {@code root} path to determine the path {@code p} on the file-system to
     * serve in the response. If the path {@code p} is a directory, then the
     * response contains a directory listing, formatted in HTML, as the response
     * body. If the path {@code p} is a file, then the response contains a
     * "Content-Type" header based on the best-guess content type, as determined
     * by an invocation of
     * {@linkplain java.net.FileNameMap#getContentTypeFor(String) getContentTypeFor},
     * on the system-wide {@link URLConnection#getFileNameMap() mimeTable}, as
     * well as the contents of the file as the response body.
     *
     * <p> The handler supports only requests with the <i>HEAD</i> or <i>GET</i>
     * method, and will reply with a {@code 405} response code for requests with
     * any other method.
     *
     * @param root the root directory to be served, must be an absolute path
     * @return a file handler
     * @throws IllegalArgumentException if root is not absolute, not a directory,
     *         does not exist, or is not readably
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
     * Creates a {@code Filter} that prints log messages about {@linkplain
     * HttpExchange exchanges}. The log messages are printed to the given
     * {@code OutputStream}.
     *
     * @apiNote To not output any log messages it is recommended to not use a
     * filter.
     *
     * @param out         the stream to print to
     * @param outputLevel the output level
     * @return a Filter
     * @throws IllegalArgumentException if {@link OutputLevel#NONE OutputLevel.NONE}
     *                                  is given
     * @throws NullPointerException     if any argument is null
     */
    // TODO: since we're using an output stream, we should clarity the charset
    //       used or accept a PrintStream
    public static Filter createOutputFilter(OutputStream out,
                                            OutputLevel outputLevel) {
        Objects.requireNonNull(out);
        Objects.requireNonNull(outputLevel);
        return OutputFilter.create(out, outputLevel);
    }
}
