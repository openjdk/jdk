/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8190312
 * @key intermittent
 * @summary test redirected URLs for -link
 * @library /tools/lib ../../lib /test/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox toolbox.JavacTask javadoc.tester.*
 * @build jtreg.SkippedException
 * @build jdk.test.lib.Platform jdk.test.lib.net.SimpleSSLContext jdk.test.lib.net.URIBuilder
 * @run main TestRedirectLinks
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javadoc.tester.JavadocTester;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import toolbox.JavacTask;
import toolbox.ToolBox;

import jdk.test.lib.Platform;
import jtreg.SkippedException;

public class TestRedirectLinks extends JavadocTester {
    // represents the HTTP response body length when the response contains no body
    private static final int NO_RESPONSE_BODY = -1;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String... args) throws Exception {
        if (Platform.isSlowDebugBuild()) {
            throw new SkippedException("Test is unstable with slowdebug bits");
        }
        var tester = new TestRedirectLinks();
        tester.runTests();
    }

    private ToolBox tb = new ToolBox();

    /*
     * This test requires access to a URL that is redirected
     * from http: to https:.
     * For now, we use the main JDK API on docs.oracle.com.
     * The test is skipped if access to the server is not available.
     * (A better solution is to use a local testing web server.)
     */
    @Test
    public void testRedirects() throws Exception {
        // This test relies on access to an external resource, which may or may not be
        // reliably available, depending on the host system configuration and other
        // networking issues. Therefore, it is disabled by default, unless the system
        // property "javadoc.dev" is set "true".
        String property = "javadoc.dev";
        if (!Boolean.getBoolean(property)) {
            out.println("Test case disabled by default; "
                    + "set system property \"" + property + "\" to true to enable it.");
            return;
        }

        // test to see if access to external URLs is available, and that the URL uses a redirect

        URL testURL = new URL("http://docs.oracle.com/en/java/javase/11/docs/api/element-list");
        String testURLHost = testURL.getHost();
        try {
            InetAddress testAddr = InetAddress.getByName(testURLHost);
            out.println("Found " + testURLHost + ": " + testAddr);
        } catch (UnknownHostException e) {
            out.println("Setup failed (" + testURLHost + " not found); this test skipped");
            return;
        }

        boolean haveRedirectURL = false;
        Instant start = Instant.now();
        try {
            URLConnection conn = testURL.openConnection();
            conn.connect();
            out.println("Opened connection to " + testURL);
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                int status = httpConn.getResponseCode();
                if (status / 100 == 3) {
                    haveRedirectURL = true;
                }
                out.println("Status: " + status);
                int n = 0;
                while (httpConn.getHeaderField(n) != null) {
                    out.println("Header: " + httpConn.getHeaderFieldKey(n) + ": " + httpConn.getHeaderField(n));
                    n++;
                }
                httpConn.disconnect();
            }
        } catch (Exception e) {
            out.println("Exception occurred: " + e);
            Instant now = Instant.now();
            out.println("Attempt took " + Duration.between(start, now).toSeconds() + " seconds");
        }

        if (!haveRedirectURL) {
            out.println("Setup failed (no redirect URL); this test skipped");
            return;
        }

        String apiURL = "http://docs.oracle.com/en/java/javase/11/docs/api";
        String outRedirect = "outRedirect";
        javadoc("-d", outRedirect,
                "-sourcepath", testSrc,
                "-link", apiURL,
                "-Xdoclint:none",
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/B.html", true,
                "<a href=\"" + apiURL + """
                    /java.base/java/lang/String.html" title="class or interface in java.lang" class=\
                    "external-link">Link-Plain to String Class</a>""");
        checkOutput("pkg/C.html", true,
                "<a href=\"" + apiURL + """
                    /java.base/java/lang/Object.html" title="class or interface in java.lang" class="external-link">Object</a>""");
    }

    private Path libApi = Path.of("libApi");
    private HttpServer oldServer = null;
    private HttpsServer newServer = null;

    /**
     * This test verifies redirection using temporary localhost web servers,
     * such that one server redirects to the other.
     */
    @Test
    public void testWithServers() throws Exception {
        // Set up a simple library
        Path libSrc = Path.of("libSrc");
        tb.writeJavaFiles(libSrc.resolve("mA"),
                "module mA { exports p1; exports p2; }",
                "package p1; public class C1 { }",
                "package p2; public class C2 { }");
        tb.writeJavaFiles(libSrc.resolve("mB"),
                "module mB { exports p3; exports p4; }",
                "package p3; public class C3 { }",
                "package p4; public class C4 { }");

        Path libModules = Path.of("libModules");
        Files.createDirectories(libModules);

        new JavacTask(tb)
                .outdir(libModules)
                .options("--module-source-path", libSrc.toString(),
                        "--module", "mA,mB",
                        "-Xdoclint:none")
                .run()
                .writeAll();

        javadoc("-d", libApi.toString(),
                "--module-source-path", libSrc.toString(),
                "--module", "mA,mB",
                "-Xdoclint:none" );

        // start web servers
        // use loopback address to avoid any issues if proxy is in use
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try {
            oldServer = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
            String oldURL = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(oldServer.getAddress().getPort())
                    .build().toString();
            oldServer.createContext("/", this::handleOldRequest);
            out.println("Starting old server (" + oldServer.getClass().getSimpleName() + ") on " + oldURL);
            oldServer.start();

            SSLContext sslContext = new SimpleSSLContext().get();
            if (sslContext == null) {
                throw new AssertionError("Could not create a SSLContext");
            }
            newServer = HttpsServer.create(new InetSocketAddress(loopback, 0), 0);
            String newURL = URIBuilder.newBuilder()
                    .scheme("https")
                    .loopback()
                    .port(newServer.getAddress().getPort())
                    .build().toString();
            newServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            newServer.createContext("/", this::handleNewRequest);
            out.println("Starting new server (" + newServer.getClass().getSimpleName() + ") on " + newURL);
            newServer.start();

            // Set up API to use that library
            Path src = Path.of("src");
            tb.writeJavaFiles(src.resolve("mC"),
                    "module mC { requires mA; requires mB; exports p5; exports p6; }",
                    "package p5; public class C5 extends p1.C1 { }",
                    "package p6; public class C6 { public p4.C4 c4; }");

            // Set defaults for HttpsURLConfiguration for the duration of this
            // invocation of javadoc to use our testing sslContext
            HostnameVerifier prevHostNameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
            SSLSocketFactory prevSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
            try {
                HttpsURLConnection.setDefaultHostnameVerifier((hostName, session) -> true);
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

                javadoc("-d", "api",
                        "--module-source-path", src.toString(),
                        "--module-path", libModules.toString(),
                        "-link", oldURL,
                        "--module", "mC",
                        "-Xdoclint:none");

            } finally {
                HttpsURLConnection.setDefaultHostnameVerifier(prevHostNameVerifier);
                HttpsURLConnection.setDefaultSSLSocketFactory(prevSSLSocketFactory);
            }

            // Verify the following:
            // 1: A warning about the redirection is generated.
            // 2: The contents of the redirected link were read successfully,
            //    identifying the remote API
            // 3: The original URL is still used in the generated docs, to avoid assuming
            //    that all the other files at that link have been redirected as well.
            checkOutput(Output.OUT, true,
                    "warning: URL " + oldURL + "/element-list was redirected to " + newURL + "/element-list");
            checkOutput("mC/p5/C5.html", true,
                    "extends <a href=\"" + oldURL + """
                        /mA/p1/C1.html" title="class or interface in p1" class="external-link">C1</a>""");
            checkOutput("mC/p6/C6.html", true,
                    "<a href=\"" + oldURL + """
                        /mB/p4/C4.html" title="class or interface in p4" class="external-link">C4</a>""");
        } finally {
            if (oldServer != null) {
                out.println("Stopping old server on " + oldServer.getAddress());
                oldServer.stop(0);
            }
            if (newServer != null) {
                out.println("Stopping new server on " + newServer.getAddress());
                newServer.stop(0);
            }
        }
    }

    private void handleOldRequest(HttpExchange x) throws IOException {
        out.println("old request: "
                + x.getProtocol() + " "
                + x.getRequestMethod() + " "
                + x.getRequestURI());
        String newProtocol = (newServer instanceof HttpsServer) ? "https" : "http";
        String redirectTo;
        try {
            redirectTo = URIBuilder.newBuilder().scheme(newProtocol)
                    .host(newServer.getAddress().getAddress())
                    .port(newServer.getAddress().getPort())
                    .path(x.getRequestURI().getPath())
                    .build().toString();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        out.println("    redirect to: " + redirectTo);
        x.getResponseHeaders().add("Location", redirectTo);
        x.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_PERM, NO_RESPONSE_BODY);
        x.getResponseBody().close();
    }

    private void handleNewRequest(HttpExchange x) throws IOException {
        out.println("new request: "
                + x.getProtocol() + " "
                + x.getRequestMethod() + " "
                + x.getRequestURI());
        Path file = libApi.resolve(x.getRequestURI().getPath().substring(1).replace('/', File.separatorChar));
        System.err.println(file);
        if (Files.exists(file)) {
            byte[] bytes = Files.readAllBytes(file);
            // in the context of this test, the only request should be element-list,
            // which we can say is text/plain.
            x.getResponseHeaders().add("Content-type", "text/plain");
            x.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
            try (OutputStream responseStream = x.getResponseBody()) {
                responseStream.write(bytes);
            }
        } else {
            x.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, NO_RESPONSE_BODY);
            x.getResponseBody().close();
        }
    }
}
