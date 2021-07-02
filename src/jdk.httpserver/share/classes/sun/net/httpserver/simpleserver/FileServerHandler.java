/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpHandlers;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A basic HTTP file server handler for static content.
 * <p>
 * Must be given an absolute pathname to the directory to be served.
 * Supports only HEAD and GET requests. Directory listings and files can be
 * served, content types are supported on a best-guess basis.
 */
public final class FileServerHandler implements HttpHandler {

    private static final List<String> SUPPORTED_METHODS = List.of("HEAD", "GET");
    private static final List<String> UNSUPPORTED_METHODS = List.of("CONNECT", "DELETE", "OPTIONS", "PATCH", "POST", "PUT", "TRACE");

    private final Path root;
    private final UnaryOperator<String> mimeTable;

    private FileServerHandler(Path root, UnaryOperator<String> mimeTable) {
        root = root.normalize();
        if (!Files.exists(root))
            throw new IllegalArgumentException("Path does not exist: " + root);
        if (!root.isAbsolute())
            throw new IllegalArgumentException("Path is not absolute: " + root);
        if (!Files.isDirectory(root))
            throw new IllegalArgumentException("Path is not a directory: " + root);
        if (!Files.isReadable(root))
            throw new IllegalArgumentException("Path is not readable: " + root);
        if (root.getFileSystem() != FileSystems.getDefault()
                || root.getFileSystem().getClass().getModule() != Object.class.getModule())
            throw new IllegalArgumentException("Path is not associated with " +
                    "the system-default file system: " + root);
        this.root = root;
        this.mimeTable = mimeTable;
    }

    private static final HttpHandler NOT_IMPLEMENTED_HANDLER =
            HttpHandlers.of(501, Headers.of(), "");

    private static final HttpHandler METHOD_NOT_ALLOWED_HANDLER =
            HttpHandlers.of(405, Headers.of("Allow", "HEAD, GET"), "");

    public static HttpHandler create(Path root, UnaryOperator<String> mimeTable) {
        var fallbackHandler = HttpHandlers.handleOrElse(
                r -> UNSUPPORTED_METHODS.contains(r.getRequestMethod()),
                METHOD_NOT_ALLOWED_HANDLER,
                NOT_IMPLEMENTED_HANDLER);
        return HttpHandlers.handleOrElse(
                r -> SUPPORTED_METHODS.contains(r.getRequestMethod()),
                new FileServerHandler(root, mimeTable), fallbackHandler);
    }

