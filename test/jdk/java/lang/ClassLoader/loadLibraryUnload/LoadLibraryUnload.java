/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8266310
 * @summary deadlock while loading the JNI code
 * @library /test/lib
 * @build LoadLibraryUnload p.Class1
 * @run main/othervm/native -Xcheck:jni LoadLibraryUnload
 */
import jdk.test.lib.Asserts;
import java.lang.*;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import p.Class1;

public class LoadLibraryUnload {

    private static class TestLoader extends URLClassLoader {
        public TestLoader() throws Exception {
            super(new URL[] { Path.of(System.getProperty("test.classes")).toUri().toURL() });
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class clazz = findLoadedClass(name);
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

        public LoadLibraryFromClass(Class<?> fromClass) {
            try {
                this.object = fromClass.newInstance();
                this.method = fromClass.getDeclaredMethod("loadLibrary");
            } catch (Exception error) {
                throw new Error(error);
            }
        }

        @Override
        public void run() {
            try {
                method.invoke(object);
            } catch (Exception error) {
                throw new Error(error);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        URLClassLoader loader = new TestLoader();
        Class<?> class1 = loader.loadClass("p.Class1");
        List<Thread> threads = new ArrayList<>();

        for (int i = 0 ; i < 10 ; i++) {
            threads.add(new Thread(new LoadLibraryFromClass(class1)));
        }
        threads.forEach( t -> {
            t.start();
        });

        // wait for all threads to finish
        for (Thread t : threads) {
            t.join();
        }
        WeakReference<Class> wClass = new WeakReference<>(class1);

        // release strong refs
        class1 = null;
        loader = null;
        threads = null;
        waitForUnload(wClass);
        Asserts.assertTrue(wClass.get() == null, "Class1 hasn't been GC'ed");
    }

    private static void waitForUnload(WeakReference<Class> wClass)
            throws InterruptedException {
        for (int i = 0; i < 100 && wClass.get() != null; ++i) {
            System.gc();
            Thread.sleep(1);
        }
    }
}
