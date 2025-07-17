/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test virtual threads using core reflection
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit Reflection
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;

import jdk.test.lib.thread.VThreadScheduler;
import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class Reflection {

    /**
     * Test invoking static method.
     */
    @Test
    void testInvokeStatic1() throws Exception {
        VThreadRunner.run(() -> {
            int result = (int) divideMethod().invoke(null, 20, 2);
            assertTrue(result == 10);
        });
    }

    /**
     * Test that InvocationTargetException is thrown when a static method throws
     * exception.
     */
    @Test
    void testInvokeStatic2() throws Exception {
        VThreadRunner.run(() -> {
            try {
                divideMethod().invoke(null, 20, 0);
                fail();
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof ArithmeticException);
            }
        });
    }

    /**
     * Test that IllegalArgumentException is thrown when trying to invoke a static
     * method with bad parameters.
     */
    @Test
    void testInvokeStatic3() throws Exception {
        VThreadRunner.run(() -> {
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(null));
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(null, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(null, 1, 2, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(new Object()));
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(new Object(), 1));
            assertThrows(IllegalArgumentException.class,
                    () -> divideMethod().invoke(new Object(), 1, 2, 3));
        });
    }

    /**
     * Test that ExceptionInInitializerError is thrown when invoking a static
     * method triggers its class to be initialized and it fails with exception.
     */
    @Test
    void testInvokeStatic4() throws Exception {
        VThreadRunner.run(() -> {
            Method foo = BadClass1.class.getDeclaredMethod("foo");
            try {
                foo.invoke(null);
                fail();
            } catch (ExceptionInInitializerError e) {
                assertTrue(e.getCause() instanceof ArithmeticException);
            }
        });
    }

    static class BadClass1 {
        static {
            if (1==1) throw new ArithmeticException();
        }
        static void foo() { }
    }

    /**
     * Test that an error is thrown when invoking a static method triggers its
     * class to be initialized and it fails with an error.
     */
    @Test
    void testInvokeStatic5() throws Exception {
        VThreadRunner.run(() -> {
            Method foo = BadClass2.class.getDeclaredMethod("foo");
            assertThrows(AbstractMethodError.class, () -> foo.invoke(null));
        });
    }

    static class BadClass2 {
        static {
            if (1==1) throw new AbstractMethodError();
        }
        static void foo() { }
    }

    /**
     * Test that invoking a static method does not pin the carrier thread.
     */
    @Test
    void testInvokeStatic6() throws Exception {
        assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        Method parkMethod = Parker.class.getDeclaredMethod("park");
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var ready = new CountDownLatch(1);
            Thread vthread = factory.newThread(() -> {
                ready.countDown();
                try {
                    parkMethod.invoke(null);   // blocks
                } catch (Exception e) { }
            });
            vthread.start();

            try {
                // wait for thread to run
                ready.await();

                // unpark with another virtual thread, runs on same carrier thread
                Thread unparker = factory.newThread(() -> LockSupport.unpark(vthread));
                unparker.start();
                unparker.join();
            } finally {
                LockSupport.unpark(vthread);  // in case test fails
            }
        }
    }

    /**
     * Test invoking instance method.
     */
    @Test
    void testInvokeInstance1() throws Exception {
        VThreadRunner.run(() -> {
            var adder = new Adder();
            Adder.addMethod().invoke(adder, 5);
            assertTrue(adder.sum == 5);
        });
    }

    /**
     * Test that InvocationTargetException is thrown when an instance method throws
     * exception.
     */
    @Test
    void testInvokeInstance2() throws Exception {
        VThreadRunner.run(() -> {
            var adder = new Adder();
            try {
                Adder.addMethod().invoke(adder, -5);
                fail();
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof IllegalArgumentException);
            }
        });
    }

    /**
     * Test that NullPointerException and IllegalArgumentException are thrown when
     * trying to invoke an instance method with null or bad parameters.
     */
    @Test
    void testInvokeInstance3() throws Exception {
        VThreadRunner.run(() -> {
            var adder = new Adder();
            Method addMethod = Adder.addMethod();
            assertThrows(NullPointerException.class,
                    () -> addMethod.invoke(null));
            assertThrows(IllegalArgumentException.class,
                    () -> addMethod.invoke(adder));
            assertThrows(IllegalArgumentException.class,
                    () -> addMethod.invoke(adder, 1, 2));
            assertThrows(IllegalArgumentException.class,
                    () -> addMethod.invoke(adder, 1, "hi"));
            assertThrows(IllegalArgumentException.class,
                    () -> addMethod.invoke(adder, "hi"));
        });
    }

    /**
     * Test invoking newInstance to create an object.
     */
    @Test
    void testNewInstance1() throws Exception {
        VThreadRunner.run(() -> {
            Constructor<?> ctor = Adder.class.getDeclaredConstructor(long.class);
            Adder adder = (Adder) ctor.newInstance(10);
            assertTrue(adder.sum == 10);
        });
    }

    /**
     * Test that InvocationTargetException is thrown when a constructor throws
     * exception.
     */
    @Test
    void testNewInstance2() throws Exception {
        VThreadRunner.run(() -> {
            Constructor<?> ctor = Adder.class.getDeclaredConstructor(long.class);
            try {
                ctor.newInstance(-10);
                fail();
            } catch (InvocationTargetException e) {
                assertTrue(e.getCause() instanceof IllegalArgumentException);
            }
        });
    }

    /**
     * Test that IllegalArgumentException is thrown when newInstacne is called
     * with bad parameters.
     */
    @Test
    void testNewInstance3() throws Exception {
        VThreadRunner.run(() -> {
            var adder = new Adder();
            Constructor<?> ctor = Adder.class.getDeclaredConstructor(long.class);
            assertThrows(IllegalArgumentException.class,
                    () -> ctor.newInstance((Object[])null));
            assertThrows(IllegalArgumentException.class,
                    () -> ctor.newInstance(adder));
            assertThrows(IllegalArgumentException.class,
                    () -> ctor.newInstance(adder, null));
            assertThrows(IllegalArgumentException.class,
                    () -> ctor.newInstance(adder, "foo"));
            assertThrows(IllegalArgumentException.class,
                    () -> ctor.newInstance(adder, 1, 2));
        });
    }

    /**
     * Test that ExceptionInInitializerError is thrown when invoking newInstance
     * triggers the class to be initialized and it fails with exception.
     */
    @Test
    void testNewInstance4() throws Exception {
        VThreadRunner.run(() -> {
            Constructor<?> ctor = BadClass3.class.getDeclaredConstructor();
            try {
                ctor.newInstance((Object[])null);
                fail();
            } catch (ExceptionInInitializerError e) {
                assertTrue(e.getCause() instanceof ArithmeticException);
            }
        });
    }

    static class BadClass3 {
        static {
            if (1==1) throw new ArithmeticException();
        }
        static void foo() { }
    }

    /**
     * Test that error is thrown when invoking newInstance triggers the class
     * to be initialized and it fails with an error.
     */
    @Test
    void testNewInstance5() throws Exception {
        VThreadRunner.run(() -> {
            Constructor<?> ctor = BadClass4.class.getDeclaredConstructor();
            assertThrows(AbstractMethodError.class, () -> ctor.newInstance((Object[])null));
        });
    }

    static class BadClass4 {
        static {
            if (1==1) throw new AbstractMethodError();
        }
        static void foo() { }
    }

    /**
     * Test that newInstance does not pin the carrier thread
     */
    @Test
    void testNewInstance6() throws Exception {
        assumeTrue(VThreadScheduler.supportsCustomScheduler(), "No support for custom schedulers");
        Constructor<?> ctor = Parker.class.getDeclaredConstructor();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            var ready = new CountDownLatch(1);
            Thread vthread = factory.newThread(() -> {
                ready.countDown();
                try {
                    ctor.newInstance();
                } catch (Exception e) { }
            });
            vthread.start();

            try {
                // wait for thread to run
                ready.await();

                // unpark with another virtual thread, runs on same carrier thread
                Thread unparker = factory.newThread(() -> LockSupport.unpark(vthread));
                unparker.start();
                unparker.join();
            } finally {
                LockSupport.unpark(vthread);  // in case test fails
            }
        }
    }


    // -- support classes and methods --

    static int divide(int x, int y) {
        return x / y;
    }

    static Method divideMethod() throws NoSuchMethodException {
        return Reflection.class.getDeclaredMethod("divide", int.class, int.class);
    }

    static class Adder {
        long sum;
        Adder() { }
        Adder(long x) {
            if (x < 0)
                throw new IllegalArgumentException();
            sum = x;
        }
        Adder add(long x) {
            if (x < 0)
                throw new IllegalArgumentException();
            sum += x;
            return this;
        }
        static Method addMethod() throws NoSuchMethodException {
            return Adder.class.getDeclaredMethod("add", long.class);
        }
        long sum() {
            return sum;
        }
    }

    static class Parker {
        Parker() {
            LockSupport.park();
        }
        static void park() {
            LockSupport.park();
        }
    }
}
