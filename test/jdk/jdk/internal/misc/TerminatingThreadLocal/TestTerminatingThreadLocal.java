/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.TerminatingThreadLocal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8202788 8291897 8357637
 * @summary TerminatingThreadLocal unit test
 * @modules java.base/java.lang:+open java.base/jdk.internal.misc
 * @requires vm.continuations
 * @run testng/othervm TestTerminatingThreadLocal
 */
public class TestTerminatingThreadLocal {

    @SafeVarargs
    static <T> Object[] testCase(T initialValue,
                                 Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                 T... expectedTerminatedValues) {
        return new Object[] {initialValue, ttlOps, Arrays.asList(expectedTerminatedValues)};
    }

    static <T> Stream<Object[]> testCases(T v0, T v1) {
        return Stream.of(
            testCase(v0, ttl -> {                                         }    ),
            testCase(v0, ttl -> { ttl.get();                              }, v0),
            testCase(v0, ttl -> { ttl.get();   ttl.remove();              }    ),
            testCase(v0, ttl -> { ttl.get();   ttl.set(v1);               }, v1),
            testCase(v0, ttl -> { ttl.set(v1);                            }, v1),
            testCase(v0, ttl -> { ttl.set(v1); ttl.remove();              }    ),
            testCase(v0, ttl -> { ttl.set(v1); ttl.remove(); ttl.get();   }, v0),
            testCase(v0, ttl -> { ttl.get();   ttl.remove(); ttl.set(v1); }, v1)
        );
    }

    @DataProvider
    public Object[][] testCases() {
        return Stream.of(
            testCases(42, 112),
            testCases(null, new Object()),
            testCases("abc", null)
        ).flatMap(Function.identity()).toArray(Object[][]::new);
    }

    /**
     * Test TerminatingThreadLocal with a platform thread.
     */
    @Test(dataProvider = "testCases")
    public <T> void ttlTestPlatform(T initialValue,
                                    Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                    List<T> expectedTerminatedValues) throws Exception {
        List<T> terminatedValues = new CopyOnWriteArrayList<>();

        TerminatingThreadLocal<T> ttl = new TerminatingThreadLocal<>() {
            @Override
            protected void threadTerminated(T value) {
                terminatedValues.add(value);
            }

            @Override
            protected T initialValue() {
                return initialValue;
            }
        };

        Thread thread = new Thread(() -> ttlOps.accept(ttl), "ttl-test-platform");
        thread.start();
        thread.join();

        assertEquals(terminatedValues, expectedTerminatedValues);
    }

    /**
     * Test TerminatingThreadLocal with a virtual thread. The thread local should be
     * carrier thread local but accessible to the virtual thread. The threadTerminated
     * method should be invoked when the carrier thread terminates.
     */
    @Test(dataProvider = "testCases")
    public <T> void ttlTestVirtual(T initialValue,
                                   Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                   List<T> expectedTerminatedValues) throws Exception {
        List<T> terminatedValues = new CopyOnWriteArrayList<>();

        TerminatingThreadLocal<T> ttl = new TerminatingThreadLocal<>() {
            @Override
            protected void threadTerminated(T value) {
                terminatedValues.add(value);
            }

            @Override
            protected T initialValue() {
                return initialValue;
            }
        };

        Thread carrier;

        // use a single worker thread pool for the cheduler
        try (var pool = Executors.newSingleThreadExecutor()) {

            // capture carrier Thread
            carrier = pool.submit(Thread::currentThread).get();

            ThreadFactory factory = virtualThreadBuilder(pool)
                    .name("ttl-test-virtual-", 0)
                    .factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                executor.submit(() -> ttlOps.accept(ttl)).get();
            }

            assertTrue(terminatedValues.isEmpty(),
                       "Unexpected terminated values after virtual thread terminated");
        }

        // wait for carrier to terminate
        carrier.join();

        assertEquals(terminatedValues, expectedTerminatedValues);
    }

    /**
     * Test TerminatingThreadLocal when thread locals are "cleared" by null'ing the
     * threadLocal field of the current Thread.
     */
    @Test
    public void testClearingThreadLocals() throws Throwable {
        var terminatedValues = new CopyOnWriteArrayList<Object>();

        var tl = new ThreadLocal<String>();
        var ttl = new TerminatingThreadLocal<String>() {
            @Override
            protected void threadTerminated(String value) {
                terminatedValues.add(value);
            }
        };
        var throwableRef = new AtomicReference<Throwable>();

        String tlValue = "abc";
        String ttlValue = "xyz";

        Thread thread = Thread.ofPlatform().start(() -> {
            try {
                tl.set(tlValue);
                ttl.set(ttlValue);

                assertEquals(tl.get(), tlValue);
                assertEquals(ttl.get(), ttlValue);

                // set Thread.threadLocals to null
                Field f = Thread.class.getDeclaredField("threadLocals");
                f.setAccessible(true);
                f.set(Thread.currentThread(), null);

                assertNull(tl.get());
                assertEquals(ttl.get(), ttlValue);
            } catch (Throwable t) {
                throwableRef.set(t);
            }
        });
        thread.join();
        if (throwableRef.get() instanceof Throwable t) {
            throw t;
        }

        assertEquals(terminatedValues, List.of(ttlValue));
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
