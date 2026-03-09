/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267139
 * @summary Verify PathSubscriber exception behavior
 * @modules java.net.http/jdk.internal.net.http
 *          java.net.http/jdk.internal.net.http:open
 * @run testng/othervm PathSubscriberExceptionTest
 */

import jdk.internal.net.http.ResponseSubscribers.PathSubscriber;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class PathSubscriberExceptionTest {
    private static final Field OUT_FIELD = outField();

    @Test
    public void testOnNextException() throws Exception {
        Path tempFile = Files.createTempFile("testOnNextException", ".txt");
        try {
            PathSubscriber subscriber = newSubscriber(tempFile);
            CompletableFuture<Path> future = subscriber.getBody().toCompletableFuture();
            TestSubscription subscription = new TestSubscription();

            subscriber.onSubscribe(subscription);
            assertEquals(subscription.requested, 1L);

            out(subscriber).close();
            subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1})));

            assertEquals(subscription.requested, 1L);
            assertEquals(subscription.cancelCalls, 1);
            assertThrows(CompletionException.class, future::join);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testOnErrorCompletionOrder() throws Exception {
        Path tempFile = Files.createTempFile("testOnErrorCompletionOrder", ".txt");
        try {
            PathSubscriber subscriber = newSubscriber(tempFile);
            subscriber.onSubscribe(new TestSubscription());

            CompletableFuture<Path> future = subscriber.getBody().toCompletableFuture();
            AtomicBoolean openAtCompletion = new AtomicBoolean(true);
            FileChannel channel = out(subscriber);

            assertTrue(channel.isOpen());
            future.whenComplete((r, t) -> openAtCompletion.set(channel.isOpen()));

            subscriber.onError(new RuntimeException());
            assertThrows(CompletionException.class, future::join);

            assertFalse(openAtCompletion.get());
            assertFalse(channel.isOpen());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static PathSubscriber newSubscriber(Path path) {
        return PathSubscriber.create(path, List.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE));
    }

    private static FileChannel out(PathSubscriber subscriber) throws ReflectiveOperationException {
        return (FileChannel) OUT_FIELD.get(subscriber);
    }

    private static Field outField() {
        try {
            Field field = PathSubscriber.class.getDeclaredField("out");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final class TestSubscription implements Flow.Subscription {
        private long requested;
        private int cancelCalls;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
            cancelCalls++;
        }
    }
}
