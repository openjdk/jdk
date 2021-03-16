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

package sun.net.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A basic HTTP file server handler for static content.
 * <p>
 * Must be given an absolute pathname to the directory to be served.
 * Supports only HEAD and GET requests. Directory listings and files can be
 * served, content types are supported on a best-guess basis.
 */
public final class FileServerHandler implements HttpHandler {
    private final Path root;
    private final Function<String, String> mimeTable;
    private static final List<String> SUPPORTED_METHODS = List.of("HEAD", "GET");

    private FileServerHandler(Path root, Function<String, String> mimeTable) {
        root = root.normalize();
        if (!Files.exists(root))
            throw new IllegalArgumentException("Path does not exist: " + root);
        if (!Files.isDirectory(root))
            throw new IllegalArgumentException("Path not a directory: " + root);
        if (!root.isAbsolute())
            throw new IllegalArgumentException("Path is not absolute: " + root);
        if (!Files.isReadable(root))
            throw new IllegalArgumentException("Path is not readable: " + root);
        this.root = root;
        this.mimeTable = mimeTable;
    }

    public static HttpHandler create(Path root, Function<String, String> mimeTable)
        throws IOException
    {
        return HttpHandler.newComposedHandler(new FileServerHandler(root, mimeTable),
                r -> !SUPPORTED_METHODS.contains(r.getRequestMethod()), FileServerHandler::handleNotAllowed);
    }

    static void handleNotAllowed(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(405, -1);
            exchange.getResponseHeaders().set("Allow", "HEAD, GET");
        }
    }

    void handleHEAD(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, false);
    }

    void handleGET(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, true);
    }

    void handleSupportedMethod(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        if (Files.isSymbolicLink(path)) {
            handleNotFound(exchange);
        }
        if (Files.isDirectory(path)) {
            if (testMissingSlash(exchange)) {
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

    void handleNotFound(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, 0);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(("<h2>File not found</h2>"
                    + sanitize.apply(exchange.getRequestURI().getPath(), chars)
                    + "<p>").getBytes());
        }
    }

    void handleMovedPermanently(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(301, -1);
        exchange.getResponseHeaders().set("Location", "http://"
                + exchange.getRequestHeaders().getFirst("Host")
                + exchange.getRequestURI().getPath() + "/");
    }


    void discardRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            is.readAllBytes();
        }
    }

    boolean testNotFound(HttpExchange exchange, Path path) throws IOException {
        if (!Files.exists(path)) {
            handleNotFound(exchange);
            return true;
        }
        return false;
    }

    boolean testMissingSlash(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().endsWith("/")) {
            handleMovedPermanently(exchange);
            return true;
        }
        return false;
    }

    Path mapToPath(HttpExchange exchange, Path root) {
        URI uri = exchange.getRequestURI();
        String contextPath = exchange.getHttpContext().getPath();
        if (!contextPath.endsWith("/"))
            contextPath += "/";
        String requestPath = URI.create(contextPath).relativize(uri).getPath();
        Path validatedPath = root.resolve(requestPath).normalize();
        if (!validatedPath.startsWith(root) || isHiddenOrSymLink(validatedPath))
            return root;
        return validatedPath;
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
        respHdrs.set("Content-Type", mediaType(exchange.getRequestURI().getPath()));
        exchange.sendResponseHeaders(200, 0);
        if (writeBody) {
            try (InputStream fis = Files.newInputStream(path);
                 OutputStream os = exchange.getResponseBody()) {
                fis.transferTo(os);
            }
        }
    }

    void listFiles(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        if (writeBody) {
            try (OutputStream os = exchange.getResponseBody();
                 PrintStream ps = new PrintStream(os, false, UTF_8)) {
                ps.println(openHTML
                        + "<h2>Directory listing for "
                        + sanitize.apply(exchange.getRequestURI().getPath(), chars)
                        + "</h2>\n" + "<ul>");
                printDirContent(ps, path);
                ps.println(closeHTML);
                ps.flush();
            }
        }
    }

    private static final String openHTML = """
                <!DOCTYPE html>
                <html>
                <body>""";

    private static final String closeHTML = """
                </ul><p><hr>
                </body>
                </html>""";

    void printDirContent(PrintStream ps, Path path) throws IOException {
        Files.list(path)
                .filter(p -> !isHiddenOrSymLink(p))
                .map(p -> path.toUri().relativize(p.toUri()).toASCIIString())
                .forEach(uri -> ps.println("<li><a href=\"" + uri
                        + "\">" + sanitize.apply(uri, chars) + "</a></li>"));
    }

    static boolean isHiddenOrSymLink(Path path) {
        try {
            return Files.isHidden(path) || Files.isSymbolicLink(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String mediaType(String file) {
        String type = mimeTable.apply(file);
        return type != null ? type : "application/octet-stream";
        // default for unknown content types, as per rfc2046
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
        try (exchange) {
            discardRequestBody(exchange);
            Path path = mapToPath(exchange, root);
            exchange.setAttribute("path", path);  // store path for output filter
            if (testNotFound(exchange, path)) {
                return;
            }
            if (exchange.getRequestMethod().equals("HEAD")) {
                handleHEAD(exchange, path);
            } else {
                handleGET(exchange, path);
            }
        }
    }
}
