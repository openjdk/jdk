/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, BELLSOFT. All rights reserved.
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
 * LoadLibraryUnload class calls ClassLoader.loadedLibrary from multiple threads
 */
/*
 * The driver for this test is LoadLibraryUnloadTest.java.
 *
 * @bug 8266310
 * @summary Loads a native library from multiple class loaders and multiple
 *          threads. This creates a race for loading the library. The winner
 *          loads the library in two threads. All threads except two would fail
 *          with UnsatisfiedLinkError when the class being loaded is already
 *          loaded in a different class loader that won the race. The test
 *          checks that the loaded class is GC'ed, that means the class loader
 *          is GC'ed and the native library is unloaded.
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.lang.*;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import p.Class1;

public class LoadLibraryUnload {

    private static class TestLoader extends URLClassLoader {
        public TestLoader() throws Exception {
            super(new URL[] { Path.of(System.getProperty("test.classes")).toUri().toURL() });
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> clazz = findLoadedClass(name);
                if (clazz == null) {
                    try {
                        clazz = findClass(name);
                    } catch (ClassNotFoundException ignore) {
                    }
                    if (clazz == null) {
                        clazz = super.loadClass(name);
                    }
                }
                return clazz;
            }
        }
    }

    private static class LoadLibraryFromClass implements Runnable {
        Object object;
        Method method;
        Object canary;

        public LoadLibraryFromClass(Class<?> fromClass, Object canary) {
            try {
                this.object = fromClass.newInstance();
                this.method = fromClass.getDeclaredMethod("loadLibrary", Object.class);
                this.canary = canary;
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException(roe);
            }
        }

        @Override
        public void run() {
            try {
                method.invoke(object, canary);
            } catch (ReflectiveOperationException roe) {
                throw new RuntimeException(roe);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        int LOADER_COUNT = 5;
        List<Thread> threads = new ArrayList<>();
        Object[] canary = new Object[LOADER_COUNT];
        WeakReference<Object> wCanary[] = new WeakReference[LOADER_COUNT];
        ReferenceQueue<Object> refQueue = new ReferenceQueue<>();

        for (int i = 0 ; i < LOADER_COUNT ; i++) {
            // LOADER_COUNT loaders and 2X threads in total.
            // winner loads the library in 2 threads
            canary[i] = new Object();
            wCanary[i] = new WeakReference<>(canary[i], refQueue);

            Class<?> clazz = new TestLoader().loadClass("p.Class1");
            threads.add(new Thread(new LoadLibraryFromClass(clazz, canary[i])));
            threads.add(new Thread(new LoadLibraryFromClass(clazz, canary[i])));
        }

        final Set<Throwable> exceptions = ConcurrentHashMap.newKeySet();
        threads.forEach( t -> {
            t.setUncaughtExceptionHandler((th, ex) -> {
                // collect the root cause of each failure
                Throwable rootCause = ex;
                while((ex = ex.getCause()) != null) {
                    rootCause = ex;
                }
                exceptions.add(rootCause);
            });
            t.start();
        });

        // wait for all threads to finish
        for (Thread t : threads) {
            t.join();
        }

        // expect all errors to be UnsatisfiedLinkError
        boolean allAreUnsatisfiedLinkError = exceptions
                .stream()
                .map(e -> e instanceof UnsatisfiedLinkError)
                .reduce(true, (i, a) -> i && a);

        // expect exactly 8 errors
        int expectedErrorCount = (LOADER_COUNT - 1) * 2;
        Asserts.assertTrue(exceptions.size() == expectedErrorCount,
                "Expected to see " + expectedErrorCount + " failing threads");

        Asserts.assertTrue(allAreUnsatisfiedLinkError,
                "All errors have to be UnsatisfiedLinkError");

        // release strong refs
        threads = null;
        canary = null;
        exceptions.clear();
        // Wait for the canary for each of the libraries to be GC'd
        // before exiting the test.
        for (int i = 0; i < LOADER_COUNT; i++) {
            System.gc();
            var res = refQueue.remove(Utils.adjustTimeout(30 * 1000L));
            System.out.println(i + " dequeued: " + res);
            if (res == null) {
                Asserts.fail("Too few cleared WeakReferences");
            }
        }
        // Ensure the WeakReferences are strongly referenced until they can be dequeued
        Reference.reachabilityFence(wCanary);
    }
}
