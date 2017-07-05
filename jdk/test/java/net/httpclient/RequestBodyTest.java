/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test @bug 8087112
 * @library /lib/testlibrary/ /
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @build LightWeightHttpServer
 * @build jdk.testlibrary.SimpleSSLContext ProxyServer
 * @run main/othervm RequestBodyTest
 */

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;

public class RequestBodyTest {

    final static String STRING = "string";
    final static String BYTE_ARRAY = "byteArray";
    final static String BYTE_ARRAYS = "byteArrays";
    final static String BYTE_ARRAY_OFFSET = "byteArray_offset";
    final static String FILE = "file";
    final static String STRING_CHARSET = "string_charset";
    final static String INPUTSTREAM = "InputStream";

    final static String midSizedFilename = "/files/notsobigfile.txt";
    final static String smallFilename = "/files/smallfile.txt";
    static Path midSizedFile;
    static Path smallFile;
    static String fileroot;
    static HttpClient client;
    static SSLContext ctx;
    static String httproot;
    static String httpsroot;

    public static void main(String args[]) throws Exception {
        fileroot = System.getProperty("test.src") + "/docs";
        midSizedFile = Paths.get(fileroot + midSizedFilename);
        smallFile = Paths.get(fileroot + smallFilename);
        //start the server
        LightWeightHttpServer.initServer();

        httproot = LightWeightHttpServer.httproot;
        httpsroot = LightWeightHttpServer.httpsroot;
        ctx = LightWeightHttpServer.ctx;
        client = HttpClient.create().sslContext(ctx)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .executorService(Executors.newCachedThreadPool())
                .build();

        String TARGET = httproot + "echo/foo";
        boolean isSync = false;
        requestBodyTypes(TARGET, STRING, STRING, isSync);
        requestBodyTypes(TARGET, STRING, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, STRING, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, STRING, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, STRING, FILE, isSync);

        requestBodyTypes(TARGET, BYTE_ARRAY, STRING, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, FILE, isSync);

        requestBodyTypes(TARGET, BYTE_ARRAYS, STRING, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, FILE, isSync);

        requestBodyTypes(TARGET, INPUTSTREAM, STRING, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, FILE, isSync);

        requestBodyTypes(TARGET, FILE, STRING, isSync);
        requestBodyTypes(TARGET, FILE, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, FILE, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, FILE, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, FILE, FILE, isSync);

        isSync = true;
        requestBodyTypes(TARGET, STRING, STRING, isSync);
        requestBodyTypes(TARGET, STRING, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, STRING, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, STRING, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, STRING, FILE, isSync);

        requestBodyTypes(TARGET, BYTE_ARRAY, STRING, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAY, FILE, isSync);

        requestBodyTypes(TARGET, BYTE_ARRAYS, STRING, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, BYTE_ARRAYS, FILE, isSync);

        requestBodyTypes(TARGET, INPUTSTREAM, STRING, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, INPUTSTREAM, FILE, isSync);

        requestBodyTypes(TARGET, FILE, STRING, isSync);
        requestBodyTypes(TARGET, FILE, BYTE_ARRAY, isSync);
        requestBodyTypes(TARGET, FILE, BYTE_ARRAYS, isSync);
        requestBodyTypes(TARGET, FILE, INPUTSTREAM, isSync);
        requestBodyTypes(TARGET, FILE, FILE, isSync);

    }

    static void requestBodyTypes(final String target,
                                 final String requestType,
                                 final String responseType,
                                 final boolean isAsync)
        throws Exception
    {
        System.out.println("Running test_request_body_type " + requestType +
                " and response type " + responseType + " and sync=" + isAsync);
        URI uri = new URI(target);
        byte buf[];
        String filename = smallFile.toFile().getAbsolutePath();
        String fileContents = HttpUtils.getFileContent(filename);
        buf = fileContents.getBytes();
        HttpRequest.Builder builder = HttpUtils.getHttpRequestBuilder(client,
                                                                      requestType,
                                                                      uri);
        HttpResponse response;
        if (!isAsync) {
            response = builder.GET().response();
        } else {
            response = builder.GET().responseAsync().join();
        }
        HttpUtils.checkResponse(response, requestType, responseType);
        System.out.println("OK");
    }
}
