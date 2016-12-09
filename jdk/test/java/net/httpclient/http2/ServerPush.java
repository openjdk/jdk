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
 * @bug 8087112 8159814
 * @library /lib/testlibrary server
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,requests,responses ServerPush
 */

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import jdk.incubator.http.*;
import jdk.incubator.http.HttpResponse.MultiProcessor;
import jdk.incubator.http.HttpResponse.BodyHandler;
import java.util.*;
import java.util.concurrent.*;
import org.testng.annotations.Test;

public class ServerPush {

    static ExecutorService e = Executors.newCachedThreadPool();

    static final int LOOPS = 13;
    static final int FILE_SIZE = 512 * 1024 + 343;

    static Path tempFile;

    @Test(timeOut=30000)
    public static void test() throws Exception {
        Http2TestServer server = null;
        final Path dir = Files.createTempDirectory("serverPush");
        try {
            server = new Http2TestServer(false, 0);
            server.addHandler(new PushHandler(FILE_SIZE, LOOPS), "/");
            tempFile = TestUtil.getAFile(FILE_SIZE);

            System.err.println("Server listening on port " + server.getAddress().getPort());
            server.start();
            int port = server.getAddress().getPort();

            // use multi-level path
            URI uri = new URI("http://127.0.0.1:" + port + "/foo/a/b/c");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

            CompletableFuture<MultiMapResult<Path>> cf =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                    .executor(e).build().sendAsync(
                        request, MultiProcessor.asMap((req) -> {
                            URI u = req.uri();
                            Path path = Paths.get(dir.toString(), u.getPath());
                            try {
                                Files.createDirectories(path.getParent());
                            } catch (IOException ee) {
                                throw new UncheckedIOException(ee);
                            }
                            return Optional.of(BodyHandler.asFile(path));
                        }
                    ));
            MultiMapResult<Path> results = cf.get();

            //HttpResponse resp = request.response();
            System.err.println(results.size());
            Set<HttpRequest> requests = results.keySet();

            for (HttpRequest r : requests) {
                URI u = r.uri();
                Path result = results.get(r).get().body();
                System.err.printf("%s -> %s\n", u.toString(), result.toString());
                TestUtil.compareFiles(result, tempFile);
            }
            if (requests.size() != LOOPS + 1)
                throw new RuntimeException("some results missing");
            System.out.println("TEST OK: sleeping for 5 sec");
            Thread.sleep (5 * 1000);
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
