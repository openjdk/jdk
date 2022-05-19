/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8216498
 * @summary Tests Exception detail message when too few response bytes are
 *          received before a socket exception or eof.
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext ShortResponseBody ShortResponseBodyPost
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=headers,errors,channel
 *       -Djdk.internal.httpclient.debug=true
 *       ShortResponseBodyPost
 */

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
public class ShortResponseBodyPost extends ShortResponseBody {

    // POST tests are racy in what may be received before writing may cause a
    // broken pipe or reset exception, before all the received data can be read.
    // Any message up to, and including, the "expected" error message can occur.
    // Strictly ordered list, in order of possible occurrence.
    static final List<String> MSGS_ORDER =
            List.of("no bytes", "status line", "header");


    @Test(dataProvider = "uris")
    void testSynchronousPOST(String urlp, String expectedMsg, boolean sameClient)
        throws Exception
    {
        checkSkip();
        out.print("---\n");
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            String url = uniqueURL(urlp);
            if (client == null)
                client = newHttpClient(sameClient);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .POST(BodyPublishers.ofInputStream(() -> new InfiniteInputStream()))
                    .build();
            out.println("Request: " + request);
            try {
                HttpResponse<String> response = client.send(request, ofString());
                String body = response.body();
                out.println(response + ": " + body);
                fail("UNEXPECTED RESPONSE: " + response);
            } catch (IOException ioe) {
                out.println("Caught expected exception:" + ioe);

                List<String> expectedMessages = new ArrayList<>();
                expectedMessages.add(expectedMsg);
                MSGS_ORDER.stream().takeWhile(s -> !s.equals(expectedMsg))
                                   .forEach(expectedMessages::add);

                assertExpectedMessage(request, ioe, expectedMessages);
                // synchronous API must have the send method on the stack
                assertSendMethodOnStack(ioe);
                assertNoConnectionExpiredException(ioe);
            }
        }
    }

    @Test(dataProvider = "uris")
    void testAsynchronousPOST(String urlp, String expectedMsg, boolean sameClient)
        throws Exception
    {
        checkSkip();
        out.print("---\n");
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            String url = uniqueURL(urlp);
            if (client == null)
                client = newHttpClient(sameClient);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .POST(BodyPublishers.ofInputStream(() -> new InfiniteInputStream()))
                    .build();
            out.println("Request: " + request);
            try {
                HttpResponse<String> response = client.sendAsync(request, ofString()).get();
                String body = response.body();
                out.println(response + ": " + body);
                fail("UNEXPECTED RESPONSE: " + response);
            } catch (ExecutionException ee) {
                if (ee.getCause() instanceof IOException) {
                    IOException ioe = (IOException) ee.getCause();
                    out.println("Caught expected exception:" + ioe);

                    List<String> expectedMessages = new ArrayList<>();
                    expectedMessages.add(expectedMsg);
                    MSGS_ORDER.stream().takeWhile(s -> !s.equals(expectedMsg))
                            .forEach(expectedMessages::add);

                    assertExpectedMessage(request, ioe, expectedMessages);
                    assertNoConnectionExpiredException(ioe);
                } else {
                    throw ee;
                }
            }
        }
    }

}

