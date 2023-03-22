/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8294241
 * @library /test/lib
 * @modules java.base/java.net:+open
 * @summary Test URL::fromURI(URI, URLStreamHandler)
 * @run junit/othervm URLFromURITest
 */

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class URLFromURITest {

    static final Random RAND = RandomFactory.getRandom();

    record TestInput(String uri, URLStreamHandler handler) {
        static TestInput withNoHandler(String uri) {
            return new TestInput(uri, null);
        }
        TestInput withCustomHandler() {
            return new TestInput(uri(), new CustomStreamHandler());
        }
        TestInput withUrlPrefix() {return inputWithUrlPrefix(this);}
    }

    static URI uriWithUrlPrefix(URI uri) {
        return URI.create(stringWithUrlPrefix(uri.toString()));
    }

    static String stringWithUrlPrefix(String uriStr) {
        if (uriStr.regionMatches(true, 0, "url:", 0, 4)) return uriStr;
        return RAND.nextBoolean() ? "url:" + uriStr : "Url:" + uriStr;
    }

    static TestInput inputWithUrlPrefix(TestInput input) {
        String uriStr = input.uri();
        var handler = input.handler();

        var urlUriStr = stringWithUrlPrefix(uriStr);
        if (uriStr.equals(urlUriStr)) return null;

        var urlURI = URI.create(urlUriStr);
        try {
            new URL(null, urlURI.toString(), handler);
        } catch (Throwable t) {
            System.err.println("skipping new URL(null, \"" + urlURI + "\", handler): " + t);
            return null;
        }
        return new TestInput(urlUriStr, handler);
    }

    static Stream<String> uris() {
        var uris = Stream.of(
                "http://jag:cafebabe@java.sun.com:94/b/c/d?q#g",
                "http://[1080:0:0:0:8:800:200C:417A]/index.html",
                "http://a/b/c/d;p?q",
                "mailto:mduerst@ifi.unizh.ch",
                "http:comp.infosystems.www.servers.unix",
                "http://j%41g:cafeb%41be@java.sun.com:94/%41/b/c/d?q#g",
                "jar:file:///x.jar!/",
                "jmod:/java.base",
                "jmod:///java.base");

        if (hasFtp()) {
            uris = Stream.concat(uris,
                    Stream.of("ftp://ftp.is.co.za/rfc/rfc1808.txt"));
        }

        return uris;
    }

    static Stream<String> nonOverridableUris() {
        return Stream.of("file:///nohost/%41/",
                "file://with.host/%41/",
                "file:/x/y/z",
                "jrt:/java.base/java/lang/Integer.class",
                "jrt:///java.base/java/lang/Integer.class");
    }
    static Stream<TestInput> withNoHandler() {
        return Stream.concat(uris(), nonOverridableUris())
                .map(TestInput::withNoHandler);
    }

    static Stream<TestInput> withCustomHandler() {
        var withHandlers = uris()
                .map(TestInput::withNoHandler)
                .map(TestInput::withCustomHandler);
        return Stream.concat(withHandlers, Stream.of(
                new TestInput("foo:bar:baz", new CustomStreamHandler()),
                new TestInput("jar:file:///x.jar!/", new CustomStreamHandler()),
                new TestInput("jar:jar:file///x.jar!/bing", new CustomStreamHandler()),
                new TestInput("blah://localhost:80/x/y/z", new CustomStreamHandler())
        ));
    }

    static Stream<TestInput> overridingNonOverridable() {
        return nonOverridableUris().map(TestInput::withNoHandler)
                .map(TestInput::withCustomHandler);
    }

    @Test
    public void checkExceptions() {
        var noscheme = URI.create("http");
        var unknown = URI.create("unknown:///foo/bar");
        var opaque = URI.create("opaque:opaque-path");
        var jrt = URI.create("jrt:/java.base/java.lang.Integer.class");
        var file = URI.create("file:/");
        var unoscheme = uriWithUrlPrefix(noscheme);
        var uunknown = uriWithUrlPrefix(unknown);
        var uopaque = uriWithUrlPrefix(opaque);
        var ujrt = uriWithUrlPrefix(jrt);
        var ufile = uriWithUrlPrefix(file);
        var handler = new CustomStreamHandler();
        assertThrows(NullPointerException.class, () -> URL.of(null, null));
        assertThrows(NullPointerException.class, () -> URL.of(null, handler));
        assertThrows(IllegalArgumentException.class, () -> URL.of(noscheme, null));
        assertThrows(IllegalArgumentException.class, () -> URL.of(noscheme, handler));
        assertThrows(IllegalArgumentException.class, () -> URL.of(jrt, handler));
        assertThrows(IllegalArgumentException.class, () -> URL.of(file, handler));
        assertThrows(IllegalArgumentException.class, () -> URL.of(ujrt, handler));
        assertThrows(IllegalArgumentException.class, () -> URL.of(ufile, handler));
        assertThrows(MalformedURLException.class, () -> URL.of(unknown, null));
        assertThrows(MalformedURLException.class, () -> URL.of(opaque, null));
        assertThrows(MalformedURLException.class, () -> URL.of(uunknown, null));
        assertThrows(MalformedURLException.class, () -> URL.of(uopaque, null));
        assertThrows(MalformedURLException.class, () -> URL.of(unoscheme, null));
        assertThrows(MalformedURLException.class, () -> URL.of(unoscheme, handler));
    }

    @ParameterizedTest
    @MethodSource(value = "withNoHandler")
    public void testWithNoHandler(TestInput input) throws Exception {
        String uriStr = input.uri();
        URLStreamHandler handler = input.handler();
        System.err.println("testWithNoHandler: " + uriStr);
        assertNull(handler, input + ": input handler");
        URI uri = new URI(uriStr);
        URL url = URL.of(uri, handler);
        checkNoHandler(input, uri, url);
        var urlInput = input.withUrlPrefix();
        if (urlInput != null) {
            try {
                var urlURI = URI.create(input.uri());
                checkNoHandler(urlInput, uri, URL.of(urlURI, null));
            } catch (Throwable x) {
                throw new AssertionError("Failed: " + urlInput.uri() + " with: " + x, x);
            }
        }
    }

    private void checkNoHandler(TestInput input, URI uri, URL url) throws Exception {
        System.err.println("Testing: " + uri);
        checkURL(input, uri, url);
        URLStreamHandler urlHandler = URLAccess.getHandler(url);
        assertNotNull(urlHandler, input + ": URL.handler");
        assertNull(urlHandler.getClass().getClassLoader(),
                input + ": URL.handler class loader");
    }

    @ParameterizedTest
    @MethodSource(value = "withCustomHandler")
    public void checkCustomHandler(TestInput input) throws Exception {
        String uriStr = input.uri();
        URLStreamHandler handler = input.handler();
        System.err.println("testWithCustomHandler: " + input);
        assertNotNull(handler, input + ": input handler");
        URI uri = new URI(uriStr);
        URL url = URL.of(uri, handler);
        checkCustomHandler(input, uri, url, handler);
        var urlInput = input.withUrlPrefix();
        if (urlInput != null) {
            urlInput = urlInput.withCustomHandler();
            handler = urlInput.handler();
            try {
                var urlURI = URI.create(urlInput.uri());
                checkCustomHandler(urlInput, uri, URL.of(urlURI, handler), handler);
            } catch (Throwable x) {
                throw new AssertionError("Failed with handler: " + urlInput.uri() + " with: " + x, x);
            }
        }
    }

    private void checkCustomHandler(TestInput input, URI uri, URL url,
                                    URLStreamHandler handler) throws Exception {
        System.err.println("Testing: " + uri);
        checkURL(input, uri, url);
        URLStreamHandler urlHandler = URLAccess.getHandler(url);
        assertSame(handler, urlHandler, input + ": URL.handler");
        URLConnection c = url.openConnection();
        assertNotNull(c, input + ": opened connection");
        assertEquals(CustomURLConnection.class, c.getClass(),
                input + ": connection class");
        assertEquals(CustomStreamHandler.class, urlHandler.getClass(),
                input + ": handler class");
        assertEquals(((CustomURLConnection)c).handler, handler, input + ": handler");
        assertEquals(c.getURL(), url, input + ": connection url");
        var customHandler = (CustomStreamHandler)urlHandler;
        assertEquals(customHandler.parseURLCalled(), 1, "parseURL calls");
    }

    @ParameterizedTest
    @MethodSource(value = "overridingNonOverridable")
    public void testOverridingNonOverridable(TestInput input) throws Exception {
        String uriStr = input.uri();
        URLStreamHandler handler = input.handler();
        System.err.println("testOverridingNonOverridable: " + input);
        assertNotNull(handler, input + ": input handler");
        URI uri = new URI(uriStr);
        try {
            URL url = URL.of(uri, handler);
            throw new AssertionError("Should not be able to specify handler for: " + uriStr);
        } catch (IllegalArgumentException x) {
            System.err.println("Got expected exception: " + x);
        }
    }

    private static boolean isFileBased(URI uri) {
        String scheme = uri.getScheme();
        boolean isJrt = "jrt".equals(scheme.toLowerCase(Locale.ROOT));
        boolean isJmod = "jmod".equals(scheme.toLowerCase(Locale.ROOT));
        boolean isFile = "file".equals(scheme.toLowerCase(Locale.ROOT));
        return isJmod || isJrt || isFile;
    }

    private static void checkURL(TestInput input, URI uri, URL url) throws MalformedURLException {
        String scheme = uri.getScheme();
        assertEquals(scheme, url.getProtocol(), input + ": scheme");

        if (uri.isOpaque()) {
            String ssp = uri.getSchemeSpecificPart();
            assertEquals(ssp, url.getPath(), input + ": ssp");
        } else {
            String authority = uri.getRawAuthority();
            boolean isHierarchical = uri.toString().startsWith(scheme + "://");
            boolean isFileBased = isFileBased(uri);

            // Network based URLs usually follow URI, but file based
            // protocol handlers have a few discrepancies in how they
            // treat an absent authority:
            // - URI authority is null if there is no authority, always
            // - URL authority is null or empty depending on the protocol
            //   and on whether the URL is hierarchical or not.
            if (isFileBased && authority == null) {
                // jrt: takes a fastpath - so that jrt:/ is equivalent to jrt:///
                if (scheme.equals("jrt")) {
                    authority = "";
                }
                if (isHierarchical) {
                    authority = "";
                }
            }
            assertEquals(authority, url.getAuthority(), input + ": authority");

            // Network based URLs usually follow URI, but file based
            // protocol handlers have a few discrepancies in how they
            // treat an absent host:
            String host = uri.getHost();
            if (isFileBased && host == null) {
                host = "";
            }

            assertEquals(host, url.getHost(), input + ": host");
            if (host != null) {
                String userInfo = uri.getRawUserInfo();
                assertEquals(userInfo, url.getUserInfo(), input + ": userInfo");
                assertEquals(uri.getPort(), url.getPort(), input + ": port");
            }

            String path = uri.getRawPath();
            assertEquals(path, url.getPath(), input + ": path");

            String query = uri.getQuery();
            assertEquals(query, url.getQuery(), input + ": query");
        }
        String frag = uri.getRawFragment();
        assertEquals(frag, url.getRef(), input + ": fragment");
    }

    @SuppressWarnings("deprecation")
    private static boolean hasFtp() {
        try {
            return new java.net.URL("ftp://localhost/") != null;
        } catch (java.net.MalformedURLException x) {
            System.err.println("FTP not supported by this runtime.");
            return false;
        }
    }

    static class CustomURLConnection extends URLConnection {

        public final CustomStreamHandler handler;
        CustomURLConnection(CustomStreamHandler handler, URL url) {
            super(url);
            this.handler = handler;
        }

        @Override
        public void connect() throws IOException {

        }
    }
    static class CustomStreamHandler extends URLStreamHandler {

        final AtomicInteger parseURLCalled = new AtomicInteger();

        @Override
        protected void parseURL(URL u, String spec, int start, int limit) {
            parseURLCalled.incrementAndGet();
            super.parseURL(u, spec, start, limit);
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new CustomURLConnection(this, u);
        }

        public int parseURLCalled() {
            return parseURLCalled.get();
        }
    }

    static final class URLAccess {
        static final VarHandle HANDLER;
        static {
            try {
                Lookup lookup = MethodHandles.privateLookupIn(URL.class, MethodHandles.lookup());
                HANDLER = lookup.findVarHandle(URL.class, "handler", URLStreamHandler.class);
            } catch (Exception x) {
                throw new ExceptionInInitializerError(x);
            }
        }
        static URLStreamHandler getHandler(URL url) {
            return (URLStreamHandler)HANDLER.get(url);
        }
    }
}
