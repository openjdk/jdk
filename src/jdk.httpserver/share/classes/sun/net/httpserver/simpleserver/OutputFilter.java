/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A Filter that outputs log messages about an HttpExchange. The implementation
 * uses a {@link Filter#afterHandler(String, Consumer) post-processing filter}.
 *
 * <p> If the outputLevel is INFO, the format is based on the
 * <a href='https://www.w3.org/Daemon/User/Config/Logging.html#common-logfile-format'>Common Logfile Format</a>.
 * In this case the output includes the following information about an exchange:
 *
 * <p> remotehost rfc931 authuser [date] "request line" status bytes
 *
 * <p> Example:
 * 127.0.0.1 - - [22/Jun/2000:13:55:36 -0700] "GET /example.txt HTTP/1.1" 200 -
 *
 * <p> The fields rfc931, authuser and bytes are not captured in the implementation
 * and are always represented as '-'.
 *
 * <p> If the outputLevel is VERBOSE, the output additionally includes the
 * absolute path of the resource requested, if it has been
 * {@linkplain HttpExchange#setAttribute(String, Object) provided} via the
 * attribute {@code "request-path"}, as well as the request and response headers
 * of the exchange.
 */
public final class OutputFilter extends Filter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");
    private final PrintStream printStream;
    private final OutputLevel outputLevel;
    private final Filter filter;

    private OutputFilter(OutputStream os, OutputLevel outputLevel) {
        printStream = new PrintStream(os, true, UTF_8);
        this.outputLevel = outputLevel;
        var description = "HttpExchange OutputFilter (outputLevel: " + outputLevel + ")";
        this.filter = Filter.afterHandler(description, operation());
    }

    public static OutputFilter create(OutputStream os, OutputLevel outputLevel) {
        if (outputLevel.equals(OutputLevel.NONE)) {
            throw new IllegalArgumentException("Not a valid outputLevel: " + outputLevel);
        }
        return new OutputFilter(os, outputLevel);
    }

    private Consumer<HttpExchange> operation() {
        return e -> {
            String s = e.getRemoteAddress().getHostString() + " "
                    + "- - "    // rfc931 and authuser
                    + "[" + OffsetDateTime.now().format(FORMATTER) + "] "
                    + "\"" + e.getRequestMethod() + " " + e.getRequestURI() + " " + e.getProtocol() + "\" "
                    + e.getResponseCode() + " -";    // bytes
            printStream.println(s);

            if (outputLevel.equals(OutputLevel.VERBOSE)) {
                if (e.getAttribute("request-path") instanceof String requestPath) {
                    printStream.println("Resource requested: " + requestPath);
                }
                logHeaders(">", e.getRequestHeaders());
                logHeaders("<", e.getResponseHeaders());
            }
        };
    }

    private void logHeaders(String sign, Headers headers) {
        headers.forEach((name, values) -> {
            var sb = new StringBuilder();
            var it = values.iterator();
            while (it.hasNext()) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            printStream.println(sign + " " + name + ": " + sb);
        });
        printStream.println(sign);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        try  {
            filter.doFilter(exchange, chain);
        } catch (Throwable t) {
            if (!outputLevel.equals(OutputLevel.NONE)) {
                reportError(ResourceBundleHelper.getMessage("err.server.handle.failed",
                        t.getMessage()));
            }
            throw t;
        }
    }

    @Override
    public String description() { return filter.description(); }

    private void reportError(String message) {
        printStream.println(ResourceBundleHelper.getMessage("error.prefix") + " " + message);
    }
}
