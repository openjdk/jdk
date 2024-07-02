/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * To use ClassUnloadCommon from a sub-process, see test/hotspot/jtreg/runtime/logging/ClassLoadUnloadTest.java
 * for an example.
 */


package jdk.test.lib.classloader;
import jdk.test.whitebox.WhiteBox;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ClassUnloadCommon {
    public static class TestFailure extends RuntimeException {
        TestFailure(String msg) {
            super(msg);
        }
    }

    public static void failIf(boolean value, String msg) {
        if (value) throw new TestFailure("Test failed: " + msg);
    }

    private static volatile Object dummy = null;
    private static void allocateMemory(int kilobytes) {
        ArrayList<byte[]> l = new ArrayList<>();
        dummy = l;
        for (int i = kilobytes; i > 0; i -= 1) {
            l.add(new byte[1024]);
        }
        l = null;
        dummy = null;
    }

    public static void triggerUnloading() {
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.fullGC();  // will do class unloading
    }

    /**
     * Calls triggerUnloading() in a retry loop for 2 seconds or until WhiteBox.isClassAlive
     * determines that no classes named in classNames are alive.
     *
     * This variant of triggerUnloading() accommodates the inherent raciness
     * of class unloading. For example, it's possible for a JIT compilation to hold
     * strong roots to types (e.g. in virtual call or instanceof profiles) that
     * are not released or converted to weak roots until the compilation completes.
     *
     * @param classNames the set of classes that are expected to be unloaded
     * @return the set of classes that have not been unloaded after exiting the retry loop
     */
    public static Set<String> triggerUnloading(List<String> classNames) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        Set<String> aliveClasses = new HashSet<>(classNames);
        int attempt = 0;
        while (!aliveClasses.isEmpty() && attempt < 20) {
            ClassUnloadCommon.triggerUnloading();
            for (String className : classNames) {
                if (aliveClasses.contains(className)) {
                    if (wb.isClassAlive(className)) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                        }
                    } else {
                        aliveClasses.remove(className);
                    }
                }
            }
            attempt++;
        }
        return aliveClasses;
    }

    /**
     * Creates a class loader that loads classes from {@code ${test.class.path}}
     * before delegating to the system class loader.
     */
    public static ClassLoader newClassLoader() {
        String cp = System.getProperty("test.class.path", ".");
        URL[] urls = Stream.of(cp.split(File.pathSeparator))
                .map(Paths::get)
                .map(ClassUnloadCommon::toURL)
                .toArray(URL[]::new);
        return new URLClassLoader("ClassUnloadCommonClassLoader", urls, new ClassUnloadCommon().getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String cn, boolean resolve)
                throws ClassNotFoundException
            {
                synchronized (getClassLoadingLock(cn)) {
                    Class<?> c = findLoadedClass(cn);
                    if (c == null) {
                        try {
                            c = findClass(cn);
                        } catch (ClassNotFoundException e) {
                            c = getParent().loadClass(cn);
                        }

                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }
        };
    }

    static URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    // Get data for pre-compiled class file to load.
    public static byte[] getClassData(String name) {
        try {
            String tempName = name.replaceAll("\\.", "/");
            return ClassUnloadCommon.class.getClassLoader().getResourceAsStream(tempName + ".class").readAllBytes();
        } catch (Exception e) {
              return null;
        }
    }
}
