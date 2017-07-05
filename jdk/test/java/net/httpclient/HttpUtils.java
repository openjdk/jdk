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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import static java.net.http.HttpRequest.fromByteArray;
import static java.net.http.HttpRequest.fromByteArrays;
import static java.net.http.HttpRequest.fromFile;
import static java.net.http.HttpRequest.fromInputStream;
import static java.net.http.HttpRequest.fromString;
import java.net.http.HttpResponse;
import static java.net.http.HttpResponse.asByteArray;
import static java.net.http.HttpResponse.asFile;
import static java.net.http.HttpResponse.asInputStream;
import static java.net.http.HttpResponse.asString;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class HttpUtils {

    static final int DEFAULT_OFFSET = 10;
    static final int DEFAULT_LENGTH = 1000;
    static final String midSizedFilename = "/files/notsobigfile.txt";
    static final String smallFilename = "/files/smallfile.txt";
    static final Path midSizedFile;
    static final Path smallFile;
    static final String fileroot;

    public enum RequestBody {
        STRING, FILE, BYTE_ARRAY, BYTE_ARRAY_OFFSET, INPUTSTREAM, STRING_WITH_CHARSET,
    }

    static {
        fileroot = System.getProperty("test.src") + "/docs";
        midSizedFile = Paths.get(fileroot + midSizedFilename);
        smallFile = Paths.get(fileroot + smallFilename);
    }

    static public String getFileContent(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        byte[] buf = new byte[2 * 1024];
        StringBuilder sb = new StringBuilder();
        int byteRead;
        while ((byteRead = fis.read(buf)) != -1) {
            sb.append(new String(buf, 0, byteRead, "US-ASCII"));
        }
        return sb.toString();
    }

    public static HttpRequest.Builder getHttpRequestBuilder(final HttpClient client,
                                                            final String requestType,
                                                            final URI uri)
        throws IOException
    {
        HttpRequest.Builder builder;
        String filename = smallFile.toFile().getAbsolutePath();
        String fileContents = HttpUtils.getFileContent(filename);
        byte buf[] = fileContents.getBytes();
        switch (requestType) {
            case "InputStream":
                InputStream inputStream = new FileInputStream(smallFile.toFile());
                builder = client.request(uri)
                                .body(fromInputStream(inputStream));
                break;
            case "byteArray":
                builder = client.request(uri)
                                .body(fromByteArray(buf));
                break;
            case "byteArrays":
                Iterable iterable = Arrays.asList(buf);
                builder = client.request(uri)
                                .body(fromByteArrays(iterable.iterator()));
                break;
            case "string":
                builder = client.request(uri)
                                .body(fromString(fileContents));
                break;
            case "byteArray_offset":
                builder = client.request(uri)
                                .body(fromByteArray(buf,
                                                    DEFAULT_OFFSET,
                                                    DEFAULT_LENGTH));
                break;
            case "file":
                builder = client.request(uri)
                                .body(fromFile(smallFile));
                break;
            case "string_charset":
                builder = client.request(uri)
                                .body(fromString(new String(buf),
                                                 Charset.defaultCharset()));
                break;
            default:
                builder = null;
                break;
        }
        return builder;
    }

    public static void checkResponse(final HttpResponse response,
                                     String requestType,
                                     final String responseType)
        throws IOException
    {
        String filename = smallFile.toFile().getAbsolutePath();
        String fileContents = HttpUtils.getFileContent(filename);
        if (requestType.equals("byteArray_offset")) {
            fileContents = fileContents.substring(DEFAULT_OFFSET,
                                                  DEFAULT_OFFSET + DEFAULT_LENGTH);
        }
        byte buf[] = fileContents.getBytes();
        String responseBody;
        switch (responseType) {
            case "string":
                responseBody = response.body(asString());
                if (!responseBody.equals(fileContents)) {
                    throw new RuntimeException();
                }
                break;
            case "byteArray":
                byte arr[] = response.body(asByteArray());
                if (!Arrays.equals(arr, buf)) {
                    throw new RuntimeException();
                }
                break;
            case "file":
                response.body(asFile(Paths.get("barf.txt")));
                Path downloaded = Paths.get("barf.txt");
                if (Files.size(downloaded) != fileContents.length()) {
                    throw new RuntimeException("Size mismatch");
                }
                break;
            case "InputStream":
                InputStream is = response.body(asInputStream());
                byte arr1[] = new byte[1024];
                int byteRead;
                StringBuilder sb = new StringBuilder();
                while ((byteRead = is.read(arr1)) != -1) {
                    sb.append(new String(arr1, 0, byteRead));
                }
                if (!sb.toString().equals(fileContents)) {
                    throw new RuntimeException();
                }
                break;
        }
    }
}
