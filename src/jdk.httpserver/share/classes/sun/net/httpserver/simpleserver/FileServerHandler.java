/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpHandlers;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A basic HTTP file server handler for static content.
 *
 * <p> Must be given an absolute pathname to the directory to be served.
 * Supports only HEAD and GET requests. Directory listings and files can be
 * served, content types are supported on a best-guess basis.
 */
public final class FileServerHandler implements HttpHandler {

    private static final List<String> SUPPORTED_METHODS = List.of("HEAD", "GET");
    private static final List<String> UNSUPPORTED_METHODS =
            List.of("CONNECT", "DELETE", "OPTIONS", "PATCH", "POST", "PUT", "TRACE");

    private final Path root;
    private final UnaryOperator<String> mimeTable;
    private final Logger logger;

    private FileServerHandler(Path root, UnaryOperator<String> mimeTable) {
        root = root.normalize();

        @SuppressWarnings("removal")
        var securityManager = System.getSecurityManager();
        if (securityManager != null)
            securityManager.checkRead(pathForSecurityCheck(root.toString()));

        if (!Files.exists(root))
            throw new IllegalArgumentException("Path does not exist: " + root);
        if (!root.isAbsolute())
            throw new IllegalArgumentException("Path is not absolute: " + root);
        if (!Files.isDirectory(root))
            throw new IllegalArgumentException("Path is not a directory: " + root);
        if (!Files.isReadable(root))
            throw new IllegalArgumentException("Path is not readable: " + root);
        this.root = root;
        this.mimeTable = mimeTable;
        this.logger = System.getLogger("com.sun.net.httpserver");
    }

    private static String pathForSecurityCheck(String path) {
        var separator = String.valueOf(File.separatorChar);
        return path.endsWith(separator) ? (path + "-") : (path + separator + "-");
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

    private void handleHEAD(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, false);
    }

    private void handleGET(HttpExchange exchange, Path path) throws IOException {
        handleSupportedMethod(exchange, path, true);
    }

    private void handleSupportedMethod(HttpExchange exchange, Path path, boolean writeBody)
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

    private void handleMovedPermanently(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", getRedirectURI(exchange.getRequestURI()));
        exchange.sendResponseHeaders(301, -1);
    }

    private void handleForbidden(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(403, -1);
    }

    private void handleNotFound(HttpExchange exchange) throws IOException {
        String fileNotFound = ResourceBundleHelper.getMessage("html.not.found");
        var bytes = (openHTML
                + "<h1>" + fileNotFound + "</h1>\n"
                + "<p>" + sanitize.apply(exchange.getRequestURI().getPath()) + "</p>\n"
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

    private static void discardRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            is.skip(Integer.MAX_VALUE);
        }
    }

    private String getRedirectURI(URI uri) {
        String query = uri.getRawQuery();
        String redirectPath = uri.getRawPath() + "/";
        return query == null ? redirectPath : redirectPath + "?" + query;
    }

    private static boolean missingSlash(HttpExchange exchange) {
        return !exchange.getRequestURI().getPath().endsWith("/");
    }

    private static String contextPath(HttpExchange exchange) {
        String context = exchange.getHttpContext().getPath();
        if (!context.startsWith("/")) {
            throw new IllegalArgumentException("Context path invalid: " + context);
        }
        return context;
    }

    private static String requestPath(HttpExchange exchange) {
        String request = exchange.getRequestURI().getPath();
        if (!request.startsWith("/")) {
            throw new IllegalArgumentException("Request path invalid: " + request);
        }
        return request;
    }

    // Checks that the request does not escape context.
    private static void checkRequestWithinContext(String requestPath,
                                                  String contextPath) {
        if (requestPath.equals(contextPath)) {
            return;  // context path requested, e.g. context /foo, request /foo
        }
        String contextPathWithTrailingSlash = contextPath.endsWith("/")
                ? contextPath : contextPath + "/";
        if (!requestPath.startsWith(contextPathWithTrailingSlash)) {
            throw new IllegalArgumentException("Request not in context: " + contextPath);
        }
    }

