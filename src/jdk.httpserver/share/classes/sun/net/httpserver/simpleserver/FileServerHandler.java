/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpHandlers;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_CHUNKED;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_EMPTY;

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
    private static final String FAVICON_RESOURCE_PATH =
            "/sun/net/httpserver/simpleserver/resources/favicon.ico";
    private static final String FAVICON_LAST_MODIFIED = "Mon, 23 May 1995 11:11:11 GMT";
    private static final int MAX_RANGES = 32;

    private final Path root;
    private final UnaryOperator<String> mimeTable;
    private final Logger logger;

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
        this.root = root;
        this.mimeTable = mimeTable;
        this.logger = System.getLogger("com.sun.net.httpserver");
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
        boolean requestURIEndsWithSlash = pathEndsWithSlash(exchange);
        if (Files.isDirectory(path)) {
            if (!requestURIEndsWithSlash) {
                handleMovedPermanently(exchange);
                return;
            }
            Path indexFile = indexFile(path);
            if (indexFile != null) {
                serveFile(exchange, indexFile, writeBody);
            } else {
                listFiles(exchange, path, writeBody);
            }
        }
        // Disallow non-directory paths ending with slash
        else if (requestURIEndsWithSlash) {
            handleNotFound(exchange);
        } else {
            serveFile(exchange, path, writeBody);
        }
    }

    private void handleMovedPermanently(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", getRedirectURI(exchange.getRequestURI()));
        exchange.sendResponseHeaders(301, RSPBODY_EMPTY);
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
            exchange.sendResponseHeaders(404, RSPBODY_EMPTY);
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

    private static boolean pathEndsWithSlash(HttpExchange exchange) {
        return exchange.getRequestURI().getPath().endsWith("/");
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

    private static boolean isFavIconRequest(HttpExchange exchange) {
        return "/favicon.ico".equals(exchange.getRequestURI().getPath());
    }

    private void serveDefaultFavIcon(HttpExchange exchange, boolean writeBody)
            throws IOException
    {
        var respHdrs = exchange.getResponseHeaders();
        try (var stream = getClass().getModule().getResourceAsStream(FAVICON_RESOURCE_PATH)) {
            var bytes = stream.readAllBytes();
            respHdrs.set("Content-Type", "image/x-icon");
            respHdrs.set("Last-Modified", FAVICON_LAST_MODIFIED);
            if (writeBody) {
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                respHdrs.set("Content-Length", Integer.toString(bytes.length));
                exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
            }
        }
    }

    private static final String rangePartHeader =
            """
            --%s\r
            Content-Type: %s\r
            Content-Range: bytes %s-%s/%s\r
            \r
            """;

    private void serveFile(HttpExchange exchange, Path path, boolean writeBody)
        throws IOException
    {
        var respHdrs = exchange.getResponseHeaders();
        String contentType = mediaType(path.toString());

        respHdrs.set("Content-Type", contentType);  // may be overridden by multipart/byteranges
        respHdrs.set("Last-Modified", getLastModified(path));
        respHdrs.set("Accept-Ranges", "bytes");

        long fileSize = Files.size(path);
        if (!writeBody) {
            respHdrs.set("Content-Length", Long.toString(fileSize));
            exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
            return;
        }

        if (fileSize == 0) {
            respHdrs.set("Content-Length", "0");
            exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
            return;
        }

        String hdrRange = exchange.getRequestHeaders().getFirst("Range");
        String hdrIfRange = exchange.getRequestHeaders().getFirst("If-Range");
        if (hdrRange == null
                || hdrIfRange != null) {  // Conditional range requests are not supported.
            sendFullFile(exchange, path, fileSize);
            return;
        }

        Range[] ranges = parseRanges(hdrRange, fileSize);
        if (ranges == null) {  // No Range header, or invalid/unsupported Range header
            sendFullFile(exchange, path, fileSize);
            return;
        }

        // Valid Range header, but no satisfiable ranges
        if (ranges.length == 0) {
            sendRangeNotSatisfiable(exchange, fileSize);
            return;
        }

        // Single range.
        if (ranges.length == 1) {
            Range range = ranges[0];
            respHdrs.set("Content-Range",
                    "bytes %s-%s/%s".formatted(range.first(), range.last(), fileSize));
            exchange.sendResponseHeaders(206, ranges[0].length());
            try (SeekableByteChannel ch = Files.newByteChannel(path);
                 OutputStream os = exchange.getResponseBody()) {
                sendFileRanged(os, ch, range);
            }
            return;
        }

        // Multiple ranges, send as multipart/byteranges
        String boundary = "range_" + UUID.randomUUID();
        respHdrs.set("Content-Type", "multipart/byteranges; boundary=" + boundary);

        exchange.sendResponseHeaders(206, RSPBODY_CHUNKED);
        try (SeekableByteChannel ch = Files.newByteChannel(path);
             OutputStream os = exchange.getResponseBody()) {
            for (Range range : ranges) {
                os.write(rangePartHeader.formatted(
                        boundary,
                        contentType,
                        range.first(),
                        range.last(),
                        fileSize
                ).getBytes(US_ASCII));

                sendFileRanged(os, ch, range);

                os.write("\r\n".getBytes(US_ASCII));
            }

            // end of multipart/byteranges
            os.write(("--%s--\r\n".formatted(boundary)).getBytes(US_ASCII));
        }
    }

    private static void sendFullFile(HttpExchange exchange, Path path, long fileSize)
            throws IOException {
        exchange.sendResponseHeaders(200, fileSize);
        try (InputStream is = Files.newInputStream(path);
             OutputStream os = exchange.getResponseBody()) {
            is.transferTo(os);
        }
    }

    private static void sendRangeNotSatisfiable(HttpExchange exchange, long fileSize)
            throws IOException {
        var respHdrs = exchange.getResponseHeaders();
        respHdrs.remove("Content-Type");  // no body, so no content type
        respHdrs.set("Content-Range", "bytes */" + fileSize);
        exchange.sendResponseHeaders(416, RSPBODY_EMPTY);
    }

    private static void sendFileRanged(OutputStream os, SeekableByteChannel ch, Range range)
            throws IOException {
        ch.position(range.first());
        long remaining = range.length();
        ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
        while (remaining > 0) {
            buffer.clear();
            if (remaining < buffer.capacity()) {
                buffer.limit((int) remaining);
            }

            int bytesRead = ch.read(buffer);
            if (bytesRead == -1) {
                throw new EOFException("Unexpected end of file while reading range");
            }

            os.write(buffer.array(), 0, bytesRead);
            remaining -= bytesRead;
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
            exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
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
    private static final Map<Integer, String> RESERVED_CHARS = Map.of(
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
            boolean isHeadRequest = exchange.getRequestMethod().equals("HEAD");
            Path path = mapToPath(exchange, root);
            if (path != null) {
                exchange.setAttribute("request-path", path.toString());  // store for OutputFilter
                if (!Files.exists(path) || !Files.isReadable(path) || isHiddenOrSymLink(path)) {
                    handleNotFound(exchange);
                } else if (isHeadRequest) {
                    handleHEAD(exchange, path);
                } else {
                    handleGET(exchange, path);
                }
            } else {
                if (isFavIconRequest(exchange)) {
                    try {
                        serveDefaultFavIcon(exchange, !isHeadRequest);
                        return;
                    } catch (IOException ignore) {
                        // fall through to send the not-found response
                    }
                }
                exchange.setAttribute("request-path", "could not resolve request URI path");
                handleNotFound(exchange);
            }
        }
    }

    /**
     * Parses the Range header value and returns an array of Range objects.
     *
     * Examples of valid Range header values:
     * <ul>
     *   <li>bytes=0-499: first 500 bytes</li>
     *   <li>bytes=500-999: second 500 bytes</li>
     *   <li>bytes=-500: last 500 bytes</li>
     *   <li>bytes=500-: from byte 500 to end</li>
     * </ul>
     * Multiple ranges are sorted, and overlapping or adjacent ranges are merged:
     * <ul>
     *   <li>bytes=0-499,500-999: first 1000 bytes</li>
     *   <li>bytes=500-999,0-499: first 1000 bytes</li>
     *   <li>bytes=0-499,400-599: first 600 bytes</li>
     * </ul>
     * Requests containing an excessive number of range specs are rejected.
     *
     * @param header the value of the Range header
     * @param fileSize the size of the file being requested
     * @return {@code null} if the Range header is absent, invalid, or unsupported.
     *         An empty array if the request cannot be satisfied or is rejected.
     *         Otherwise, returns normalised byte ranges.
     */
    public static Range[] parseRanges(String header, long fileSize) {
        if (header == null) {
            return null;
        }
        if (fileSize == 0) {
            return null;
        }

        String unit = "bytes=";  // Only bytes ranges are supported
        if (!header.regionMatches(true, 0, unit, 0, unit.length())) {
            return null;
        }

        String rangeSet = header.substring(unit.length()).trim();
        if (rangeSet.isEmpty()) {
            return null;
        }

        List<Range> ranges = new ArrayList<>();
        String[] specs = rangeSet.split(",", MAX_RANGES + 1);
        if (specs.length > MAX_RANGES) {
            return new Range[0];  // Not satisfiable: too many ranges.
        }
        for (String spec : specs) {
            Range range = parseRange(spec, fileSize);
            if (range == null) {
                return null;  // invalid range spec
            }

            if (range.length() != 0) {
                ranges.add(range);
            }
        }

        return mergeOverlappingRanges(ranges);
    }

    private static Range[] mergeOverlappingRanges(List<Range> ranges) {
        List<Range> mergedRanges = new ArrayList<>();

        ranges.sort(Comparator.comparingLong(Range::first));
        for (Range range : ranges) {
            if (mergedRanges.isEmpty()) {
                mergedRanges.add(range);
            } else {
                Range last = mergedRanges.getLast();
                if (range.first() <= last.last() + 1) {
                    mergedRanges.set(mergedRanges.size() - 1,
                            new Range(last.first(), Math.max(last.last(), range.last())));
                } else {
                    mergedRanges.add(range);
                }
            }
        }

        return mergedRanges.toArray(new Range[0]);
    }

    private static Range parseRange(String range, long fileSize) {
        String[] parts = range.trim().split("-", -1);
        if (parts.length != 2) {
            return null;  // invalid range spec
        }

        try {
            if (parts[0].isEmpty()) {
                if (parts[1].isEmpty()) {
                    return null;  // bytes=- (invalid)
                }

                // bytes=-N
                if (parts[1].charAt(0) == '+') {
                    // Long.parseLong accepts a leading '+' sign, but this is not valid in a Range header.
                    return null;
                }
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength == 0) {
                    return Range.EMPTY;
                }

                long first = Math.max(0, fileSize - suffixLength);
                long last = fileSize - 1;

                return new Range(first, last);
            }

            if (parts[0].charAt(0) == '+') {
                return null;
            }
            long first = Long.parseLong(parts[0]);
            if (first < 0) {
                return null;
            }

            long last;
            if (parts[1].isEmpty()) {
                // bytes=n-
                last = fileSize - 1;
            } else {
                // bytes=n-N
                if (parts[1].charAt(0) == '+') {
                    return null;
                }
                last = Long.parseLong(parts[1]);
                if (last < first) {
                    return null;
                }
            }

            if (first >= fileSize) {
                return Range.EMPTY;
            }

            return new Range(first, Math.min(last, fileSize - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Range(long first, long last) {
        static final Range EMPTY = new Range(0, -1);

        long length() {
            return last - first + 1;
        }
    }
}
