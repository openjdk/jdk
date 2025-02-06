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

/*
 * @test
 * @bug     8349206
 * @summary j.u.l.Handler classes create deadlock risk via synchronized publish() method.
 * @modules java.base/sun.util.logging
 *          java.logging
 * @compile -XDignore.symbol.file LoggingDeadlock5.java
 * @run main/othervm/timeout=10 LoggingDeadlock5
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * This test verifies that logging Handler implementations no longer suffer
 * from the deadlock risk outlined in JDK-8349206.
 *
 * <p>This test should reliably cause, and detect, deadlocks in a timely
 * manner if the problem occurs, and SHOULD NOT time out.
 */
public class LoggingDeadlock5 {

    // Formatter which calls toString() on all arguments.
    private static final Formatter TEST_FORMATTER = new Formatter() {
        @Override
        public String format(LogRecord record) {
            // All we care about is that our formatter will invoke toString() on user arguments.
            for (Object p : record.getParameters()) {
                var unused = p.toString();
            }
            return "<formatted string>";
        }
    };

    // A handler which *should* cause a deadlock by synchronizing publish().
    private static final Handler SELF_TEST_HANDLER = new Handler() {
        @Override
        public synchronized void publish(LogRecord record) {
            TEST_FORMATTER.formatMessage(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public static void main(String[] args) throws InterruptedException, IOException {
        // Self test that deadlocks are correct caught (and don't lock the entire test).
        new DeadLocker(SELF_TEST_HANDLER).checkDeadlock(true);

        // In theory, we should test FileHandler and SocketHandler here as well
        // because, while they are just subclasses of StreamHandler, they could
        // be adding locking around the call to super.publish(). However, they
        // are quite problematic in a test environment since they need to create
        // files and open sockets. They are not being tested for now.
        StreamHandler streamHandler = new StreamHandler(new ByteArrayOutputStream(0), TEST_FORMATTER);
        streamHandler.setEncoding(StandardCharsets.UTF_8.name());
        new DeadLocker(streamHandler).checkDeadlock(false);

        // Any other problematic handler classes can be easily added here if
        // they are simple enough to be constructed in a test environment.
    }

    private static class DeadLocker {
        private final static Duration JOIN_WAIT = Duration.ofMillis(500);

        private final Semaphore readyToDeadlock = new Semaphore(0);
        private final Semaphore userLockIsHeld = new Semaphore(0);
        private final Logger logger = Logger.getAnonymousLogger();
        private final Handler handlerUnderTest;
        private final Object userLock = new Object();
        private boolean deadlockEncountered = true;

        DeadLocker(Handler handlerUnderTest) {
            this.handlerUnderTest = handlerUnderTest;

            this.logger.setUseParentHandlers(false);
            this.logger.addHandler(handlerUnderTest);
            this.logger.setLevel(Level.INFO);
            this.handlerUnderTest.setLevel(Level.INFO);
        }

        void checkDeadlock(boolean expectDeadlock) throws InterruptedException {
            // Note: Even though the message format string isn't used in the
            // test formatter, it must be a valid log format string (Logger
            // detects if there are no "{n}" placeholders and skips calling
            // the formatter otherwise).
            Thread t1 = runAsDaemon(() -> logger.log(Level.INFO, "Hello {0}", new Argument()));
            readyToDeadlock.acquireUninterruptibly();
            // First thread is blocked until userLockIsHeld is released in t2.
            Thread t2 = runAsDaemon(this::locksThenLogs);

            // If deadlock occurs, the join() calls both return false.
            int threadsBlocked = 0;
            if (!t1.join(JOIN_WAIT)) {
                threadsBlocked += 1;
            }
            if (!t2.join(JOIN_WAIT)) {
                threadsBlocked += 1;
            }
            // These indicate test problems, not a failure of the code under test.
            errorIf(threadsBlocked == 1, "Inconsistent number of blocked threads.");
            errorIf(deadlockEncountered != (threadsBlocked == 2),
                    "Deadlock reporting should coincide with number of blocked threads.");

            // This is the actual test assertion.
            if (expectDeadlock != deadlockEncountered) {
                String issue = expectDeadlock ? "Expected deadlock but none occurred" : "Unexpected deadlock";
                throw new AssertionError(errorMsg(issue));
            }
        }

        void locksThenLogs() {
            synchronized (userLock) {
                userLockIsHeld.release();
                logger.log(Level.INFO, "This will cause a deadlock if the Handler locks!");
            }
        }

        void calledFromToString() {
            this.readyToDeadlock.release();
            this.userLockIsHeld.acquireUninterruptibly();
            synchronized (userLock) {
                this.deadlockEncountered = false;
            }
        }

        class Argument {
            @Override
            public String toString() {
                calledFromToString();
                return "<to string>";
            }
        }

        String errorMsg(String msg) {
            return String.format("Handler deadlock test [%s]: %s", handlerUnderTest.getClass().getName(), msg);
        }

        void errorIf(boolean condition, String msg) {
            if (condition) {
                throw new RuntimeException(errorMsg("TEST ERROR - " + msg));
            }
        }
    }

    static Thread runAsDaemon(Runnable job) {
        Thread thread = new Thread(job);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
