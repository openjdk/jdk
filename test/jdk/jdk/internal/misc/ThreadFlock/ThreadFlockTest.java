/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for ThreadFlock
 * @modules java.base/jdk.internal.misc
 * @compile --enable-preview -source ${jdk.version} ThreadFlockTest.java
 * @run testng/othervm --enable-preview ThreadFlockTest
 */

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jdk.internal.misc.ThreadFlock;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ThreadFlockTest {
    private ScheduledExecutorService scheduler;

    @BeforeClass
    public void setUp() throws Exception {
        ThreadFactory factory = (task) -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @AfterClass
    public void tearDown() {
        scheduler.shutdown();
    }

    @DataProvider(name = "factories")
    public Object[][] factories() {
        var defaultThreadFactory = Executors.defaultThreadFactory();
        var virtualThreadFactory = Thread.ofVirtual().factory();
        return new Object[][] {
                { defaultThreadFactory, },
                { virtualThreadFactory, },
        };
    }

    /**
     * Test ThreadFlock::name.
     */
    @Test
    public void testName() {
        try (var flock = ThreadFlock.open(null)) {
            assertEquals(flock.name(), null);
            flock.close();
            assertEquals(flock.name(), null);  // after close
        }
        try (var flock = ThreadFlock.open("fetcher")) {
            assertEquals(flock.name(), "fetcher");
            flock.close();
            assertEquals(flock.name(), "fetcher");  // after close
        }
    }

    /**
     * Test ThreadFlock::owner.
     */
    @Test
    public void testOwner() {
        try (var flock = ThreadFlock.open(null)) {
            assertTrue(flock.owner() == Thread.currentThread());
            flock.close();
            assertTrue(flock.owner() == Thread.currentThread());  // after close
        }
    }

    /**
     * Test ThreadFlock::isXXXX methods.
     */
    @Test
    public void testState() {
        try (var flock = ThreadFlock.open(null)) {
            assertFalse(flock.isShutdown());
            assertFalse(flock.isClosed());
            flock.close();
            assertTrue(flock.isShutdown());
            assertTrue(flock.isClosed());
        }
        try (var flock = ThreadFlock.open(null)) {
            flock.shutdown();
            assertTrue(flock.isShutdown());
            assertFalse(flock.isClosed());
            flock.close();
            assertTrue(flock.isShutdown());
            assertTrue(flock.isClosed());
        }
    }

    /**
     * Test ThreadFlock::threads enumerates all threads.
     */
    @Test(dataProvider = "factories")
    public void testThreads(ThreadFactory factory) {
        CountDownLatch latch = new CountDownLatch(1);
        var exception = new AtomicReference<Exception>();
        Runnable awaitLatch = () -> {
            try {
                latch.await();
            } catch (Exception e) {
                exception.compareAndSet(null, e);
            }
        };

        var flock = ThreadFlock.open(null);
        try {
            assertTrue(flock.threads().count() == 0);

            // start 100 threads
            Set<Thread> threads = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                Thread thread = factory.newThread(awaitLatch);
                flock.start(thread);
                threads.add(thread);
            }

            // check thread ThreadFlock::threads enumerates all threads
            assertEquals(threads, flock.threads().collect(Collectors.toSet()));

        } finally {
            latch.countDown();  // release threads
            flock.close();
        }
        assertTrue(flock.threads().count() == 0);
        assertTrue(exception.get() == null);
    }

    /**
     * Test ThreadFlock::containsThread with nested flocks.
     */
    @Test(dataProvider = "factories")
    public void testContainsThread1(ThreadFactory factory) {
        CountDownLatch latch = new CountDownLatch(1);
        var exception = new AtomicReference<Exception>();

        Runnable awaitLatch = () -> {
            try {
                latch.await();
            } catch (Exception e) {
                exception.compareAndSet(null, e);
            }
        };

        try (var flock1 = ThreadFlock.open(null)) {
            var flock2 = ThreadFlock.open(null);
            try {
                Thread currentThread = Thread.currentThread();
                assertFalse(flock1.containsThread(currentThread));
                assertFalse(flock2.containsThread(currentThread));

                // start thread1 in flock1
                Thread thread1 = factory.newThread(awaitLatch);
                flock1.start(thread1);

                // start thread2 in flock2
                Thread thread2 = factory.newThread(awaitLatch);
                flock2.start(thread2);

                assertTrue(flock1.containsThread(thread1));
                assertTrue(flock1.containsThread(thread2));
                assertFalse(flock2.containsThread(thread1));
                assertTrue(flock2.containsThread(thread2));

            } finally {
                latch.countDown();   // release threads
                flock2.close();
            }
        }
    }

    /**
     * Test ThreadFlock::containsThread with a tree of flocks.
     */
    @Test(dataProvider = "factories")
    public void testContainsThread2(ThreadFactory factory) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        var exception = new AtomicReference<Exception>();

        Runnable awaitLatch = () -> {
            try {
                latch.await();
            } catch (Exception e) {
                exception.compareAndSet(null, e);
            }
        };

        try (var flock1 = ThreadFlock.open(null)) {

            // use box to publish to enclosing scope
            class Box {
                volatile ThreadFlock flock2;
                volatile Thread thread2;
            }
            var box = new Box();

            // flock1 will be "parent" of flock2
            Thread thread1 = factory.newThread(() -> {
                try (var flock2 = ThreadFlock.open(null)) {
                    Thread thread2 = factory.newThread(awaitLatch);
                    flock2.start(thread2);
                    box.flock2 = flock2;
                    box.thread2 = thread2;
                }
            });
            flock1.start(thread1);

            // wait for thread2 to start
            ThreadFlock flock2;
            Thread thread2;
            while ((flock2 = box.flock2) == null || (thread2 = box.thread2) == null) {
                Thread.sleep(20);
            }

            try {
                assertTrue(flock1.containsThread(thread1));
                assertTrue(flock1.containsThread(thread2));
                assertFalse(flock2.containsThread(thread1));
                assertTrue(flock2.containsThread(thread2));
            } finally {
                latch.countDown();   // release threads
            }
        }
    }

    /**
     * Test that start causes a thread to execute.
     */
    @Test(dataProvider = "factories")
    public void testStart(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            AtomicBoolean executed = new AtomicBoolean();
            Thread thread = factory.newThread(() -> executed.set(true));
            assertTrue(flock.start(thread) == thread);
            thread.join();
            assertTrue(executed.get());
        }
    }

    /**
     * Test that start throws IllegalStateException when shutdown
     */
    @Test(dataProvider = "factories")
    public void testStartAfterShutdown(ThreadFactory factory) {
        try (var flock = ThreadFlock.open(null)) {
            flock.shutdown();
            Thread thread = factory.newThread(() -> { });
            expectThrows(IllegalStateException.class, () -> flock.start(thread));
        }
    }

    /**
     * Test that start throws IllegalStateException when closed
     */
    @Test(dataProvider = "factories")
    public void testStartAfterClose(ThreadFactory factory) {
        var flock = ThreadFlock.open(null);
        flock.close();;
        Thread thread = factory.newThread(() -> { });
        expectThrows(IllegalStateException.class, () -> flock.start(thread));
    }

    /**
     * Test that start throws IllegalThreadStateException when invoked to
     * start a thread that has already started.
     */
    @Test(dataProvider = "factories")
    public void testStartAfterStarted(ThreadFactory factory) {
        try (var flock = ThreadFlock.open(null)) {
            Thread thread = factory.newThread(() -> { });
            flock.start(thread);
            expectThrows(IllegalThreadStateException.class, () -> flock.start(thread));
        }
    }

    /**
     * Test start is confined to threads in the flock.
     */
    @Test(dataProvider = "factories")
    public void testStartConfined(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            // thread in flock
            testStartConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                return flock.start(thread);
            });

            // thread in flock
            try (var flock2 = ThreadFlock.open(null)) {
                testStartConfined(flock, task -> {
                    Thread thread = factory.newThread(task);
                    return flock2.start(thread);
                });
            }

            // thread not contained in flock
            testStartConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                thread.start();
                return thread;
            });
        }
    }

    /**
     * Test that a thread created with the given factory cannot start a thread
     * in the given flock.
     */
    private void testStartConfined(ThreadFlock flock,
                                   Function<Runnable, Thread> factory) throws Exception {
        var exception = new AtomicReference<Exception>();
        Thread thread = factory.apply(() -> {
            try {
                Thread t = Thread.ofVirtual().unstarted(() -> { });
                flock.start(t);
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.join();
        Throwable cause = exception.get();
        if (flock.containsThread(thread)) {
            assertTrue(cause == null);
        } else {
            assertTrue(cause instanceof WrongThreadException);
        }
    }

    /**
     * Test awaitAll with no threads.
     */
    @Test
    public void testAwaitAllWithNoThreads() throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            assertTrue(flock.awaitAll());
            assertTrue(flock.awaitAll(Duration.ofSeconds(1)));
        }
    }

    /**
     * Test awaitAll with threads running.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllWithThreads(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            AtomicBoolean done = new AtomicBoolean();
            Runnable task = () -> {
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                    done.set(true);
                } catch (InterruptedException e) { }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);
            assertTrue(flock.awaitAll());
            assertTrue(done.get());
        }
    }

    /**
     * Test awaitAll with timeout, threads finish before timeout expires.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllWithTimeout1(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            Runnable task = () -> {
                try {
                    Thread.sleep(Duration.ofSeconds(2));
                } catch (InterruptedException e) { }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);

            long startMillis = millisTime();
            boolean done = flock.awaitAll(Duration.ofSeconds(30));
            assertTrue(done);
            checkDuration(startMillis, 1900, 4000);
        }
    }

    /**
     * Test awaitAll with timeout, timeout expires before threads finish.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllWithTimeout2(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            Runnable task = () -> {
                try {
                    Thread.sleep(Duration.ofSeconds(30));
                } catch (InterruptedException e) { }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);

            try {
                long startMillis = millisTime();
                try {
                    flock.awaitAll(Duration.ofSeconds(2));
                    fail();
                } catch (TimeoutException e) {
                    checkDuration(startMillis, 1900, 4000);
                }
            } finally {
                thread.interrupt();
            }
        }
    }

    /**
     * Test awaitAll with timeout many times.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllWithTimeout3(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            Runnable task = () -> {
                try {
                    Thread.sleep(Duration.ofSeconds(30));
                } catch (InterruptedException e) { }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);

            try {
                for (int i = 0; i < 3; i++) {
                    try {
                        flock.awaitAll(Duration.ofSeconds(1));
                        fail();
                    } catch (TimeoutException expected) { }
                }
            } finally {
                thread.interrupt();
            }

            boolean done = flock.awaitAll();
            assertTrue(done);
        }
    }

    /**
     * Test awaitAll with a 0 or negative timeout.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllWithTimeout4(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            Runnable task = () -> {
                try {
                    Thread.sleep(Duration.ofSeconds(30));
                } catch (InterruptedException e) { }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);

            try {
                try {
                    flock.awaitAll(Duration.ofSeconds(0));
                    fail();
                } catch (TimeoutException expected) { }
                try {
                    flock.awaitAll(Duration.ofSeconds(-1));
                    fail();
                } catch (TimeoutException expected) { }
            } finally {
                thread.interrupt();
            }

            boolean done = flock.awaitAll();
            assertTrue(done);
        }
    }

    /**
     * Test awaitAll with interrupt status set, should interrupt thread.
     */
    @Test(dataProvider = "factories")
    public void testInterruptAwaitAll1(ThreadFactory factory) {
        CountDownLatch latch = new CountDownLatch(1);
        var exception = new AtomicReference<Exception>();
        Runnable awaitLatch = () -> {
            try {
                latch.await();
            } catch (Exception e) {
                exception.compareAndSet(null, e);
            }
        };

        var flock = ThreadFlock.open(null);
        try {
            Thread thread = factory.newThread(awaitLatch);
            flock.start(thread);

            // invoke awaitAll with interrupt status set.
            Thread.currentThread().interrupt();
            try {
                flock.awaitAll();
                fail();
            } catch (InterruptedException e) {
                // interrupt status should be clear
                assertFalse(Thread.currentThread().isInterrupted());
            }

            // invoke awaitAll(Duration) with interrupt status set.
            Thread.currentThread().interrupt();
            try {
                flock.awaitAll(Duration.ofSeconds(30));
                fail();
            } catch (TimeoutException e) {
                fail();
            } catch (InterruptedException e) {
                // interrupt status should be clear
                assertFalse(Thread.currentThread().isInterrupted());
            }

        } finally {
            Thread.interrupted();  // clear interrupt

            latch.countDown();
            flock.close();
        }

        // thread should not have throw any exception
        assertTrue(exception.get() == null);
    }

    /**
     * Test interrupt of awaitAll.
     */
    @Test(dataProvider = "factories")
    public void testInterruptAwaitAll2(ThreadFactory factory) {
        CountDownLatch latch = new CountDownLatch(1);
        var exception = new AtomicReference<Exception>();
        Runnable awaitLatch = () -> {
            try {
                latch.await();
            } catch (Exception e) {
                exception.compareAndSet(null, e);
            }
        };

        var flock = ThreadFlock.open(null);
        try {
            Thread thread = factory.newThread(awaitLatch);
            flock.start(thread);

            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                flock.awaitAll();
                fail();
            } catch (InterruptedException e) {
                // interrupt status should be clear
                assertFalse(Thread.currentThread().isInterrupted());
            }

            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
            try {
                flock.awaitAll(Duration.ofSeconds(30));
                fail();
            } catch (TimeoutException e) {
                fail();
            } catch (InterruptedException e) {
                // interrupt status should be clear
                assertFalse(Thread.currentThread().isInterrupted());
            }

        } finally {
            Thread.interrupted();  // clear interrupt

            latch.countDown();
            flock.close();
        }

        // thread should not have throw any exception
        assertTrue(exception.get() == null);
    }

    /**
     * Test awaitAll after close.
     */
    @Test
    public void testAwaitAfterClose() throws Exception {
        var flock = ThreadFlock.open(null);
        flock.close();
        assertTrue(flock.awaitAll());
        assertTrue(flock.awaitAll(Duration.ofSeconds(1)));
    }

    /**
     * Test awaitAll is flock confined.
     */
    @Test(dataProvider = "factories")
    public void testAwaitAllConfined(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            // thread in flock
            testAwaitAllConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                return flock.start(thread);
            });

            // thread not in flock
            testAwaitAllConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                thread.start();
                return thread;
            });
        }
    }

    /**
     * Test that a thread created with the given factory cannot call awaitAll.
     */
    private void testAwaitAllConfined(ThreadFlock flock,
                                      Function<Runnable, Thread> factory) throws Exception {
        var exception = new AtomicReference<Exception>();
        Thread thread = factory.apply(() -> {
            try {
                flock.awaitAll();
                flock.awaitAll(Duration.ofMillis(1));
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.join();
        Throwable cause = exception.get();
        assertTrue(cause instanceof WrongThreadException);
    }

    /**
     * Test awaitAll with the wakeup permit.
     */
    @Test(dataProvider = "factories")
    public void testWakeupAwaitAll1(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            CountDownLatch latch = new CountDownLatch(1);
            var exception = new AtomicReference<Exception>();
            Runnable task = () -> {
                try {
                    latch.await();
                } catch (Exception e) {
                    exception.compareAndSet(null, e);
                }
            };
            Thread thread = factory.newThread(task);
            flock.start(thread);

            // invoke awaitAll with permit
            try {
                flock.wakeup();
                assertFalse(flock.awaitAll());
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Schedule a thread to wakeup the owner waiting in awaitAll.
     */
    @Test(dataProvider = "factories")
    public void testWakeupAwaitAll2(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            CountDownLatch latch = new CountDownLatch(1);
            var exception = new AtomicReference<Exception>();
            Runnable task = () -> {
                try {
                    latch.await();
                } catch (Exception e) {
                    exception.compareAndSet(null, e);
                }
            };
            Thread thread1 = factory.newThread(task);
            flock.start(thread1);

            // schedule thread to invoke wakeup
            Thread thread2 = factory.newThread(() -> {
                try { Thread.sleep(Duration.ofSeconds(1)); } catch (Exception e) { }
                flock.wakeup();
            });
            flock.start(thread2);

            try {
                assertFalse(flock.awaitAll());
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * Test wakeup is flock confined.
     */
    @Test(dataProvider = "factories")
    public void testWakeupConfined(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            // thread in flock
            testWakeupConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                return flock.start(thread);
            });

            // thread not in flock
            testWakeupConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                thread.start();
                return thread;
            });
        }
    }

    /**
     * Test that a thread created with the given factory cannot wakeup the
     * given flock.
     */
    private void testWakeupConfined(ThreadFlock flock,
                                    Function<Runnable, Thread> factory) throws Exception {
        var exception = new AtomicReference<Exception>();
        Thread thread = factory.apply(() -> {
            try {
                flock.wakeup();
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.join();
        Throwable cause = exception.get();
        if (flock.containsThread(thread)) {
            assertTrue(cause == null);
        } else {
            assertTrue(cause instanceof WrongThreadException);
        }
    }

    /**
     * Test close with no threads running.
     */
    @Test
    public void testCloseWithNoThreads() {
        var flock = ThreadFlock.open(null);
        flock.close();
        assertTrue(flock.isClosed());
        assertTrue(flock.threads().count() == 0);
    }

    /**
     * Test close with threads running.
     */
    @Test(dataProvider = "factories")
    public void testCloseWithThreads(ThreadFactory factory) {
        var exception = new AtomicReference<Exception>();
        Runnable sleepTask = () -> {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (Exception e) {
                exception.set(e);
            }
        };
        var flock = ThreadFlock.open(null);
        try {
            Thread thread = factory.newThread(sleepTask);
            flock.start(thread);
        } finally {
            flock.close();
        }
        assertTrue(flock.isClosed());
        assertTrue(flock.threads().count() == 0);
        assertTrue(exception.get() == null); // no exception thrown
    }

    /**
     * Test close after flock is closed.
     */
    @Test
    public void testCloseAfterClose() {
        var flock = ThreadFlock.open(null);
        flock.close();
        assertTrue(flock.isClosed());
        flock.close();
        assertTrue(flock.isClosed());
    }

    /**
     * Test close is owner confined.
     */
    @Test(dataProvider = "factories")
    public void testCloseConfined(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            // thread in flock
            testCloseConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                return flock.start(thread);
            });

            // thread not in flock
            testCloseConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                thread.start();
                return thread;
            });
        }
    }

    /**
     * Test that a thread created with the given factory cannot close the
     * given flock.
     */
    private void testCloseConfined(ThreadFlock flock,
                                   Function<Runnable, Thread> factory) throws Exception {
        var exception = new AtomicReference<Exception>();
        Thread thread = factory.apply(() -> {
            try {
                flock.close();
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.join();
        Throwable cause = exception.get();
        assertTrue(cause instanceof WrongThreadException);
    }

    /**
     * Test close with interrupt status set, should not interrupt threads.
     */
    @Test(dataProvider = "factories")
    public void testInterruptClose1(ThreadFactory factory) {
        var exception = new AtomicReference<Exception>();
        Runnable sleepTask = () -> {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (Exception e) {
                exception.set(e);
            }
        };
        try (var flock = ThreadFlock.open(null)) {
            Thread thread = factory.newThread(sleepTask);
            flock.start(thread);
            Thread.currentThread().interrupt();
        } finally {
            assertTrue(Thread.interrupted());  // clear interrupt
        }
        assertTrue(exception.get() == null);
    }

    /**
     * Test interrupt thread block in close.
     */
    @Test(dataProvider = "factories")
    public void testInterruptClose2(ThreadFactory factory) {
        var exception = new AtomicReference<Exception>();
        Runnable sleepTask = () -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (Exception e) {
                exception.set(e);
            }
        };
        try (var flock = ThreadFlock.open(null)) {
            Thread thread = factory.newThread(sleepTask);
            flock.start(thread);
            scheduleInterrupt(Thread.currentThread(), Duration.ofMillis(500));
        } finally {
            assertTrue(Thread.interrupted());  // clear interrupt
        }
        assertTrue(exception.get() == null);
    }

    /**
     * Test shutdown is confined to threads in the flock.
     */
    @Test(dataProvider = "factories")
    public void testShutdownConfined(ThreadFactory factory) throws Exception {
        try (var flock = ThreadFlock.open(null)) {
            // thread in flock
            testShutdownConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                return flock.start(thread);
            });

            // thread in flock
            try (var flock2 = ThreadFlock.open(null)) {
                testShutdownConfined(flock, task -> {
                    Thread thread = factory.newThread(task);
                    return flock2.start(thread);
                });
            }

            // thread not contained in flock
            testShutdownConfined(flock, task -> {
                Thread thread = factory.newThread(task);
                thread.start();
                return thread;
            });
        }
    }

    /**
     * Test that a thread created with the given factory cannot shut down the
     * given flock.
     */
    private void testShutdownConfined(ThreadFlock flock,
                                      Function<Runnable, Thread> factory) throws Exception {
        var exception = new AtomicReference<Exception>();
        Thread thread = factory.apply(() -> {
            try {
                flock.shutdown();
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.join();
        Throwable cause = exception.get();
        if (flock.containsThread(thread)) {
            assertTrue(cause == null);
        } else {
            assertTrue(cause instanceof WrongThreadException);
        }
    }

    /**
     * Test that closing an enclosing thread flock closes a nested thread flocks.
     */
    @Test
    public void testStructureViolation() {
        try (var flock1 = ThreadFlock.open("flock1")) {
            try (var flock2 = ThreadFlock.open("flock2")) {
                try {
                    flock1.close();
                    fail();
                } catch (RuntimeException e) {
                    assertTrue(e.toString().contains("Structure"));
                }
                assertTrue(flock1.isClosed());
                assertTrue(flock2.isClosed());
            }
        }
    }

    /**
     * Test Thread exiting with an open flock. The exiting thread should close the flock.
     */
    @Test(dataProvider = "factories")
    public void testThreadExitWithOpenFlock(ThreadFactory factory) throws Exception {
        var flockRef = new AtomicReference<ThreadFlock>();
        var childRef = new AtomicReference<Thread>();

        Thread thread = factory.newThread(() -> {
            Thread.dumpStack();

            var flock = ThreadFlock.open(null);
            Thread child = factory.newThread(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(2));
                } catch (InterruptedException e) { }
            });
            flock.start(child);
            flockRef.set(flock);
            childRef.set(child);
        });
        thread.start();
        thread.join();

        // flock should be closed and the child thread should have terminated
        ThreadFlock flock = flockRef.get();
        Thread child = childRef.get();
        assertTrue(flock.isClosed() && child.join(Duration.ofMillis(500)));
    }

    /**
     * Test toString includes the flock name.
     */
    @Test
    public void testToString() {
        try (var flock = ThreadFlock.open("xxxx")) {
            assertTrue(flock.toString().contains("xxx"));
        }
    }

    /**
     * Test for NullPointerException.
     */
    @Test
    public void testNulls() {
        try (var flock = ThreadFlock.open(null)) {
            expectThrows(NullPointerException.class, () -> flock.start(null));
            expectThrows(NullPointerException.class, () -> flock.awaitAll(null));
            expectThrows(NullPointerException.class, () -> flock.containsThread(null));
        }
    }

    /**
     * Schedules a thread to be interrupted after the given delay.
     */
    private void scheduleInterrupt(Thread thread, Duration delay) {
        long millis = delay.toMillis();
        scheduler.schedule(thread::interrupt, millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task
     * @param start start time, in milliseconds
     * @param min minimum expected duration, in milliseconds
     * @param max maximum expected duration, in milliseconds
     * @return the duration (now - start), in milliseconds
     */
    private static long checkDuration(long start, long min, long max) {
        long duration = millisTime() - start;
        assertTrue(duration >= min,
                "Duration " + duration + "ms, expected >= " + min + "ms");
        assertTrue(duration <= max,
                "Duration " + duration + "ms, expected <= " + max + "ms");
        return duration;
    }
}
