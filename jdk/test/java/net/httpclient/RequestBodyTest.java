/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test @bug 8087112
 * @modules jdk.incubator.httpclient
 *          java.logging
 *          jdk.httpserver
 * @library /lib/testlibrary/ /test/lib
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @build jdk.test.lib.Platform
 * @build jdk.test.lib.util.FileUtils
 * @build LightWeightHttpServer
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng/othervm RequestBodyTest
 */

import java.io.*;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import jdk.test.lib.util.FileUtils;
import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.*;
import static jdk.incubator.http.HttpRequest.BodyProcessor.*;
import static jdk.incubator.http.HttpResponse.BodyHandler.*;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RequestBodyTest {

    static final String fileroot = System.getProperty("test.src") + "/docs";
    static final String midSizedFilename = "/files/notsobigfile.txt";
    static final String smallFilename = "/files/smallfile.txt";

    HttpClient client;
    ExecutorService exec = Executors.newCachedThreadPool();
    String httpURI;
    String httpsURI;

    enum RequestBody {
        BYTE_ARRAY,
        BYTE_ARRAY_OFFSET,
        BYTE_ARRAYS,
        FILE,
        INPUTSTREAM,
        STRING,
        STRING_WITH_CHARSET
    }

    enum ResponseBody {
        BYTE_ARRAY,
        BYTE_ARRAY_CONSUMER,
        DISCARD,
        FILE,
        FILE_WITH_OPTION,
        STRING,
        STRING_WITH_CHARSET,
    }

    @BeforeTest
    public void setup() throws Exception {
        LightWeightHttpServer.initServer();
        httpURI = LightWeightHttpServer.httproot + "echo/foo";
        httpsURI = LightWeightHttpServer.httpsroot + "echo/foo";

        SSLContext ctx = LightWeightHttpServer.ctx;
        client = HttpClient.newBuilder()
                           .sslContext(ctx)
                           .version(HttpClient.Version.HTTP_1_1)
                           .followRedirects(HttpClient.Redirect.ALWAYS)
                           .executor(exec)
                           .build();
    }

    @AfterTest
    public void teardown() throws Exception {
        exec.shutdownNow();
        LightWeightHttpServer.stop();
    }

    @DataProvider
    public Object[][] exchanges() throws Exception {
        List<Object[]> values = new ArrayList<>();

        for (boolean async : new boolean[] { false, true })
            for (String uri : new String[] { httpURI, httpsURI })
                for (String file : new String[] { smallFilename, midSizedFilename })
                    for (RequestBody requestBodyType : RequestBody.values())
                        for (ResponseBody responseBodyType : ResponseBody.values())
                            values.add(new Object[]
                                {uri, requestBodyType, responseBodyType, file, async});

        return values.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "exchanges")
    void exchange(String target,
                  RequestBody requestBodyType,
                  ResponseBody responseBodyType,
                  String file,
                  boolean async)
        throws Exception
    {
        Path filePath = Paths.get(fileroot + file);
        URI uri = new URI(target);

        HttpRequest request = createRequest(uri, requestBodyType, filePath);

        checkResponse(client, request, requestBodyType, responseBodyType, filePath, async);
    }

    static final int DEFAULT_OFFSET = 10;
    static final int DEFAULT_LENGTH = 1000;

    HttpRequest createRequest(URI uri,
                              RequestBody requestBodyType,
                              Path file)
        throws IOException
    {
        HttpRequest.Builder rb =  HttpRequest.newBuilder(uri);

        String filename = file.toFile().getAbsolutePath();
        byte[] fileAsBytes = getFileBytes(filename);
        String fileAsString = new String(fileAsBytes, UTF_8);

        switch (requestBodyType) {
            case BYTE_ARRAY:
                rb.POST(fromByteArray(fileAsBytes));
                break;
            case BYTE_ARRAY_OFFSET:
                rb.POST(fromByteArray(fileAsBytes, DEFAULT_OFFSET, DEFAULT_LENGTH));
                break;
            case BYTE_ARRAYS:
                Iterable<byte[]> iterable = Arrays.asList(fileAsBytes);
                rb.POST(fromByteArrays(iterable));
                break;
            case FILE:
                rb.POST(fromFile(file));
                break;
            case INPUTSTREAM:
                rb.POST(fromInputStream(fileInputStreamSupplier(file)));
                break;
            case STRING:
                rb.POST(fromString(fileAsString));
                break;
            case STRING_WITH_CHARSET:
                rb.POST(fromString(new String(fileAsBytes), Charset.defaultCharset()));
                break;
            default:
                throw new AssertionError("Unknown request body:" + requestBodyType);
        }
        return rb.build();
    }

    void checkResponse(HttpClient client,
                       HttpRequest request,
                       RequestBody requestBodyType,
                       ResponseBody responseBodyType,
                       Path file,
                       boolean async)
        throws InterruptedException, IOException
    {
        String filename = file.toFile().getAbsolutePath();
        byte[] fileAsBytes = getFileBytes(filename);
        if (requestBodyType == RequestBody.BYTE_ARRAY_OFFSET) {
            // Truncate the expected response body, if only a portion was sent
            fileAsBytes = Arrays.copyOfRange(fileAsBytes,
                                             DEFAULT_OFFSET,
                                             DEFAULT_OFFSET + DEFAULT_LENGTH);
        }
        String fileAsString = new String(fileAsBytes, UTF_8);
        Path tempFile = Paths.get("RequestBodyTest.tmp");
        FileUtils.deleteFileIfExistsWithRetry(tempFile);

        switch (responseBodyType) {
            case BYTE_ARRAY:
                HttpResponse<byte[]> bar = getResponse(client, request, asByteArray(), async);
                assertEquals(bar.statusCode(), 200);
                assertEquals(bar.body(), fileAsBytes);
                break;
            case BYTE_ARRAY_CONSUMER:
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                HttpResponse<Void> v = getResponse(client, request,
                        asByteArrayConsumer(o -> consumerBytes(o, baos) ), async);
                byte[] ba = baos.toByteArray();
                assertEquals(v.statusCode(), 200);
                assertEquals(ba, fileAsBytes);
                break;
            case DISCARD:
                Object o = new Object();
                HttpResponse<Object> or = getResponse(client, request, discard(o), async);
                assertEquals(or.statusCode(), 200);
                assertSame(or.body(), o);
                break;
            case FILE:
                HttpResponse<Path> fr = getResponse(client, request, asFile(tempFile), async);
                assertEquals(fr.statusCode(), 200);
                assertEquals(Files.size(tempFile), fileAsString.length());
                assertEquals(Files.readAllBytes(tempFile), fileAsBytes);
                break;
            case FILE_WITH_OPTION:
                fr = getResponse(client, request, asFile(tempFile, CREATE_NEW, WRITE), async);
                assertEquals(fr.statusCode(), 200);
                assertEquals(Files.size(tempFile), fileAsString.length());
                assertEquals(Files.readAllBytes(tempFile), fileAsBytes);
                break;
            case STRING:
                HttpResponse<String> sr = getResponse(client, request, asString(), async);
                assertEquals(sr.statusCode(), 200);
                assertEquals(sr.body(), fileAsString);
                break;
            case STRING_WITH_CHARSET:
                HttpResponse<String> r = getResponse(client, request, asString(StandardCharsets.UTF_8), async);
                assertEquals(r.statusCode(), 200);
                assertEquals(r.body(), fileAsString);
                break;
            default:
                throw new AssertionError("Unknown response body:" + responseBodyType);
        }
    }

    static <T> HttpResponse<T> getResponse(HttpClient client,
                                           HttpRequest request,
                                           HttpResponse.BodyHandler<T> handler,
                                           boolean async)
        throws InterruptedException, IOException
    {
        if (!async)
            return client.send(request, handler);
        else
            return client.sendAsync(request, handler).join();
    }

    static byte[] getFileBytes(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            bis.transferTo(baos);
            return baos.toByteArray();
        }
    }

    static Supplier<FileInputStream> fileInputStreamSupplier(Path f) {
        return new Supplier<>() {
            Path file = f;
            @Override
            public FileInputStream get() {
                try {
                    return new FileInputStream(file.toFile());
                } catch (FileNotFoundException x) {
                    throw new UncheckedIOException(x);
                }
            }
        };
    }

    static void consumerBytes(Optional<byte[]> bytes, ByteArrayOutputStream baos) {
        try {
            if (bytes.isPresent())
                baos.write(bytes.get());
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }
}
