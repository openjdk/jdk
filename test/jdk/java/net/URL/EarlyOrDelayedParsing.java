/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293590
 * @summary URL built-in protocol handlers should parse the URL early
 *          to avoid constructing URLs for which openConnection
 *          would later throw an exception, when possible.
 *          A jdk.net.url.delayParsing property allows to switch that
 *          behavior off to mitigate risks of regression
 * @run junit  EarlyOrDelayedParsing
 * @run junit/othervm -Djdk.net.url.delayParsing EarlyOrDelayedParsing
 * @run junit/othervm -Djdk.net.url.delayParsing=true EarlyOrDelayedParsing
 * @run junit/othervm -Djdk.net.url.delayParsing=false EarlyOrDelayedParsing
 */

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.System.err;
import static org.junit.jupiter.api.Assertions.*;

public class EarlyOrDelayedParsing {

    public final boolean EARLY_PARSING;
    {
        String value = System.getProperty("jdk.net.url.delayParsing", "false");
        EARLY_PARSING = !value.isEmpty() && !Boolean.parseBoolean(value);
        if (!EARLY_PARSING) {
            // we will open the connection in that case.
            // make sure no proxy is selected
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(Proxy.NO_PROXY);
                }
                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                }
            });
        }
    }

    // Some characters that when included at the wrong place
    // in the authority component, without being escaped, would
    // cause an exception.
    private static final String EXCLUDED_DELIMS = "<>\" ";
    private static final String UNWISE = "{}|\\^`";
    private static final String DELIMS = "[]/?#@";

    // Test data used to test exceptions thrown by URL
    // at some point, when constructed with some illegal input.
    sealed interface URLArgTest
            permits OneArgTest, TwoArgsTest, ThreeArgsTest, FourArgsTest {

        // Some character that is expected to cause an exception
        // at some point, and which this test case is built for
        int character();

        // An URL string containing the illegal character
        String url();

        // Some characters are already checked at construction
        // time. They will cause an exception to be thrown,
        // whether delayed parsing is activated or not.
        // This method returns true if an exception is
        // expected at construction time for this test case,
        // even when delayed parsing is activated.
        boolean early(int c);

        // The URL scheme this test case is built for.
        // Typically, one of "http", "https", "ftp"...
        default String scheme() {
            return scheme(url());
        }

        // Return the URL string of this test case, after
        // substituting its scheme with the given scheme.
        default String urlWithScheme(String scheme) {
            String url = url();
            int colon = url.indexOf(':');
            String urlWithScheme = scheme + url.substring(colon);
            return urlWithScheme;
        }

        // Which exception to expect when parsing is delayed
        default boolean acceptDelayedException(Throwable exception) {
            return exception instanceof MalformedURLException
                    || exception instanceof UnknownHostException;
        }

        default String describe() {
            return this.getClass().getSimpleName() + "(url=" + url() + ")";
        }

        static int port(String protocol) {
            return switch (protocol) {
                case "http" -> 80;
                case "https" -> 443;
                case "ftp" -> 21;
                default -> -1;
            };
        }

        static String scheme(String url) {
            return url.substring(0, url.indexOf(':'));
        }
    }

    // Test data for the one arg constructor
    // public URL(String spec) throws MalformedURLException
    sealed interface OneArgTest extends URLArgTest {

        // Create a new test case identical to this one but
        // with a different URL scheme
        default OneArgTest withScheme(String scheme) {
            String urlWithScheme = urlWithScheme(scheme);
            if (this instanceof OfHost) {
                return new OfHost(character(), urlWithScheme);
            }
            if (this instanceof OfUserInfo) {
                return new OfUserInfo(character(), urlWithScheme);
            }
            throw new AssertionError("unexpected subclass: " + this.getClass());
        }

        @Override
        default boolean early(int c) {
            return this instanceof OfHost &&
                    (c < 31 || c == 127);
        }

        @Override
        default boolean acceptDelayedException(Throwable exception) {
            return URLArgTest.super.acceptDelayedException(exception)
                    || "file".equalsIgnoreCase(scheme())
                    && character() == '\\'
                    && exception instanceof IOException;
        }

        record OfHost(int character, String url) implements OneArgTest { }
        record OfUserInfo(int character, String url) implements OneArgTest { }

        static OneArgTest ofHost(int c) {
            return new OfHost(c, "http://local%shost/".formatted(Character.toString(c)));
        }
        static OneArgTest ofUserInfo(int c) {
            return new OfUserInfo(c, "http://user%sinfo@localhost:9999/".formatted(Character.toString(c)));
        }
    }

    // Test data for the two arg constructor
    // public URL(URL context, String spec) throws MalformedURLException
    sealed interface TwoArgsTest extends URLArgTest {

        // Create a new test case identical to this one but
        // with a different URL scheme
        default TwoArgsTest withScheme(String scheme) {
            String urlWithScheme = urlWithScheme(scheme);
            if (this instanceof OfTwoArgsHost) {
                return new OfTwoArgsHost(character(), urlWithScheme);
            }
            if (this instanceof OfTwoArgsUserInfo) {
                return new OfTwoArgsUserInfo(character(), urlWithScheme);
            }
            throw new AssertionError("unexpected subclass: " + this.getClass());
        }

        @Override
        default boolean early(int c) {
            return this instanceof OfTwoArgsHost &&
                    (c < 31 || c == 127);
        }

        @Override
        default boolean acceptDelayedException(Throwable exception) {
            return URLArgTest.super.acceptDelayedException(exception)
                    || "file".equalsIgnoreCase(scheme())
                    && character() == '\\'
                    && exception instanceof IOException;
        }

        record OfTwoArgsHost(int character, String url) implements TwoArgsTest { }
        record OfTwoArgsUserInfo(int character, String url) implements TwoArgsTest { }

        static TwoArgsTest ofHost(int c) {
            return new OfTwoArgsHost(c, "http://local%shost/".formatted(Character.toString(c)));
        }
        static TwoArgsTest ofUserInfo(int c) {
            return new OfTwoArgsUserInfo(c, "http://user%sinfo@localhost:9999/".formatted(Character.toString(c)));
        }
        static TwoArgsTest ofOneArgTest(OneArgTest test) {
            if (test instanceof OneArgTest.OfHost) {
                return ofHost(test.character());
            } else if (test instanceof OneArgTest.OfUserInfo) {
                return ofUserInfo(test.character());
            }
            throw new AssertionError("can't convert to TwoArgsTest: "
                    + test.getClass());
        }
    }


    // Test data for the three args constructor
    // public URL(String scheme, String host, String file)
    //     throws MalformedURLException
    sealed interface ThreeArgsTest extends URLArgTest {

        // the host component
        String host();

        // the path + query components
        String file();

        // Create a new test case identical to this one but
        // with a different URL scheme and port
        default ThreeArgsTest withScheme(String scheme) {
            String urlWithScheme = urlWithScheme(scheme);
            if (this instanceof OfHostFile) {
                return new OfHostFile(character(), host(), file(), urlWithScheme);
            }
            throw new AssertionError("unexpected subclass: " + this.getClass());
        }

        @Override
        default boolean early(int c) {
            return (c < 31 || c == 127 || c == '/');
        }

        @Override
        default boolean acceptDelayedException(Throwable exception) {
            return URLArgTest.super.acceptDelayedException(exception)
                    || "file".equalsIgnoreCase(scheme())
                    && exception instanceof IOException;
        }

        record OfHostFile(int character, String host, String file, String url)
                implements ThreeArgsTest {
        }

        static ThreeArgsTest ofHostFile(int c) {
            String host = "local%shost".formatted(Character.toString(c));
            String url = "http://" + host + "/";
            return new OfHostFile(c, host, "/", url);
        }
    }

    // Test data for the four args constructor
    // public URL(String scheme, String host, int port, String file)
    //     throws MalformedURLException
    sealed interface FourArgsTest extends URLArgTest {

        // the host component
        String host();

        // the port component
        int port();

        // the path + query components
        String file();

        // Create a new test case identical to this one but
        // with a different URL scheme and port
        default FourArgsTest withScheme(String scheme) {
            String urlWithScheme = urlWithScheme(scheme);
            if (this instanceof OfHostFilePort) {
                int port = URLArgTest.port(scheme);
                return new OfHostFilePort(character(), host(), port, file(), urlWithScheme);
            }
            throw new AssertionError("unexpected subclass: " + this.getClass());
        }

        @Override
        default boolean early(int c) {
            return (c < 31 || c == 127 || c == '/');
        }

        @Override
        default boolean acceptDelayedException(Throwable exception) {
            return URLArgTest.super.acceptDelayedException(exception)
                    || "file".equalsIgnoreCase(scheme())
                    && exception instanceof IOException;
        }

        record OfHostFilePort(int character, String host, int port, String file, String url)
                implements FourArgsTest {
        }

        static FourArgsTest ofHostPortFile(int c) {
            String host = "local%shost".formatted(Character.toString(c));
            String url = "http://" + host + "/";
            int port = URLArgTest.port(URLArgTest.scheme(url));
            return new OfHostFilePort(c, host, port, "/", url);
        }
    }


    // Generate test data for the URL one arg constructor, with variations
    // of the host component.
    static Stream<OneArgTest> oneArgHostTests() {
        List<OneArgTest> tests = new ArrayList<>();
        List<OneArgTest> urls = new ArrayList<>();
        urls.addAll((UNWISE + EXCLUDED_DELIMS).chars()
                .mapToObj(OneArgTest::ofHost).toList());
        urls.addAll(IntStream.concat(IntStream.range(0, 31), IntStream.of(127))
                .mapToObj(OneArgTest::ofHost).toList());
        for (String scheme : List.of("http", "https", "ftp")) {
            for (var test : urls) {
                tests.add(test.withScheme(scheme));
            }
        }
        return tests.stream();
    }

    // Generate test data for the URL one arg constructor, with variations
    // of the user info component.
    static Stream<OneArgTest> oneArgUserInfoTests() {
        List<OneArgTest> tests = new ArrayList<>();
        List<OneArgTest> urls = new ArrayList<>();
        urls.addAll(IntStream.concat(IntStream.range(0, 31), IntStream.of(127))
                .mapToObj(OneArgTest::ofUserInfo).toList());
        urls.add(OneArgTest.ofUserInfo('\\'));
        for (String scheme : List.of("http", "https", "ftp")) {
            for (var test : urls) {
                tests.add(test.withScheme(scheme));
            }
        }
        return tests.stream();
    }

    // Test data with all variations for the URL one arg
    // constructor (spec)
    static Stream<OneArgTest> oneArgTests() {
        return Stream.concat(oneArgHostTests(), oneArgUserInfoTests());
    }

    // Test data with all variations for the URL two arg
    // constructor (URL, spec)
    static Stream<TwoArgsTest> twoArgTests() {
        return oneArgTests().map(TwoArgsTest::ofOneArgTest);
    }

    // Generate test data for the URL three arguments constructor
    // (scheme, host, file)
    static Stream<ThreeArgsTest> threeArgsTests() {
        List<ThreeArgsTest> urls = new ArrayList<>();
        urls.addAll((UNWISE + EXCLUDED_DELIMS + DELIMS).chars()
                .mapToObj(ThreeArgsTest::ofHostFile).toList());
        urls.addAll(IntStream.concat(IntStream.range(0, 31), IntStream.of(127))
                .mapToObj(ThreeArgsTest::ofHostFile).toList());
        List<ThreeArgsTest> tests = new ArrayList<>();
        for (String scheme : List.of("http", "https", "ftp", "file")) {
            for (var test : urls) {
                tests.add(test.withScheme(scheme));
            }
        }
        return tests.stream();
    }

    // Generate test data for the URL four arguments constructor
    // (scheme, host, port, file)
    static Stream<FourArgsTest> fourArgsTests() {
        List<FourArgsTest> urls = new ArrayList<>();
        urls.addAll((UNWISE + EXCLUDED_DELIMS + DELIMS).chars()
                .mapToObj(FourArgsTest::ofHostPortFile).toList());
        urls.addAll(IntStream.concat(IntStream.range(0, 31), IntStream.of(127))
                .mapToObj(FourArgsTest::ofHostPortFile).toList());
        List<FourArgsTest> tests = new ArrayList<>();
        for (String scheme : List.of("http", "https", "ftp", "file")) {
            for (var test : urls) {
                tests.add(test.withScheme(scheme));
            }
        }
        return tests.stream();
    }



    @ParameterizedTest
    @MethodSource("oneArgTests")
    public void testOneArgConstructor(OneArgTest test) throws Exception {

        int c = test.character();
        String url = test.url();
        if (EARLY_PARSING || test.early(c)) {
            err.println("Early parsing: " + test.describe());
            var exception = assertThrows(MalformedURLException.class, () -> {
                new URL(url);
            });
            err.println("Got expected exception: " + exception);
        } else {
            err.println("Delayed parsing: " + test.describe());
            URL u = new URL(url);
            var exception = assertThrows(IOException.class, () -> {
                u.openConnection().connect();
            });
            if (!test.acceptDelayedException(exception)) {
                    err.println("unexpected exception type: " + exception);
                    throw exception;
            }
            err.println("Got expected exception: " + exception);
            assertFalse(exception instanceof ConnectException);
        }
    }

    @ParameterizedTest
    @MethodSource("twoArgTests")
    public void testTwoArgConstructor(TwoArgsTest test) throws Exception {

        int c = test.character();
        String url = test.url();
        String scheme = URLArgTest.scheme(url);
        URL u = new URL(scheme, null,"");
        if (EARLY_PARSING || test.early(c)) {
            err.println("Early parsing: " + test.describe());
            var exception = assertThrows(MalformedURLException.class, () -> {
                new URL(u, url);
            });
            err.println("Got expected exception: " + exception);
        } else {
            err.println("Delayed parsing: " + test.describe());
            URL u2 = new URL(u, url);
            var exception = assertThrows(IOException.class, () -> {
                u2.openConnection().connect();
            });
            if (!test.acceptDelayedException(exception)) {
                err.println("unexpected exception type: " + exception);
                throw exception;
            }
            err.println("Got expected exception: " + exception);
            assertFalse(exception instanceof ConnectException);
        }
    }

    @ParameterizedTest
    @MethodSource("threeArgsTests")
    public void testThreeArgsConstructor(ThreeArgsTest test) throws Exception {

        int c = test.character();
        String url = test.url();
        if (EARLY_PARSING || test.early(c)) {
            err.println("Early parsing: " + url);
            var exception = assertThrows(MalformedURLException.class, () -> {
                new URL(test.scheme(), test.host(), test.file());
            });
            err.println("Got expected exception: " + exception);
        } else {
            err.println("Delayed parsing: " + url);
            URL u = new URL(test.scheme(), test.host(), test.file());
            var exception = assertThrows(IOException.class, () -> {
                u.openConnection().connect();
            });
            if (!test.acceptDelayedException(exception)) {
                err.println("unexpected exception type: " + exception);
                throw exception;
            }
            err.println("Got expected exception: " + exception);
            assertFalse(exception instanceof ConnectException);
        }
    }

    @ParameterizedTest
    @MethodSource("fourArgsTests")
    public void testFourArgsConstructor(FourArgsTest test) throws Exception {

        int c = test.character();
        String url = test.url();
        if (EARLY_PARSING || test.early(c)) {
            err.println("Early parsing: " + url);
            var exception = assertThrows(MalformedURLException.class, () -> {
                new URL(test.scheme(), test.host(), test.port(), test.file());
            });
            err.println("Got expected exception: " + exception);
        } else {
            err.println("Delayed parsing: " + url);
            URL u = new URL(test.scheme(), test.host(), test.port(), test.file());
            var exception = assertThrows(IOException.class, () -> {
                u.openConnection().connect();
            });
            if (!test.acceptDelayedException(exception)) {
                err.println("unexpected exception type: " + exception);
                throw exception;
            }
            err.println("Got expected exception: " + exception);
            assertFalse(exception instanceof ConnectException);
        }
    }

}
