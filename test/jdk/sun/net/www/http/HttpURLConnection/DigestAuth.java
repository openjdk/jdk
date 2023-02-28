/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

import static java.util.Map.entry;

/*
 * @test
 * @bug 8138990 8281561
 * @summary Tests for HTTP Digest auth
 *          The impl maintains a cache for auth info,
 *          the testcases run in a separate JVM to avoid cache hits
 * @modules jdk.httpserver
 * @run main/othervm DigestAuth bad
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth good
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth only_nonce
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=SHA-1 DigestAuth sha1-good
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth sha1-bad
 * @run main/othervm DigestAuth sha256
 * @run main/othervm DigestAuth sha512
 * @run main/othervm DigestAuth sha256-userhash
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth sha256
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth no_header
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth no_nonce
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth no_qop
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth invalid_alg
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth validate_server
 * @run main/othervm -Dhttp.auth.digest.reEnabledAlgorithms=MD5 DigestAuth validate_server_no_qop
 */

/*
 * The sha512-256-userhash case must be run manually. It needs to run with sudo as the
 * test must bind to port 80. You also need a modified JDK where
 * sun.net.www.protocol.http.DigestAuthentication.getCnonce
 * returns the hardcoded cnonce value below (normally it is chosen at random)
 *  "NTg6RKcb9boFIAS3KrFK9BGeh+iDa/sm6jUMp2wds69v"
 * It can be run from the command line directly as follows:
 * sudo java -Djdk.net.hosts.file=hosts DigestAuth sha512-256-userhash port80
 * assuming you are running in the test source directory
 */
public class DigestAuth {

    static final String EXPECT_FAILURE = null;
    static final String EXPECT_DIGEST = "Digest";
    static final String REALM = "testrealm@host.com";
    static final String NEXT_NONCE = "40f2e879449675f288476d772627370a";

    static final String GOOD_WWW_AUTH_HEADER = "Digest "
            + "realm=\"testrealm@host.com\", "
            + "qop=\"auth,auth-int\", "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
            + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";

    static final String GOOD_WWW_AUTH_HEADER_NO_QOP = "Digest "
            + "realm=\"testrealm@host.com\", "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
            + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";

    static final String WWW_AUTH_HEADER_NO_NONCE = "Digest "
            + "realm=\"testrealm@host.com\", "
            + "qop=\"auth,auth-int\", "
            + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";

    static final String WWW_AUTH_HEADER_NO_QOP = "Digest "
            + "realm=\"testrealm@host.com\", "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
            + "opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";

    static final String WWW_AUTH_HEADER_ONLY_NONCE = "Digest "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\"";

    static final String WWW_AUTH_HEADER_SHA1 = "Digest "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
            + "algorithm=\"SHA1\"";

    static final String WWW_AUTH_HEADER_SHA256 = "Digest "
            + "nonce=\"a69ae8a2e17c219bc6c118b673e93601616a6a"
            + "4d8fde3a19996748d77ad0464b\", qop=\"auth\", "
            + "opaque=\"efc62777cff802cb29252f626b041f381cd360"
            + "7187115871ca25e7b51a3757e9\", algorithm=SHA-256";

    static final String WWW_AUTH_HEADER_SHA512 = "Digest "
            + "nonce=\"9aaa8d3ae53b54ce653a5d52d895afcd9c0e430"
            + "a17bdf98bb34235af84fba268d31376a63e0c39079b519"
            + "c14baa0429754266f35b62a47b9c8b5d3d36c638282\","
            + " qop=\"auth\", opaque=\"28cdc6bae6c5dd7ec89dbf"
            + "af4d4f26b70f41ebbb83dc7af0950d6de016c40f412224"
            + "676cd45ebcf889a70e65a2b055a8b5232e50281272ba7c"
            + "67628cc3bb3492\", algorithm=SHA-512";

    static final String WWW_AUTH_HEADER_SHA_256_UHASH = "Digest "
            + "realm=\"testrealm@host.com\", "
            + "qop=\"auth\", algorithm=SHA-256,"
            + "nonce=\"5TsQWLVdgBdmrQ0XsxbDODV+57QdFR34I9HAbC"
            + "/RVvkK\", opaque=\"HRPCssKJSGjCrkzDg8OhwpzCiGP"
            + "ChXYjwrI2QmXDnsOS\", charset=UTF-8, userhash=true";

