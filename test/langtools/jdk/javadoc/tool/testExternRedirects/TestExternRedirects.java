/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     8299627
 * @summary Fix/improve handling of "missing" element-list file
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build   toolbox.ToolBox javadoc.tester.*
 * @run main TestExternRedirects
 */

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestExternRedirects extends JavadocTester {
    public static void main(String... args) throws Exception {
        var tester = new TestExternRedirects();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();
    private HttpServer httpServer;

    @Override
    public void runTests() throws Exception {
        httpServer = startServer();
        var address = httpServer.getAddress();
        out.println("server running at " + address);
        try {
            super.runTests();
        } finally {
            httpServer.stop(0);
        }
    }

    private HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/docs", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        return server;
    }

    private class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            out.println("MyHandler: " + t.getRequestMethod() + " " + t.getRequestURI());
            assert t.getRequestMethod().equals("GET");
            var uriPath = t.getRequestURI().getPath();
            if (uriPath.contains("no-redirect")) {
                respond(t, HttpURLConnection.HTTP_OK, "");
            } else if (uriPath.matches(".*/redirect-[1-9]/.*")) {
                Matcher m = Pattern.compile("redirect-([1-9])").matcher(uriPath);
                if (m.find()) {
                    var count = Integer.parseInt(m.group(1));
                    var u = t.getRequestURI().toString();
                    var u2 = u.replace("redirect-" + count,
                            (count == 1) ? "no-redirect" : "redirect-" + (count - 1));
                    t.getResponseHeaders().add("Location", u2);
                    respond(t, HttpURLConnection.HTTP_MOVED_PERM, "");
                } else {
                    throw new IOException("internal error");
                }
            } else if (uriPath.contains("bad-redirect")){
                var u = t.getRequestURI().toString();
                var u2 = u.replace("bad-redirect", "no-redirect")
                                .replaceAll("[^/]+-list$", "not-found.html");
                t.getResponseHeaders().add("Location", u2);
                respond(t, HttpURLConnection.HTTP_MOVED_PERM, "");
            } else {
                respond(t, HttpURLConnection.HTTP_NOT_FOUND, "");
            }
        }
    }

    private void respond(HttpExchange t, int code, String body) throws IOException {
        out.println("  respond: " + code);
        t.getResponseHeaders().forEach((k, v) -> out.println("  header: " + k + ": " + v));
        body.lines().map(l -> "  body " + l).forEach(out::println);

        t.sendResponseHeaders(code, body.length());
        try (var os = t.getResponseBody()) {
            os.write(body.getBytes());
        }
    }

    @Test
    public void testNoRedirect(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:none",
                "-link", getURL("no-redirect").toString(),
                "p");
        checkExit(Exit.OK);
    }

    @Test
    public void testSomeRedirect(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:none",
                "-link", getURL("redirect-3").toString(),
                "p");
        checkExit(Exit.OK);

        new OutputChecker(Output.OUT)
                .check(Pattern.compile("warning: URL .*/docs/redirect-3/element-list" +
                        " was redirected to .*/docs/no-redirect/element-list"));
    }

    @Test
    public void testBadRedirect(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package p; public class C { }");

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "-Xdoclint:none",
                "-link", getURL("bad-redirect").toString(),
                "p");
        checkExit(Exit.ERROR);

        checkOutput(Output.OUT, true,
                "error: Unexpected redirection for URL");
    }

    private URL getURL(String key) throws URISyntaxException, MalformedURLException {
        return new URI("http",
                null,  // user-info
                httpServer.getAddress().getHostName(),
                httpServer.getAddress().getPort(),
                "/docs/" + key,
                null, // query
                null // fragment
                ).toURL();
    }
}
