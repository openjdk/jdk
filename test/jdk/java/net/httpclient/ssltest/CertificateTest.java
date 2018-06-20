/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import static java.net.http.HttpClient.Builder.NO_PROXY;

/*
 * @test
 * @build Server CertificateTest
 * @run main/othervm CertificateTest good.keystore expectSuccess
 * @run main/othervm CertificateTest bad.keystore expectFailure
 * @run main/othervm
 *      -Djdk.internal.httpclient.disableHostnameVerification
 *       CertificateTest bad.keystore expectSuccess
 * @run main/othervm
 *      -Djdk.internal.httpclient.disableHostnameVerification=true
 *       CertificateTest bad.keystore expectSuccess
 * @run main/othervm
 *      -Djdk.internal.httpclient.disableHostnameVerification=false
 *       CertificateTest bad.keystore expectFailure
 * @run main/othervm
 *      -Djdk.internal.httpclient.disableHostnameVerification=xxyyzz
 *       CertificateTest bad.keystore expectFailure
 * @run main/othervm CertificateTest loopback.keystore expectSuccess
 */

/**
 * The test runs a number of times. In all cases it uses a valid self-signed certificate
 * that is installed in the trust store (so is trusted) and the same cert is supplied
 * by the server for its own identity. Two servers on two different ports are used
 * on the remote end.
 *
 * For the "good" run the cert contains the correct hostname of the target server
 * and therefore should be accepted by the cert checking code in the client.
 * For the "bad" run, the cert contains an invalid hostname, and should be rejected.
 */
public class CertificateTest {
    static SSLContext ctx;
    static SSLParameters params;
    static boolean expectSuccess;
    static String trustStoreProp;
    static Server server;
    static int port;

    static String TESTSRC = System.getProperty("test.src");
    public static void main(String[] args) throws Exception
    {
        try {
            String keystore = args[0];
            trustStoreProp = TESTSRC + File.separatorChar + keystore;

            String passOrFail = args[1];

            if (passOrFail.equals("expectSuccess")) {
                expectSuccess = true;
            } else {
                expectSuccess = false;
            }
            server = new Server(trustStoreProp);
            port = server.getPort();
            System.setProperty("javax.net.ssl.trustStore", trustStoreProp);
            System.setProperty("javax.net.ssl.trustStorePassword", "passphrase");
            init();
            test(args);
        } finally {
            server.stop();
        }
    }

    static void init() throws Exception
    {
        ctx = SSLContext.getDefault();
        params = ctx.getDefaultSSLParameters();
        //params.setProtocols(new String[] { "TLSv1.2" });
    }

    static void test(String[] args) throws Exception
    {
        String uri_s;
        if (args[0].equals("loopback.keystore"))
            uri_s = "https://127.0.0.1:" + Integer.toString(port) + "/foo";
        else
            uri_s = "https://localhost:" + Integer.toString(port) + "/foo";
        String error = null;
        Exception exception = null;
        System.out.println("Making request to " + uri_s);
        HttpClient client = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .sslContext(ctx)
                .sslParameters(params)
                .build();

        HttpRequest request = HttpRequest.newBuilder(new URI(uri_s))
                .version(HttpClient.Version.HTTP_1_1)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            System.out.printf("Status code %d received\n", response.statusCode());
            if (expectSuccess && response.statusCode() != 200)
                error = "Test failed: good: status should be 200";
            else if (!expectSuccess)
                error = "Test failed: bad: status should not be 200";
        } catch (IOException e) {
            // there must be an SSLException as the exception or cause
            checkExceptionOrCause(SSLException.class, e);
            System.err.println("Caught Exception " + e + ". expectSuccess = " + expectSuccess);
            exception = e;
            if (expectSuccess)
                error = "Test failed: expectSuccess:true, but got unexpected exception";
        }
        if (error != null)
            throw new RuntimeException(error, exception);
    }

    static void checkExceptionOrCause(Class<? extends Throwable> clazz, Throwable t) {
        final Throwable original = t;
        do {
            if (clazz.isInstance(t)) {
                System.out.println("Found expected exception/cause: " + t);
                return; // found
            }
        } while ((t = t.getCause()) != null);
        original.printStackTrace(System.out);
        throw new RuntimeException("Expected " + clazz + "in " + original);
    }
}
