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

/*
 * @test
 * @bug 8087112
 * @library /lib/testlibrary
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.httpclient
 * @compile/module=java.httpclient java/net/http/BodyOutputStream.java
 * @compile/module=java.httpclient java/net/http/BodyInputStream.java
 * @compile/module=java.httpclient java/net/http/PushHandler.java
 * @compile/module=java.httpclient java/net/http/Http2Handler.java
 * @compile/module=java.httpclient java/net/http/Http2TestExchange.java
 * @compile/module=java.httpclient java/net/http/Http2TestServerConnection.java
 * @compile/module=java.httpclient java/net/http/Http2TestServer.java
 * @compile/module=java.httpclient java/net/http/OutgoingPushPromise.java
 * @compile/module=java.httpclient java/net/http/TestUtil.java
 * @run testng/othervm -Djava.net.http.HttpClient.log=requests,responses ServerPush
 */

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.testng.annotations.Test;

public class ServerPush {

    static ExecutorService e = Executors.newCachedThreadPool();

    static final int LOOPS = 13;
    static final int FILE_SIZE = 32 * 1024;

    static Path tempFile;

    @Test(timeOut=30000)
    public static void test() throws Exception {
        Http2TestServer server = null;
        Path dir = null;
        try {
            server = new Http2TestServer(false, 0,
                                         new PushHandler(FILE_SIZE, LOOPS));
            tempFile = TestUtil.getAFile(FILE_SIZE);

            System.err.println("Server listening on port " + server.getAddress().getPort());
            server.start();
            int port = server.getAddress().getPort();
            dir = Files.createTempDirectory("serverPush");

            URI uri = new URI("http://127.0.0.1:" + Integer.toString(port) + "/foo");
            HttpRequest request = HttpRequest.create(uri)
                    .version(HttpClient.Version.HTTP_2)
                    .GET();

            CompletableFuture<Map<URI,Path>> cf =
            request.multiResponseAsync(HttpResponse.multiFile(dir));
            Map<URI,Path> results = cf.get();

            //HttpResponse resp = request.response();
            System.err.println(results.size());
            Set<URI> uris = results.keySet();
            for (URI u : uris) {
                Path result = results.get(u);
                System.err.printf("%s -> %s\n", u.toString(), result.toString());
                TestUtil.compareFiles(result, tempFile);
            }
            System.out.println("TEST OK");
        } finally {
            e.shutdownNow();
            server.stop();
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    dir.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    path.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
