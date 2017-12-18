/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.OpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import jdk.incubator.http.HttpHeaders;
import jdk.incubator.http.HttpRequest.BodyPublisher;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.testng.Assert.assertThrows;

/*
 * @test
 * @summary Basic tests for API specified exceptions from Publisher, Handler,
 *          and Subscriber convenience static factory methods.
 * @run testng SubscriberPublisherAPIExceptions
 */

public class SubscriberPublisherAPIExceptions {

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    static final Class<IndexOutOfBoundsException> IOB = IndexOutOfBoundsException.class;

    @Test
    public void publisherAPIExceptions() {
        assertThrows(NPE, () -> BodyPublisher.fromByteArray(null));
        assertThrows(NPE, () -> BodyPublisher.fromByteArray(null, 0, 1));
        assertThrows(IOB, () -> BodyPublisher.fromByteArray(new byte[100],    0, 101));
        assertThrows(IOB, () -> BodyPublisher.fromByteArray(new byte[100],    1, 100));
        assertThrows(IOB, () -> BodyPublisher.fromByteArray(new byte[100],   -1,  10));
        assertThrows(IOB, () -> BodyPublisher.fromByteArray(new byte[100],   99,   2));
        assertThrows(IOB, () -> BodyPublisher.fromByteArray(new byte[1],   -100,   1));
        assertThrows(NPE, () -> BodyPublisher.fromByteArrays(null));
        assertThrows(NPE, () -> BodyPublisher.fromFile(null));
        assertThrows(NPE, () -> BodyPublisher.fromInputStream(null));
        assertThrows(NPE, () -> BodyPublisher.fromString(null));
        assertThrows(NPE, () -> BodyPublisher.fromString("A", null));
        assertThrows(NPE, () -> BodyPublisher.fromString(null, UTF_8));
        assertThrows(NPE, () -> BodyPublisher.fromString(null, null));
    }

    @DataProvider(name = "nonExistentFiles")
    public Object[][] nonExistentFiles() {
        List<Path> paths = List.of(Paths.get("doesNotExist"),
                                   Paths.get("tsixEtoNseod"),
                                   Paths.get("doesNotExist2"));
        paths.forEach(p -> {
            if (Files.exists(p))
                throw new AssertionError("Unexpected " + p);
        });

        return paths.stream().map(p -> new Object[] { p }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "nonExistentFiles", expectedExceptions = FileNotFoundException.class)
    public void fromFileCheck(Path path) throws Exception {
        BodyPublisher.fromFile(path);
    }

    @Test
    public void handlerAPIExceptions() {
        Path path = Paths.get(".").resolve("tt");
        assertThrows(NPE, () -> BodyHandler.asByteArrayConsumer(null));
        assertThrows(NPE, () -> BodyHandler.asFile(null));
        assertThrows(NPE, () -> BodyHandler.asFile(null, CREATE, WRITE));
        assertThrows(NPE, () -> BodyHandler.asFile(path, (OpenOption[])null));
        assertThrows(NPE, () -> BodyHandler.asFile(path, new OpenOption[] {null}));
        assertThrows(NPE, () -> BodyHandler.asFile(path, new OpenOption[] {CREATE, null}));
        assertThrows(NPE, () -> BodyHandler.asFile(path, new OpenOption[] {null, CREATE}));
        assertThrows(NPE, () -> BodyHandler.asFile(null, (OpenOption[])null));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(null, CREATE, WRITE));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(path, (OpenOption[])null));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(path, new OpenOption[] {null}));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(path, new OpenOption[] {CREATE, null}));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(path, new OpenOption[] {null, CREATE}));
        assertThrows(NPE, () -> BodyHandler.asFileDownload(null, (OpenOption[])null));
        assertThrows(NPE, () -> BodyHandler.asString(null));
        assertThrows(NPE, () -> BodyHandler.buffering(null, 1));
        assertThrows(IAE, () -> BodyHandler.buffering(new NoOpHandler(), 0));
        assertThrows(IAE, () -> BodyHandler.buffering(new NoOpHandler(), -1));
        assertThrows(IAE, () -> BodyHandler.buffering(new NoOpHandler(), Integer.MIN_VALUE));
    }

    @Test
    public void subscriberAPIExceptions() {
        Path path = Paths.get(".").resolve("tt");
        assertThrows(NPE, () -> BodySubscriber.asByteArrayConsumer(null));
        assertThrows(NPE, () -> BodySubscriber.asFile(null));
        assertThrows(NPE, () -> BodySubscriber.asFile(null, CREATE, WRITE));
        assertThrows(NPE, () -> BodySubscriber.asFile(path, (OpenOption[])null));
        assertThrows(NPE, () -> BodySubscriber.asFile(path, new OpenOption[] {null}));
        assertThrows(NPE, () -> BodySubscriber.asFile(path, new OpenOption[] {CREATE, null}));
        assertThrows(NPE, () -> BodySubscriber.asFile(path, new OpenOption[] {null, CREATE}));
        assertThrows(NPE, () -> BodySubscriber.asFile(null, (OpenOption[])null));
        assertThrows(NPE, () -> BodySubscriber.asString(null));
        assertThrows(NPE, () -> BodySubscriber.buffering(null, 1));
        assertThrows(IAE, () -> BodySubscriber.buffering(new NoOpSubscriber(), 0));
        assertThrows(IAE, () -> BodySubscriber.buffering(new NoOpSubscriber(), -1));
        assertThrows(IAE, () -> BodySubscriber.buffering(new NoOpSubscriber(), Integer.MIN_VALUE));
    }

    static class NoOpHandler implements BodyHandler<Void> {
        @Override public BodySubscriber<Void> apply(int code, HttpHeaders hrds) { return null; }
    }

    static class NoOpSubscriber implements BodySubscriber<Void> {
        @Override public void onSubscribe(Flow.Subscription subscription) { }
        @Override public void onNext(List<ByteBuffer> item) { }
        @Override public void onError(Throwable throwable) { }
        @Override public void onComplete() { }
        @Override public CompletableFuture<Void> getBody() { return null; }
    }
}
