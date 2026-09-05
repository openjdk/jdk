/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Even if LoadableDescriptors fail, we observe correct operation.
            This uses a custom classloader to ensure that classloading/linking will
            initially fail due to LoadableDescriptors.
   @compile OuterValue.java
   @compile InnerValue.java
 * @enablePreview
 * @run junit runtime.valhalla.inlinetypes.classloading.PreLoadFailuresDoNotImpactApplicationTest
 */

package runtime.valhalla.inlinetypes.classloading;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class PreLoadFailuresDoNotImpactApplicationTest {

    @Test
    void test() throws ReflectiveOperationException, InterruptedException {
        // Create an instance of the Outer class with a custom classloader.
        // This should trigger the first preload.
        Class<?> clazz = Class.forName("OuterValue", false, new CL());
        Object outer = clazz.getDeclaredConstructor().newInstance();
        // Each thread will try to load the field inner. We have to do getDeclaredField in each thread
        // as this attempts to load the class Inner which is what we want to fail on the first time.
        Thread t1 = new Thread(() -> {
            try {
                // Here the classloader will instigate a failure twice:
                // 1) when linking Inner due to LoadableDescriptor failing, this failure is quiet; and
                // 2) a "normal" ClassNotFound error that should get picked up by the application.
                var theField = clazz.getDeclaredField("inner");
                Object theInner = theField.get(outer);
                throw new RuntimeException("should have failed classloading Inner fist time, VM bug");
            } catch (NoClassDefFoundError workingAsIntended) {
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException("test bug: field accessing", e);
            }
        });
        // Execution need not be concurrent or parallel.
        // Let this thread fail at classloading Inner.
        t1.start();
        t1.join();
        Thread t2 = new Thread(() -> {
            try {
                // Here, the classloader will not fail, this should be business as usual.
                var theField = clazz.getDeclaredField("inner");
                Object theInner = theField.get(outer);
            } catch (NoClassDefFoundError e) {
                throw new IllegalStateException("should not have failed classloading Inner second time, VM bug", e);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException("test bug: field accessing", e);
            }
        });
        // This execution should succeed.
        t2.start();
        t2.join();
    }

    // This classloader uses loadClass instead of overriding findClass because it makes the test logic
    // a bit easier to write and understand. For the purpose of this test, overriding loadClass should
    // be fine.
    static class CL extends ClassLoader {
        private int attempts = 0;

        public synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            switch (name) {
            case "OuterValue": {
                return customLoadSimple(name);
            }
            case "InnerValue": {
                // First access failure: when Outer is preloading Inner via LoadableDescriptor.
                // Second access failure: when we try to load Inner the first time (LoadableDescriptor).
                // Third access failure: when class linking occurs (LoadableDescriptor).
                // Fourth access and onwards should succeed.
                if (attempts++ < 3) {
                    throw new ClassNotFoundException("purposeful exception: we can't find this class");
                }
                return customLoadSimple(name);
            }
            default:
                // Delegate loading to the parent classloader.
                return super.loadClass(name, resolve);
            }
        }

        // This will get the class data as a byte[]. DOES NOT support packages.
        private Class<?> customLoadSimple(String name) {
            byte[] bytes;
            try {
                bytes = PreLoadFailuresDoNotImpactApplicationTest.class
                    .getClassLoader()
                    .getResourceAsStream(name + ".class")
                    .readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("test bug: IO exception trying to load custom class " + name, e);
            }
            return defineClass(name, bytes, 0, bytes.length, null);
        }
    }

}
