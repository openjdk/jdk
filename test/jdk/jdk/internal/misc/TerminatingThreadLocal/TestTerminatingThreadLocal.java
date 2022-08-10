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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/*
 * @test
 * @bug 8202788 8291897
 * @summary TerminatingThreadLocal unit test
 * @modules java.base/java.lang:+open java.base/jdk.internal.misc
 * @requires vm.continuations
 * @enablePreview
 * @run main/othervm TestTerminatingThreadLocal
 */
public class TestTerminatingThreadLocal {

    public static void main(String[] args) throws Exception {
        ttlTestSet(42, 112);
        ttlTestSet(null, 112);
        ttlTestSet(42, null);

        ttlTestVirtual(666, ThreadLocal::get, 666);
    }

    static <T> void ttlTestSet(T v0, T v1) {
        ttlTestPlatform(v0, ttl -> {                                         }    );
        ttlTestPlatform(v0, ttl -> { ttl.get();                              }, v0);
        ttlTestPlatform(v0, ttl -> { ttl.get();   ttl.remove();              }    );
        ttlTestPlatform(v0, ttl -> { ttl.get();   ttl.set(v1);               }, v1);
        ttlTestPlatform(v0, ttl -> { ttl.set(v1);                            }, v1);
        ttlTestPlatform(v0, ttl -> { ttl.set(v1); ttl.remove();              }    );
        ttlTestPlatform(v0, ttl -> { ttl.set(v1); ttl.remove(); ttl.get();   }, v0);
        ttlTestPlatform(v0, ttl -> { ttl.get();   ttl.remove(); ttl.set(v1); }, v1);
    }


    @SafeVarargs
    static <T> void ttlTestPlatform(T initialValue,
                                    Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                    T... expectedTerminatedValues) {
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
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!terminatedValues.equals(Arrays.asList(expectedTerminatedValues))) {
            throw new AssertionError("Expected terminated values: " +
                                     Arrays.toString(expectedTerminatedValues) +
                                     " but got: " + terminatedValues);
        }
    }

    @SafeVarargs
    static <T> void ttlTestVirtual(T initialValue,
                                   Consumer<? super TerminatingThreadLocal<T>> ttlOps,
                                   T... expectedTerminatedValues) throws Exception {
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

        // use a single worker thread pool as the "scheduler"
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

            if (!terminatedValues.isEmpty()) {
                throw new AssertionError("Unexpected terminated values after virtual thread terminated: " +
                                         terminatedValues);
            }
        }

        // wait for carrier to terminate
        Thread carrier = carrierRef.get();
        carrier.join();

        if (!terminatedValues.equals(Arrays.asList(expectedTerminatedValues))) {
            throw new AssertionError("Expected terminated values: " +
                                     Arrays.toString(expectedTerminatedValues) +
                                     " but got: " + terminatedValues);
        }
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     */
    private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
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
