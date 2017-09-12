/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /lib/testlibrary
 * @build jdk.testlibrary.Utils JarUtils
 * @build ClassForName ClassForNameLeak
 * @run main/othervm/policy=test.policy -Djava.security.manager ClassForNameLeak
 */

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.testlibrary.Utils;

/*
 * Create .jar, load ClassForName from .jar using a URLClassLoader
 */
public class ClassForNameLeak {
    private static final long TIMEOUT = (long)(5000.0 * Utils.TIMEOUT_FACTOR);
    private static final String TESTCLASSES = System.getProperty("test.classes", ".");
    private static final String CLASSFILENAME = "ClassForName.class";
    private static final int THREADS = 10;
    private static final ReferenceQueue<ClassLoader> rq = new ReferenceQueue<>();

    // Use a new classloader to load the ClassForName class, then run its
    // Runnable.
    public static PhantomReference<ClassLoader> loadAndRun(Path jarFilePath)
            throws Exception {
        ClassLoader classLoader = new URLClassLoader(
                new URL[]{jarFilePath.toUri().toURL()}) {
            @Override public String toString() { return "LeakedClassLoader"; }
        };

        Class<?> loadClass = Class.forName("ClassForName", true, classLoader);
        ((Runnable) loadClass.newInstance()).run();

        PhantomReference<ClassLoader> ref = new PhantomReference<>(classLoader, rq);
        System.out.println("returning phantom ref: " + ref + " to " + classLoader);
        return ref;
    }

    public static void main(final String[] args) throws Exception {
        // Create a temporary .jar file containing ClassForName.class
        Path testClassesDir = Paths.get(TESTCLASSES);
        Path jarFilePath = Files.createTempFile("cfn", ".jar");
        JarUtils.createJarFile(jarFilePath, testClassesDir, CLASSFILENAME);
        jarFilePath.toFile().deleteOnExit();

        // Remove the ClassForName.class file that jtreg built, to make sure
        // we're loading from the tmp .jar
        Path classFile = FileSystems.getDefault().getPath(TESTCLASSES,
                                                          CLASSFILENAME);
        Files.delete(classFile);

        // Make simultaneous calls to the test method, to stress things a bit
        ExecutorService es = Executors.newFixedThreadPool(THREADS);

        List<Callable<PhantomReference<ClassLoader>>> callables =
                Stream.generate(() -> {
                    Callable<PhantomReference<ClassLoader>> cprcl = () -> {
                        return loadAndRun(jarFilePath);
                    };
                    return cprcl;
                }).limit(THREADS).collect(Collectors.toList());

        List<Future<PhantomReference<ClassLoader>>> refs = es.invokeAll(callables);

        // Give the GC a chance to enqueue the PhantomReferences
        for (int i = 0; i < 10; i++) {
            System.gc();
        }
        // Make sure all PhantomReferences to the leaked classloader are enqueued
        for (int j = 0; j < THREADS; j++) {
            Reference rmRef = rq.remove(TIMEOUT);
            if (rmRef == null) {
                throw new RuntimeException("ClassLoader was never enqueued!");
            } else {
                System.out.println("Enqueued " + rmRef);
            }
        }
        System.out.println("All Classloaders successfully enqued");
    }
}