    static final String WWW_AUTH_HEADER_INVALID_ALGORITHM = "Digest "
            + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
            + "algorithm=\"SHA123\"";

    static final String AUTH_INFO_HEADER_NO_QOP_FIRST =
              "nextnonce=\"" + NEXT_NONCE + "\", "
            + "rspauth=\"ee85bc4315d8b18757809f1a8b9382d8\"";

    static final String AUTH_INFO_HEADER_NO_QOP_SECOND =
              "rspauth=\"12f2fa12841b3775b6054576722446b2\"";

    static final String AUTH_INFO_HEADER_WRONG_DIGEST =
              "nextnonce=\"" + NEXT_NONCE + "\", "
            + "rspauth=\"7327570c586207eca2afae94fc20903d\", "
            + "cnonce=\"0a4f113b\", "
            + "nc=00000001, "
            + "qop=auth";

    // These two must be run manually with a modified JDK
    // that generates the exact cnonce given below.
    static final String SHA_512_256_FIRST = "Digest "
            + "realm=\"api@example.org\", "
            + "qop=\"auth\", "
            + "algorithm=SHA-512-256, "
            + "nonce=\"5TsQWLVdgBdmrQ0XsxbDODV+57QdFR34I9HAbC/RVvkK\", "
            + "opaque=\"HRPCssKJSGjCrkzDg8OhwpzCiGPChXYjwrI2QmXDnsOS\", "
            + "charset=UTF-8, "
            + "userhash=true ";

