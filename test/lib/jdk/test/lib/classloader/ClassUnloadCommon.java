/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
import nsk.share.CustomClassLoader;
import nsk.share.Failure;

import java.io.File;
import java.io.Serial;
import java.lang.ref.PhantomReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Stream;

public class ClassUnloadCommon {

    private static final int MAX_UNLOAD_ATTEMPS = 20;

    /**
     * Phantom reference to the class loader.
     */
    private PhantomReference<Object> customClassLoaderPhantomRef = null;

    /**
     * Current class loader used for loading classes.
     */
    private CustomClassLoader customClassLoader = null;

    /**
     * List of classes loaded with current class loader.
     */
    private Vector<Class<?>> classObjects = new Vector<Class<?>>();
    public static class TestFailure extends RuntimeException {
        @Serial
        private static final long serialVersionUID = -8108935949624559549L;

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
        while (!aliveClasses.isEmpty() && attempt < MAX_UNLOAD_ATTEMPS) {
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

    /**
     * Class object of the first class been loaded with current class loader.
     * To get the rest loaded classes use <code>getLoadedClass(int)</code>.
     * The call <code>getLoadedClass()</code> is effectively equivalent to the call
     * <code>getLoadedClass(0)</code>
     *
     * @return class object of the first loaded class.
     *
     * @see #getLoadedClass(int)
     */
    public Class<?> getLoadedClass() {
        return classObjects.get(0);
    }

    /**
     * Returns class objects at the specified index in the list of classes loaded
     * with current class loader.
     *
     * @return class objects at the specified index.
     */
    public Class<?> getLoadedClass(int index) {
        return classObjects.get(index);
    }

    /**
     * Creates new instance of <code>CustomClassLoader</code> class as the current
     * class loader and clears the list of loaded classes.
     *
     * @return created instance of <code>CustomClassLoader</code> class.
     *
     * @see #getClassLoader()
     * @see #setClassLoader(CustomClassLoader)
     */
    public CustomClassLoader createClassLoader() {
        customClassLoader = new CustomClassLoader();
        classObjects.removeAllElements();

        customClassLoaderPhantomRef = new PhantomReference<>(customClassLoader, null);

        return customClassLoader;
    }

    /**
     * Sets new current class loader and clears the list of loaded classes.
     *
     * @see #getClassLoader()
     * @see #createClassLoader()
     */
    public void setClassLoader(CustomClassLoader customClassLoader) {
        this.customClassLoader = customClassLoader;
        classObjects.removeAllElements();

        customClassLoaderPhantomRef = new PhantomReference<>(customClassLoader, null);
    }

    /**
     * Returns current class loader or <i>null</i> if not yet created or set.
     *
     * @return class loader object or null.
     *
     * @see #createClassLoader()
     * @see #setClassLoader(CustomClassLoader)
     */
    public CustomClassLoader getClassLoader() {
        return customClassLoader;
    }

    /**
     * Loads class for specified class name using current class loader.
     *
     * <p>Current class loader should be set and capable to load class using only
     * given class name. No other information such a location of .class files
     * is passed to class loader.
     *
     * @param className name of class to load
     *
     * @throws ClassNotFoundException if no bytecode found for specified class name
     * @throws Failure if current class loader is not specified;
     *                 or if class was actually loaded with different class loader
     *
     * @see #loadClass(String, String)
     */
    public void loadClass(String className) throws ClassNotFoundException {

        if (customClassLoader == null) {
            throw new Failure("No current class loader defined");
        }

        Class<?> cls = Class.forName(className, true, customClassLoader);

        // ensure that class was loaded by current class loader
        if (cls.getClassLoader() != customClassLoader) {
            throw new Failure("Class was loaded by unexpected class loader: " + cls.getClassLoader());
        }

        classObjects.add(cls);
    }

    /**
     * Loads class from .class file located into specified directory using
     * current class loader.
     *
     * <p>If there is no current class loader, then default class loader
     * is created using <code>createClassLoader()</code>. Parameter <i>classDir</i>
     * is passed to class loader using <code>CustomClassLoader.setClassPath()</code>
     * method before loading class.
     *
     * @param className name of class to load
     * @param classDir path to .class file location
     *
     * @throws ClassNotFoundException if no .class file found
     *          for specified class name
     * @throws Failure if class was actually loaded with different class loader
     *
     * @see #loadClass(String)
     * @see CustomClassLoader#setClassPath(String)
     */
    public void loadClass(String className, String classDir) throws ClassNotFoundException {

        if (customClassLoader == null) {
            createClassLoader();
        }

        customClassLoader.setClassPath(classDir);
        loadClass(className);
    }

    /**
     * Forces GC to unload previously loaded classes by cleaning all references
     * to class loader with its loaded classes.
     *
     * @return  <i>true</i> if classes unloading has been detected
             or <i>false</i> otherwise
     *
     * @throws  Failure if exception other than OutOfMemoryError
     *           is thrown while triggering full GC
     *
     * @see WhiteBox.getWhiteBox().fullGC()
     */

    public boolean unloadClass() {

        // free references to class and class loader to be able for collecting by GC
        classObjects.removeAllElements();
        customClassLoader = null;

        // force class unloading by triggering full GC
        WhiteBox.getWhiteBox().fullGC();
        int attempt = 0;
        while (attempt < MAX_UNLOAD_ATTEMPS && !isClassLoaderReclaimed()) {
            System.out.println("ClassUnloader: waiting for class loader reclaiming... " + attempt);
            WhiteBox.getWhiteBox().fullGC();
            try {
                // small delay to give more changes to process objects
                // inside VM like jvmti deferred queue
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        // force GC to unload marked class loader and its classes
        if (isClassLoaderReclaimed()) {
            System.out.println("ClassUnloader: class loader has been reclaimed.");
            return true;
        }

        // class loader has not been reclaimed
        System.out.println("ClassUnloader: class loader is still reachable.");
        return false;
    }

    /**
     * Has class loader been reclaimed or not.
     */
    private boolean isClassLoaderReclaimed() {
        return customClassLoaderPhantomRef != null
            && customClassLoaderPhantomRef.refersTo(null);
    }
}
