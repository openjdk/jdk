/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8202788 8291897
 * @summary TerminatingThreadLocal unit test
 * @modules java.base/java.lang:+open java.base/jdk.internal.misc
 * @requires vm.continuations
 * @enablePreview
 * @run testng/othervm TestTerminatingThreadLocal
 */
public class TestTerminatingThreadLocal {

    @SuppressWarnings("unchecked")
    static <T> Object[] testCase(T v0, Consumer<? super TerminatingThreadLocal<T>> action, T... v1) {
        return new Object[] { v0, action, v1 };
    }

    @DataProvider
    public Object[][] testCases() {
        Integer NULL = null;
        return new Object[][] {
            testCase(42, ttl -> {                                             }     ),
            testCase(42, ttl -> { ttl.get();                                  }, 42 ),
            testCase(42, ttl -> { ttl.get();    ttl.remove();                 }     ),
            testCase(42, ttl -> { ttl.get();    ttl.set(112);                 }, 112),
            testCase(42, ttl -> { ttl.set(112);                               }, 112),
            testCase(42, ttl -> { ttl.set(112); ttl.remove();                 }     ),
            testCase(42, ttl -> { ttl.set(112); ttl.remove(); ttl.get();      }, 42 ),
            testCase(42, ttl -> { ttl.get();    ttl.remove(); ttl.set(112);   }, 112),

            testCase(NULL, ttl -> {                                           }      ),
            testCase(NULL, ttl -> { ttl.get();                                }, NULL),
            testCase(NULL, ttl -> { ttl.get();    ttl.remove();               }      ),
            testCase(NULL, ttl -> { ttl.get();    ttl.set(112);               }, 112 ),
            testCase(NULL, ttl -> { ttl.set(112);                             }, 112 ),
            testCase(NULL, ttl -> { ttl.set(112); ttl.remove();               }      ),
            testCase(NULL, ttl -> { ttl.set(112); ttl.remove(); ttl.get();    }, NULL),
            testCase(NULL, ttl -> { ttl.get();    ttl.remove(); ttl.set(112); }, 112 ),

            testCase(42, ttl -> { ttl.get();     ttl.set(NULL);               }, NULL),
            testCase(42, ttl -> { ttl.set(NULL);                              }, NULL),
            testCase(42, ttl -> { ttl.set(NULL); ttl.remove();                }      ),
            testCase(42, ttl -> { ttl.set(NULL); ttl.remove(); ttl.get();     }, 42  ),
            testCase(42, ttl -> { ttl.get();     ttl.remove(); ttl.set(NULL); }, NULL),
        };
    }

    /**
     * Test TerminatingThreadLocal with a platform thread.
     */
    @Test(dataProvider = "testCases")
    public <T> void ttlTestPlatform(T initialValue,
                                    Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                    T[] expectedTerminatedValues) throws Exception {
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

        assertEquals(terminatedValues, Arrays.asList(expectedTerminatedValues));
    }

    /**
     * Test TerminatingThreadLocal with a virtual thread. The thread local should be
     * carrier thread local but accessible to the virtual thread. The threadTerminated
     * method should be invoked when the carrier thread terminates.
     */
    @Test(dataProvider = "testCases")
    public <T> void ttlTestVirtual(T initialValue,
                                   Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                   T[] expectedTerminatedValues) throws Exception {
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

        var carrierRef = new AtomicReference<Thread>();

        // use a single worker thread pool for the cheduler
        try (var pool = Executors.newSingleThreadExecutor()) {

            // capture carrier Thread
            pool.submit(() -> carrierRef.set(Thread.currentThread()));

            ThreadFactory factory = virtualThreadBuilder(pool)
                    .name("ttl-test-virtual-", 0)
                    .allowSetThreadLocals(false)
                    .factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                executor.submit(() -> ttlOps.accept(ttl)).get();
            }

            assertTrue(terminatedValues.isEmpty(),
                       "Unexpected terminated values after virtual thread terminated");
        }

        // wait for carrier to terminate
        Thread carrier = carrierRef.get();
        carrier.join();

        assertEquals(terminatedValues, Arrays.asList(expectedTerminatedValues));
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     */
    static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
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
