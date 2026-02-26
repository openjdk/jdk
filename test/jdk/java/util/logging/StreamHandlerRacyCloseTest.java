/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8365483
 * @summary verify that concurrent calls to publish() and close() on a
 *          StreamHandler do not cause unexpected exceptions
 * @run junit StreamHandlerRacyCloseTest
 */
public class StreamHandlerRacyCloseTest {

    private static final class ExceptionTrackingErrorManager extends ErrorManager {
        private final AtomicReference<Exception> firstError = new AtomicReference<>();

        @Override
        public void error(String msg, Exception ex, int code) {
            // just track one/first exception, that's good enough for this test
            this.firstError.compareAndSet(null, new RuntimeException(msg, ex));
        }
    }

    @Test
    void testRacyClose() throws Exception {
        final int numTimes = 100;
        try (ExecutorService executor = Executors.newFixedThreadPool(numTimes)) {
            final List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 1; i <= numTimes; i++) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // construct a StreamHandler with an ErrorManager which propagates
                // any errors that happen during publish()
                final StreamHandler handler = new StreamHandler(baos, new SimpleFormatter());
                handler.setErrorManager(new ExceptionTrackingErrorManager());

                final CountDownLatch latch = new CountDownLatch(2);
                // create a publisher and closer task which will run concurrently
                tasks.add(new Publisher(handler, latch));
                tasks.add(new Closer(handler, latch));
            }
            // submit the tasks and expect successful completion of each
            final List<Future<Void>> completed = executor.invokeAll(tasks);
            for (var f : completed) {
                f.get();
            }
        }
    }

    private static final class Closer implements Callable<Void> {
        private final StreamHandler handler;
        private final CountDownLatch startLatch;

        private Closer(final StreamHandler handler, final CountDownLatch startLatch) {
            this.handler = handler;
            this.startLatch = startLatch;
        }

        @Override
        public Void call() throws Exception {
            // notify the other task of our readiness
            this.startLatch.countDown();
            // wait for the other task to arrive
            this.startLatch.await();
            // close the handler
            this.handler.close();
            // propagate any exception that may have been caught by the error manager
            final var errMgr = (ExceptionTrackingErrorManager) this.handler.getErrorManager();
            if (errMgr.firstError.get() != null) {
                throw errMgr.firstError.get();
            }
            return null;
        }
    }

    private static final class Publisher implements Callable<Void> {
        private final StreamHandler handler;
        private final CountDownLatch startLatch;

        private Publisher(final StreamHandler handler, final CountDownLatch startLatch) {
            this.handler = handler;
            this.startLatch = startLatch;
        }

        @Override
        public Void call() throws Exception {
            final LogRecord record = new LogRecord(Level.WARNING, "hello world");
            // notify the other task of our readiness
            this.startLatch.countDown();
            // wait for the other task to arrive
            this.startLatch.await();
            // publish the record
            this.handler.publish(record);
            // propagate any exception that may have been caught by the error manager
            final var errMgr = (ExceptionTrackingErrorManager) this.handler.getErrorManager();
            if (errMgr.firstError.get() != null) {
                throw errMgr.firstError.get();
            }
            return null;
        }
    }
}