    // Checks that path is, or is within, the root.
    private static Path checkPathWithinRoot(Path path, Path root) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Request not in root");
        }
        return path;
    }

    // Returns the request URI path relative to the context.
    private static String relativeRequestPath(HttpExchange exchange) {
        String context = contextPath(exchange);
        String request = requestPath(exchange);
        checkRequestWithinContext(request, context);
        return request.substring(context.length());
    }

    private Path mapToPath(HttpExchange exchange, Path root) {
        try {
            assert root.isAbsolute() && Files.isDirectory(root);  // checked during creation
            String uriPath = relativeRequestPath(exchange);
            String[] pathSegment = uriPath.split("/");

            // resolve each path segment against the root
            Path path = root;
            for (var segment : pathSegment) {
                if (!URIPathSegment.isSupported(segment)) {
                    return null;  // stop resolution, null results in 404 response
                }
                path = path.resolve(segment);
                if (!Files.isReadable(path) || isHiddenOrSymLink(path)) {
                    return null;  // stop resolution
                }
            }
            path = path.normalize();
            return checkPathWithinRoot(path, root);
        } catch (Exception e) {
            logger.log(System.Logger.Level.TRACE,
                    "FileServerHandler: request URI path resolution failed", e);
            return null;  // could not resolve request URI path
        }
    }

    private static Path indexFile(Path path) {
        Path html = path.resolve("index.html");
        Path htm = path.resolve("index.htm");
        return Files.exists(html) ? html : Files.exists(htm) ? htm : null;
    }

    private void serveFile(HttpExchange exchange, Path path, boolean writeBody)
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

    private void listFiles(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        var respHdrs = exchange.getResponseHeaders();
        respHdrs.set("Content-Type", "text/html; charset=UTF-8");
        respHdrs.set("Last-Modified", getLastModified(path));
        var bodyBytes = dirListing(exchange, path).getBytes(UTF_8);
        if (writeBody) {
            exchange.sendResponseHeaders(200, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        } else {
            respHdrs.set("Content-Length", Integer.toString(bodyBytes.length));
            exchange.sendResponseHeaders(200, -1);
        }
    }

    private static final String openHTML = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8"/>
            </head>
            <body>
            """;

    private static final String closeHTML = """
            </body>
            </html>
            """;

    private static final String hrefListItemTemplate = """
            <li><a href="%s">%s</a></li>
            """;

    private static String hrefListItemFor(URI uri) {
        return hrefListItemTemplate.formatted(uri.toASCIIString(), sanitize.apply(uri.getPath()));
    }

    private static String dirListing(HttpExchange exchange, Path path) throws IOException {
        String dirListing = ResourceBundleHelper.getMessage("html.dir.list");
        var sb = new StringBuilder(openHTML
                + "<h1>" + dirListing + " "
                + sanitize.apply(exchange.getRequestURI().getPath())
                + "</h1>\n"
                + "<ul>\n");
        try (var paths = Files.list(path)) {
            paths.filter(p -> Files.isReadable(p) && !isHiddenOrSymLink(p))
                 .map(p -> path.toUri().relativize(p.toUri()))
                 .forEach(uri -> sb.append(hrefListItemFor(uri)));
        }
        sb.append("</ul>\n");
        sb.append(closeHTML);

        return sb.toString();
    }

    private static String getLastModified(Path path) throws IOException {
        var fileTime = Files.getLastModifiedTime(path);
        return fileTime.toInstant().atZone(ZoneId.of("GMT"))
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    private static boolean isHiddenOrSymLink(Path path) {
        try {
            return Files.isHidden(path) || Files.isSymbolicLink(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Default for unknown content types, as per RFC 2046
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private String mediaType(String file) {
        String type = mimeTable.apply(file);
        return type != null ? type : DEFAULT_CONTENT_TYPE;
    }

    // A non-exhaustive map of reserved-HTML and special characters to their
    // equivalent entity.
    private static final Map<Integer,String> RESERVED_CHARS = Map.of(
            (int) '&'  , "&amp;"   ,
            (int) '<'  , "&lt;"    ,
            (int) '>'  , "&gt;"    ,
            (int) '"'  , "&quot;"  ,
            (int) '\'' , "&#x27;"  ,
            (int) '/'  , "&#x2F;"  );

    // A function that takes a string and returns a sanitized version of that
    // string with the reserved-HTML and special characters replaced with their
    // equivalent entity.
    private static final UnaryOperator<String> sanitize =
            file -> file.chars().collect(StringBuilder::new,
                    (sb, c) -> sb.append(RESERVED_CHARS.getOrDefault(c, Character.toString(c))),
                    StringBuilder::append).toString();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        assert List.of("GET", "HEAD").contains(exchange.getRequestMethod());
        try (exchange) {
            discardRequestBody(exchange);
            Path path = mapToPath(exchange, root);
            if (path != null) {
                exchange.setAttribute("request-path", path.toString());  // store for OutputFilter
                if (!Files.exists(path) || !Files.isReadable(path) || isHiddenOrSymLink(path)) {
                    handleNotFound(exchange);
                } else if (exchange.getRequestMethod().equals("HEAD")) {
                    handleHEAD(exchange, path);
                } else {
                    handleGET(exchange, path);
                }
            } else {
                exchange.setAttribute("request-path", "could not resolve request URI path");
                handleNotFound(exchange);
            }
        }
    }
}
