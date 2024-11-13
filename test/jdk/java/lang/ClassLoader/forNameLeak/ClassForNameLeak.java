/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8151486
 * @summary Call Class.forName() on the system classloader from a class loaded
 *          from a custom classloader.
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.util.ForceGC
 *        jdk.test.lib.util.JarUtils
 * @build ClassForName ClassForNameLeak
 * @run main/othervm ClassForNameLeak
 */

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.Utils;
import jdk.test.lib.util.ForceGC;
import jdk.test.lib.util.JarUtils;

/*
 * Create .jar, load ClassForName from .jar using a URLClassLoader
 */
public class ClassForNameLeak {
    private static final long TIMEOUT = (long)(5000.0 * Utils.TIMEOUT_FACTOR);
    private static final int THREADS = 10;
    private static final Path jarFilePath = Paths.get("cfn.jar");

    static class TestLoader extends URLClassLoader {
        static URL[] toURLs() {
            try {
                return new URL[]{jarFilePath.toUri().toURL()};
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        TestLoader() {
            super("LeakedClassLoader", toURLs(), ClassLoader.getPlatformClassLoader());
        }
    }

    // Use a new classloader to load the ClassForName class, then run its
    // Runnable.
    static WeakReference<TestLoader> loadAndRun() {
        TestLoader classLoader = new TestLoader();
        try {
            Class<?> loadClass = Class.forName("ClassForName", true, classLoader);
            ((Runnable) loadClass.newInstance()).run();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return new WeakReference<>(classLoader);
    }

    public static void main(String... args) throws Exception {
        // create the JAR file
        setup();

        // Make simultaneous calls to the test method, to stress things a bit
        ExecutorService es = Executors.newFixedThreadPool(THREADS);

        List<Callable<WeakReference<TestLoader>>> callables =
                Stream.generate(() -> {
                    Callable<WeakReference<TestLoader>> cprcl = ClassForNameLeak::loadAndRun;
                    return cprcl;
                }).limit(THREADS).collect(Collectors.toList());

        for (Future<WeakReference<TestLoader>> future : es.invokeAll(callables)) {
            WeakReference<TestLoader> ref = future.get();
            if (!ForceGC.wait(() -> ref.refersTo(null))) {
                throw new RuntimeException(ref.get() + " not unloaded");
            }
        }
        es.shutdown();
    }

    private static final String CLASSFILENAME = "ClassForName.class";
    private static void setup() throws IOException {
        String testclasses = System.getProperty("test.classes", ".");

        // Create a temporary .jar file containing ClassForName.class
        Path testClassesDir = Paths.get(testclasses);
        JarUtils.createJarFile(jarFilePath, testClassesDir, CLASSFILENAME);
    }
}
