/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test Make sure class unloading occur even if ClassLoaderStats VM operations are executed
 * @bug 8297427
 * @requires vm.opt.final.ClassUnloading
 * @modules java.base/jdk.internal.misc
 * @library /test/lib classes
 * @build jdk.test.whitebox.WhiteBox test.Empty test.LoadInParent test.LoadInChild
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xlog:gc*,class+unload=debug UnloadTestDuringClassLoaderStatsVMOperation
 */
import java.lang.ref.Reference;
import java.net.URLClassLoader;

import jdk.test.lib.classloader.ClassUnloadCommon;
import jdk.test.whitebox.WhiteBox;

public class UnloadTestDuringClassLoaderStatsVMOperation {
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    private static String className = "test.Empty";
    private static String parentClassName = "test.LoadInParent";
    private static String childClassName = "test.LoadInChild";

    public static void main(String args[]) throws Exception {
        // Create a thread forcing ClassLoaderStats VM operations.
        var clsThread = new Thread(() -> {
            while (true) {
                wb.forceClassLoaderStatsSafepoint();
            }
        });
        clsThread.setDaemon(true);
        clsThread.start();

        // Make sure classes can be unloaded even though the class loader
        // stats VM operation is running.
        testClassIsUnloaded();
        testClassLoadedInParentIsUnloaded();
    }

    public static void testClassIsUnloaded() throws Exception {
        ClassUnloadCommon.failIf(wb.isClassAlive(className), className + " is not expected to be alive yet");

        // Load a test class and verify that it gets unloaded once we do a major collection.
        var classLoader = ClassUnloadCommon.newClassLoader();
        var loaded = classLoader.loadClass(className);
        var object = loaded.getDeclaredConstructor().newInstance();

        ClassUnloadCommon.failIf(!wb.isClassAlive(className), className + " should be loaded and live");

        // Using reachabilityFence() to ensure the object is live. If the test
        // is run with -Xcomp and ergonomically triggered GCs occur the class
        // could otherwise be unloaded before verified to be alive above.
        Reference.reachabilityFence(object);

        System.out.println("testClassIsUnloaded loaded klass: " + className);

        // Make class unloadable.
        classLoader = null;
        loaded = null;
        object = null;

        // Full/Major collection should always unload classes.
        wb.fullGC();
        ClassUnloadCommon.failIf(wb.isClassAlive(className), className + " should have been unloaded");
    }

    public static void testClassLoadedInParentIsUnloaded() throws Exception {
        ClassUnloadCommon.failIf(wb.isClassAlive(parentClassName), parentClassName + " is not expected to be alive yet");
        ClassUnloadCommon.failIf(wb.isClassAlive(childClassName), childClassName + " is not expected to be alive yet");

        // Create two class loaders and load a test class in the parent and
        // verify that it gets unloaded once we do a major collection.
        var parentClassLoader = ClassUnloadCommon.newClassLoader();
        var childClassLoader =  new ChildURLClassLoader((URLClassLoader) parentClassLoader);
        var loadedParent = parentClassLoader.loadClass(parentClassName);
        var loadedChild = childClassLoader.loadClass(childClassName);
        var parent = loadedParent.getDeclaredConstructor().newInstance();
        var child = loadedChild.getDeclaredConstructor().newInstance();

        ClassUnloadCommon.failIf(!wb.isClassAlive(parentClassName), parentClassName + " should be loaded and live");
        ClassUnloadCommon.failIf(!wb.isClassAlive(childClassName), childClassName + " should be loaded and live");

        // Using reachabilityFence() to ensure the objects are live. If the test
        // is run with -Xcomp and ergonomically triggered GCs occur they could
        // otherwise be unloaded before verified to be alive above.
        Reference.reachabilityFence(parent);
        Reference.reachabilityFence(child);

        System.out.println("testClassLoadedInParentIsUnloaded loaded klass: " + loadedParent);
        System.out.println("testClassLoadedInParentIsUnloaded loaded klass: " + loadedChild);

        // Clear to allow unloading.
        parentClassLoader = null;
        childClassLoader = null;
        loadedParent = null;
        loadedChild = null;
        parent = null;
        child = null;

        // Full/Major collection should always unload classes.
        wb.fullGC();
        ClassUnloadCommon.failIf(wb.isClassAlive(parentClassName), parentClassName + " should have been unloaded");
        ClassUnloadCommon.failIf(wb.isClassAlive(childClassName), childClassName + " should have been unloaded");
    }

    static class ChildURLClassLoader extends URLClassLoader {
        public ChildURLClassLoader(URLClassLoader parent) {
            super("ChildURLClassLoader", parent.getURLs(), parent);
        }

        @Override
        public Class<?> loadClass(String cn, boolean resolve) throws ClassNotFoundException {
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
    }
}