    void handleHEAD(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, false);
    }

    void handleGET(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, true);
    }

    void handleSupportedMethod(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException {
        if (Files.isDirectory(path)) {
            if (missingSlash(exchange)) {
                handleMovedPermanently(exchange);
                return;
            }
            if (indexFile(path) != null) {
                serveFile(exchange, indexFile(path), writeBody);
            } else {
                listFiles(exchange, path, writeBody);
            }
        } else {
            serveFile(exchange, path, writeBody);
        }
    }

    void handleMovedPermanently(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "http://"
                + exchange.getRequestHeaders().getFirst("Host")
                + exchange.getRequestURI().getPath() + "/");
        exchange.sendResponseHeaders(301, -1);
    }

    void handleForbidden(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(403, -1);
    }

    void handleNotFound(HttpExchange exchange) throws IOException {
        var bytes = (openHTML
                + "<h1>File not found</h1>\n"
                + "<p>" + sanitize.apply(exchange.getRequestURI().getPath(), chars) + "</p>\n"
                + closeHTML).getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");

        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
            exchange.sendResponseHeaders(404, -1);
        } else {
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static void discardRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            is.readAllBytes();
        }
    }

    boolean missingSlash(HttpExchange exchange) {
        return !exchange.getRequestURI().getPath().endsWith("/");
    }

    Path mapToPath(HttpExchange exchange, Path root) {
        URI rootURI = root.toUri();
        URI requestURI = exchange.getRequestURI();
        String contextPath = exchange.getHttpContext().getPath();
        String requestPath = URI.create(contextPath).relativize(requestURI).getPath();
        try {
            return Path.of(rootURI.resolve(requestPath)).normalize();
        } catch (IllegalArgumentException iae) {
            return null;  // could not resolve request URI
        }
    }

    Path indexFile(Path path) {
        Path html = path.resolve("index.html");
        Path htm = path.resolve("index.htm");
        return Files.exists(html) ? html : Files.exists(htm) ? htm : null;
    }

    void serveFile(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        var respHdrs = exchange.getResponseHeaders();
        respHdrs.set("Content-Type", mediaType(path.toString()));
        respHdrs.set("Last-Modified", getLastModified(path));
        if (writeBody) {
            exchange.sendResponseHeaders(200, Files.size(path));
            try (InputStream fis = Files.newInputStream(path);
                 OutputStream os = exchange.getResponseBody()) {
                fis.transferTo(os);
            }
        } else {
            respHdrs.set("Content-Length", Long.toString(Files.size(path)));
            exchange.sendResponseHeaders(200, -1);
        }
    }

    void listFiles(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        var respHdrs = exchange.getResponseHeaders();
        respHdrs.set("Content-Type", "text/html; charset=UTF-8");
        respHdrs.set("Last-Modified", getLastModified(path));
        var body = dirListing(exchange, path);
        if (writeBody) {
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream os = exchange.getResponseBody();
                 PrintStream ps = new PrintStream(os, false, UTF_8)) {
                ps.writeBytes(body.getBytes(UTF_8));
                ps.flush();
            }
        } else {
            respHdrs.set("Content-Length", Integer.toString(body.length()));
            exchange.sendResponseHeaders(200, -1);
        }
    }

    private static final String openHTML = """
            <!DOCTYPE html>
            <html>
            <body>
            """;

    private static final String closeHTML = """
            </body>
            </html>
            """;

    String dirListing(HttpExchange exchange, Path path) throws IOException {
        var sb = new StringBuffer(openHTML
                + "<h1>Directory listing for "
                + sanitize.apply(exchange.getRequestURI().getPath(), chars)
                + "</h1>\n"
                + "<ul>\n");
        try (var paths = Files.list(path)) {
            paths.filter(p -> !isHiddenOrSymLink(p))
                 .map(p -> path.toUri().relativize(p.toUri()).toASCIIString())
                 .forEach(uri -> sb.append("<li><a href=\"" + uri + "\">" + sanitize.apply(uri, chars) + "</a></li>\n"));
        }
        sb.append("</ul>\n");
        sb.append(closeHTML);

        return sb.toString();
    }

    // HTTP-Date as per (rfc5322). Example: Sun, 06 Nov 1994 08:49:37 GMT
    static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss v");

    static String getLastModified(Path path) throws IOException {
        var fileTime = Files.getLastModifiedTime(path);
        return fileTime.toInstant().atZone(ZoneId.of("GMT")).format(HTTP_DATE_FORMATTER);
    }

    static boolean isHiddenOrSymLink(Path path) {
        try {
            return Files.isHidden(path) || Files.isSymbolicLink(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // default for unknown content types, as per RFC 2046
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    String mediaType(String file) {
        String type = mimeTable.apply(file);
        return type != null ? type : DEFAULT_CONTENT_TYPE;
    }

    static final BiFunction<String, HashMap<Integer, String>, String> sanitize =
            (file, chars) -> file.chars().collect(StringBuilder::new,
                    (sb, c) -> sb.append(chars.getOrDefault(c, Character.toString(c))),
                    StringBuilder::append).toString();

    static final HashMap<Integer,String> chars = new HashMap<>(Map.of(
            (int) '&'  , "&amp;"   ,
            (int) '<'  , "&lt;"    ,
            (int) '>'  , "&gt;"    ,
            (int) '"'  , "&quot;"  ,
            (int) '\'' , "&#x27;"  ,
            (int) '/'  , "&#x2F;"  )
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        assert List.of("GET", "HEAD").contains(exchange.getRequestMethod());
        try (exchange) {
            discardRequestBody(exchange);
            Path path = mapToPath(exchange, root);
            if (path != null) {
                exchange.setAttribute("request-path", path);  // store for OutputFilter
                if (!Files.exists(path) || isHiddenOrSymLink(path)) {
                    handleNotFound(exchange);
                } else if (!path.startsWith(root) || !Files.isReadable(path)) {
                    handleForbidden(exchange);
                } else if (exchange.getRequestMethod().equals("HEAD")) {
                    handleHEAD(exchange, path);
                } else {
                    handleGET(exchange, path);
                }
            } else {
                exchange.setAttribute("request-path", "could not resolve request URI");
                handleNotFound(exchange);
            }
        }
    }
}