    // Below taken from corrected version of RFC 7616
    static final Map<String,String> SHA_512_256_EXPECTED =
        Map.ofEntries(
            entry("username", "793263caabb707a56211940d90411ea4a575adeccb"
                                + "7e360aeb624ed06ece9b0b"),
            entry("realm", "api@example.org"),
            entry("uri", "/doe.json"),
            entry("algorithm", "SHA-512-256"),
            entry("nonce", "5TsQWLVdgBdmrQ0XsxbDODV+57QdFR34I9HAbC/RVvkK"),
            entry("nc", "00000001"),
            entry("cnonce", "NTg6RKcb9boFIAS3KrFK9BGeh+iDa/sm6jUMp2wds69v"),
            entry("qop", "auth"),
            entry("response", "3798d4131c277846293534c3edc11bd8a5e4cdcbff78"
                                + "b05db9d95eeb1cec68a5"),
            entry("opaque", "HRPCssKJSGjCrkzDg8OhwpzCiGPChXYjwrI2QmXDnsOS"),
            entry("userhash", "true"));

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("No testcase specified");
        }
        String testcase = args[0];
        System.out.println("Running test: " + testcase);
        boolean usePort80 = args.length > 1 && args[1].equals("port80");

        // start a local HTTP server
        try (LocalHttpServer server = LocalHttpServer.startServer(usePort80)) {

            // set authenticator
            AuthenticatorImpl auth = new AuthenticatorImpl();

            String url = String.format("http://%s/test/", server.getAuthority());

            boolean success = true;
            switch (testcase) {
                case "sha512-256-userhash":
                    auth = new AuthenticatorImpl("J\u00e4s\u00f8n Doe", "Secret, or not?");
                    // file based name service must be used so domain
                    // below resolves to localhost
                    if (usePort80) {
                        url = "http://api.example.org/doe.json";
                    } else {
                        url = "http://api.example.org:" + server.getPort() + "/doe.json";
                    }
                    server.setWWWAuthHeader(SHA_512_256_FIRST);
                    server.setExpectedRequestParams(SHA_512_256_EXPECTED);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    break;
                case "bad":
                    // server returns a good WWW-Authenticate header with MD5
                    // but MD5 is disallowed by default
                    server.setWWWAuthHeader(GOOD_WWW_AUTH_HEADER);
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    if (auth.lastRequestedPrompt == null ||
                            !auth.lastRequestedPrompt.equals(REALM)) {
                        System.out.println("Unexpected realm: "
                                + auth.lastRequestedPrompt);
                        success = false;
                    }
                    break;
                case "good":
                    // server returns a good WWW-Authenticate header
                    server.setWWWAuthHeader(GOOD_WWW_AUTH_HEADER);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    if (auth.lastRequestedPrompt == null ||
                            !auth.lastRequestedPrompt.equals(REALM)) {
                        System.out.println("Unexpected realm: "
                                + auth.lastRequestedPrompt);
                        success = false;
                    }
                    break;
                case "validate_server":
                    // enable processing Authentication-Info headers
                    System.setProperty("http.auth.digest.validateServer",
                            "true");

                    /* Server returns good WWW-Authenticate
                     * and Authentication-Info headers with wrong digest
                     */
                    server.setWWWAuthHeader(GOOD_WWW_AUTH_HEADER);
                    server.setAuthInfoHeader(AUTH_INFO_HEADER_WRONG_DIGEST);
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    if (auth.lastRequestedPrompt == null ||
                            !auth.lastRequestedPrompt.equals(REALM)) {
                        System.out.println("Unexpected realm: "
                                + auth.lastRequestedPrompt);
                        success = false;
                    }
                    break;
                case "validate_server_no_qop":
                    // enable processing Authentication-Info headers
                    System.setProperty("http.auth.digest.validateServer",
                            "true");

                    /* Server returns good both WWW-Authenticate
                     * and Authentication-Info headers without any qop field,
                     * so that client-nonce should not be taked into account,
                     * and connection should succeed.
                     */
                    server.setWWWAuthHeader(GOOD_WWW_AUTH_HEADER_NO_QOP);
                    server.setAuthInfoHeader(AUTH_INFO_HEADER_NO_QOP_FIRST);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    if (auth.lastRequestedPrompt == null ||
                            !auth.lastRequestedPrompt.equals(REALM)) {
                        System.out.println("Unexpected realm: "
                                + auth.lastRequestedPrompt);
                        success = false;
                    }

                    // connect again and check if nextnonce was used
                    server.setAuthInfoHeader(AUTH_INFO_HEADER_NO_QOP_SECOND);
                    success &= testAuth(url, auth, EXPECT_DIGEST);
                    if (!NEXT_NONCE.equals(server.lastRequestedNonce)) {
                        System.out.println("Unexpected next nonce: "
                                + server.lastRequestedNonce);
                        success = false;
                    }
                    break;
                case "only_nonce":
                    /* Server returns a good WWW-Authenticate header
                     * which contains only nonce (no realm set).
                     *
                     * Realm from  WWW-Authenticate header is passed to
                     * authenticator which can use it as a prompt
                     * when it asks a user for credentials.
                     *
                     * It's fine if an HTTP client doesn't fail if no realm set,
                     * and delegates making a decision to authenticator/user.
                     */
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_ONLY_NONCE);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    if (auth.lastRequestedPrompt != null &&
                            !auth.lastRequestedPrompt.trim().isEmpty()) {
                        System.out.println("Unexpected realm: "
                                + auth.lastRequestedPrompt);
                        success = false;
                    }
                    break;
                case "sha1-good":
                    // server returns a good WWW-Authenticate header with SHA-1
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_SHA1);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    break;
                case "sha1-bad":
                    // server returns a WWW-Authenticate header with SHA-1
                    // but SHA-1 disabled
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_SHA1);
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    break;
                case "sha256":
                    // server returns a good WWW-Authenticate header with SHA-256
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_SHA256);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    break;
                case "sha512":
                    // server returns a good WWW-Authenticate header with SHA-512
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_SHA512);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    break;
                case "sha256-userhash":
                    // server returns a good WWW-Authenticate header with SHA-256
                    // also sets the userhash=true parameter
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_SHA_256_UHASH);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    // make sure the userhash parameter was set correctly
                    // and the username itself is the correct hash
                    server.checkUserHash(getUserHash("SHA-256", "Mufasa", REALM));
                    break;
                case "no_header":
                    // server returns no WWW-Authenticate header
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    if (auth.lastRequestedScheme != null) {
                        System.out.println("Unexpected scheme: "
                                + auth.lastRequestedScheme);
                        success = false;
                    }
                    break;
                case "no_nonce":
                    // server returns a wrong WWW-Authenticate header (no nonce)
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_NO_NONCE);
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    break;
                case "invalid_alg":
                    // server returns a wrong WWW-Authenticate header
                    // (invalid hash algorithm)
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_INVALID_ALGORITHM);
                    success = testAuth(url, auth, EXPECT_FAILURE);
                    break;
                case "no_qop":
                    // server returns a good WWW-Authenticate header
                    // without QOPs
                    server.setWWWAuthHeader(WWW_AUTH_HEADER_NO_QOP);
                    success = testAuth(url, auth, EXPECT_DIGEST);
                    break;
                default:
                    throw new RuntimeException("Unexpected testcase: "
                            + testcase);
            }

            if (!success) {
                throw new RuntimeException("Test failed");
            }
        }

        System.out.println("Test passed");
    }

    static boolean testAuth(String url, AuthenticatorImpl auth,
            String expectedScheme) {

        try {
            System.out.printf("Connect to %s, expected auth scheme is '%s'%n",
                    url, expectedScheme);
            load(url, auth);

            if (expectedScheme == null) {
                System.out.println("Unexpected successful connection");
                return false;
            }

            System.out.printf("Actual auth scheme is '%s'%n",
                    auth.lastRequestedScheme);
            if (!expectedScheme.equalsIgnoreCase(auth.lastRequestedScheme)) {
                System.out.println("Unexpected auth scheme");
                return false;
            }
        } catch (IOException e) {
            if (expectedScheme != null) {
                System.out.println("Unexpected exception: " + e);
                e.printStackTrace(System.out);
                return false;
            }
            System.out.println("Expected exception: " + e);
        }

        return true;
    }

    static void load(String url, Authenticator auth) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)(new URL(url).openConnection());
        conn.setAuthenticator(auth);
        conn.setUseCaches(false);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {

            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Couldn't read response");
            }
            do {
                System.out.println(line);
            } while ((line = reader.readLine()) != null);
        }
    }

    public static String getUserHash(String alg, String user, String realm) {
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            String msg = user + ":" + realm;
            //String msg = "Mufasa:testrealm@host.com";
            byte[] output = md.digest(msg.getBytes(StandardCharsets.ISO_8859_1));
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<output.length; i++) {
                String s1 = Integer.toHexString(output[i] & 0xf);
                String s2 = Integer.toHexString(Byte.toUnsignedInt(output[i]) >>> 4);
                sb.append(s2).append(s1);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static class AuthenticatorImpl extends Authenticator {

        private String lastRequestedScheme;
        private String lastRequestedPrompt;

        private final String user, pass;

        AuthenticatorImpl() {
            this("Mufasa", "Circle Of Life");
        }

        AuthenticatorImpl(String user, String pass) {
            this.user = user;
            this.pass = pass;
        }

        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            lastRequestedScheme = getRequestingScheme();
            lastRequestedPrompt = getRequestingPrompt();
            System.out.println("AuthenticatorImpl: requested "
                    + lastRequestedScheme);
            return new PasswordAuthentication(user, pass.toCharArray());
        }
    }

    // local HTTP server which pretends to support HTTP Digest auth
    static class LocalHttpServer implements HttpHandler, AutoCloseable {

        private final HttpServer server;
        private volatile String wwwAuthHeader = null;
        private volatile String authInfoHeader = null;
        private volatile String lastRequestedNonce;
        private volatile String lastRequestedUser;
        private volatile String lastRequestedUserhash;
        private volatile Map<String,String> expectedParams;

        private LocalHttpServer(HttpServer server) {
            this.server = server;
        }

        public String getAuthority() {
            InetAddress address = server.getAddress().getAddress();
            String hostaddr = address.isAnyLocalAddress()
                ? "localhost" : address.getHostAddress();
            if (hostaddr.indexOf(':') > -1) {
                hostaddr = "[" + hostaddr + "]";
            }
            return hostaddr + ":" + getPort();
        }

        void setWWWAuthHeader(String wwwAuthHeader) {
            this.wwwAuthHeader = wwwAuthHeader;
        }

        void setExpectedRequestParams(Map<String,String> params) {
            this.expectedParams = params;
        }

        void setAuthInfoHeader(String authInfoHeader) {
            this.authInfoHeader = authInfoHeader;
        }

        void checkUserHash(String expectedUser) {
            boolean pass = true;
            if (!expectedUser.equals(lastRequestedUser)) {
                System.out.println("Username mismatch:");
                System.out.println("Expected: " + expectedUser);
                System.out.println("Received: " + lastRequestedUser);
                pass = false;
            }
            if (!lastRequestedUserhash.equalsIgnoreCase("true")) {
                System.out.println("Userhash mismatch:");
                pass = false;
            }
            if (!pass) {
                throw new RuntimeException("Test failed: checkUserHash");
            }
        }

        void checkExpectedParams(String header) {
            if (expectedParams == null)
                return;
            expectedParams.forEach((name, value) -> {
                String rxValue = findParameter(header, name);
                if (!rxValue.equalsIgnoreCase(value)) {
                    throw new RuntimeException("value mismatch "
                        + "name = " + name + " (" + rxValue + "/"
                        + value + ")");
                }
            });
        }

        static LocalHttpServer startServer(boolean usePort80) throws IOException {
            int port = usePort80 ? 80 : 0;
            InetAddress loopback = InetAddress.getLoopbackAddress();
            HttpServer httpServer = HttpServer.create(
                    new InetSocketAddress(loopback, port), 0);
            LocalHttpServer localHttpServer = new LocalHttpServer(httpServer);
            localHttpServer.start();

            return localHttpServer;
        }

        void start() {
            server.createContext("/test", this);
            server.createContext("/", this);
            server.start();
            System.out.println("HttpServer: started on port " + getAuthority());
        }

        void stop() {
            server.stop(0);
            System.out.println("HttpServer: stopped");
        }

        int getPort() {
            return server.getAddress().getPort();
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("HttpServer: handle connection");

            // read a request
            try (InputStream is = t.getRequestBody()) {
                while (is.read() > 0);
            }

            try {
                List<String> headers = t.getRequestHeaders()
                        .get("Authorization");
                String header = "";
                if (headers != null && !headers.isEmpty()) {
                    header = headers.get(0).trim().toLowerCase();
                }
                if (header.startsWith("digest")) {
                    if (authInfoHeader != null) {
                        t.getResponseHeaders().add("Authentication-Info",
                                authInfoHeader);
                    }
                    checkExpectedParams(header);
                    lastRequestedNonce = findParameter(header, "nonce");
                    lastRequestedUser = findParameter(header, "username");
                    lastRequestedUserhash = findParameter(header, "userhash");
                    byte[] output = "hello".getBytes();
                    t.sendResponseHeaders(200, output.length);
                    t.getResponseBody().write(output);
                    System.out.println("HttpServer: return 200");
                } else {
                    if (wwwAuthHeader != null) {
                        t.getResponseHeaders().add(
                                "WWW-Authenticate", wwwAuthHeader);
                    }
                    byte[] output = "forbidden".getBytes();
                    t.sendResponseHeaders(401, output.length);
                    t.getResponseBody().write(output);
                    System.out.println("HttpServer: return 401");
                }
            } catch (IOException e) {
                System.out.println("HttpServer: exception: " + e);
                System.out.println("HttpServer: return 500");
                t.sendResponseHeaders(500, 0);
            } finally {
                t.close();
            }
        }

        private static String findParameter(String header, String name) {
            name = name.toLowerCase();
            if (header != null) {
                String[] params = header.split("\\s");
                for (String param : params) {
                    param = param.trim().toLowerCase();
                    if (param.startsWith(name)) {
                        String[] parts = param.split("=");
                        if (parts.length > 1) {
                            return parts[1]
                                    .replaceAll("\"", "").replaceAll(",", "");
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public void close() {
            stop();
        }
    }
}
