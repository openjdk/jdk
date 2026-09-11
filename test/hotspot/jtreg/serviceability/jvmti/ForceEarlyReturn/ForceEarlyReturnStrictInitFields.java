/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8387648
 * @summary Test ForceEarlyReturnVoid when the target thread's top frame is the class
 *     initializer, constructor or method of a class with strictly-initialized fields.
 * @enablePreview
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *     ForceEarlyReturnStrictInitFields$ConstructorBeforeAndAfterSuper1$TestClass
 *     ForceEarlyReturnStrictInitFields$ConstructorBeforeAndAfterSuper2$SuperClass
 *     ForceEarlyReturnStrictInitFields$ConstructorBeforeAndAfterSuper3$TestClass
 *     ForceEarlyReturnStrictInitFields$ConstructorBeforeAndAfterThis$TestClass
 *     ForceEarlyReturnStrictInitFields$MethodAfterSuper$TestClass
 *     ForceEarlyReturnStrictInitFields$MethodAfterInit$TestClass
 *     ForceEarlyReturnStrictInitFields$ClassInitializerBeforeSet$TestClass
 *     ForceEarlyReturnStrictInitFields$ClassInitializerAfterSet$TestClass
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED -agentlib:ForceEarlyReturnStrictInitFields ${test.main.class}
 */

import java.lang.invoke.MethodHandles;
import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

class ForceEarlyReturnStrictInitFields {

    private static final int JVMTI_ERROR_NONE = 0;
    private static final int JVMTI_ERROR_OPAQUE_FRAME = 32;

    /**
     * Base class to test ForceEarlyReturnVoid.
     */
    abstract class ForceEarlyReturnTest {
        volatile boolean ready;
        volatile boolean canContinue;
        volatile Throwable exception;

        /**
         * Starts a thread to execute the given action. The action is expected to spin
         * at the given class/method. Once spinning, the thread is suspended and
         * ForceEarlyReturnVoid is invoked to attempt to force it to return early.
         * @return the return from ForceEarlyReturnVoid
         */
        int test(Executable action, Class<?> targetClass, String targetMethod) throws Exception {
            assertFalse(ready, "Test already executed");
            Thread thread = Thread.ofPlatform().start(() -> {
                try {
                    action.execute();
                } catch (Throwable ex) {
                    exception = ex;
                }
            });
            int err;
            boolean suspended = false;
            try {
                // wait for target thread to spin
                while (!ready) {
                    Thread.sleep(10);
                }
                assertEquals(JVMTI_ERROR_NONE, suspendThread(thread));
                suspended = true;
                assertTopFrame(thread, targetClass, targetMethod);
                err = forceEarlyReturnVoid(thread);
            } finally {
                canContinue = true;
                if (suspended) resumeThread(thread);
                thread.join();
            }
            assertNull(exception, "target thread threw exception");
            return err;
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor of a class with a strictly-initialized instance field.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper1 extends ForceEarlyReturnTest {
        class TestClass {
            @StrictInit
            private int x;

            TestClass(ForceEarlyReturnTest test, int where) {
                x = 100;

                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();

                // after super
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 1), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 2), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor or super-constructor of a class with a strictly-initialized
     * instance field. The strict field is declared in the super class.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper2 extends ForceEarlyReturnTest {
        class SuperClass {
            @StrictInit
            private int x;

            SuperClass(ForceEarlyReturnTest test, int where) {
                x = 100;

                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();

                // after super
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        class TestClass extends SuperClass {
            TestClass(ForceEarlyReturnTest test, int where) {
                // before super
                if (where == 3) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super(test, where);

                // after super
                if (where == 4) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @Test
        void testBeforeSuperSuper() throws Exception {
            int err = test(() -> new TestClass(this, 1), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterSuperSuper() throws Exception {
            int err = test(() -> new TestClass(this, 2), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 3), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 4), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor or super-constructor of a class with a strictly-initialized
     * instance field. The strict field is declared in the sub class.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper3 extends ForceEarlyReturnTest {
        class SuperClass {
            SuperClass(ForceEarlyReturnTest test, int where) {
                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();

                // after super
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        class TestClass extends SuperClass {
            @StrictInit
            private int x;

            TestClass(ForceEarlyReturnTest test, int where) {
                x = 100;

                // before super
                if (where == 3) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super(test, where);

                // after super
                if (where == 4) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @Test
        void testBeforeSuperSuper() throws Exception {
            // no strict fields in SuperClass or its superclasses
            int err = test(() -> new TestClass(this, 1), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuperSuper() throws Exception {
            // no strict fields in SuperClass or its superclasses
            int err = test(() -> new TestClass(this, 2), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 3), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 4), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor of a class with a strictly-initialized instance field
     * before it chains to another constructor to set the field.
     */
    @Nested
    class ConstructorBeforeAndAfterThis extends ForceEarlyReturnTest {
        class TestClass {
            @StrictInit
            private int x;

            TestClass() {
                x = 100;
                super();
            }

            TestClass(ForceEarlyReturnTest test, int where) {
                // before this
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                this();

                // after this
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @Test
        void testBeforeThis() throws Exception {
            int err = test(() -> new TestClass(this, 1), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterThis() throws Exception {
            int err = test(() -> new TestClass(this, 2), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor of a value class with an instance field. Value classes
     * rely upon strict field initialization.
     */
    @Nested
    class ValueClassConstructor1 extends ForceEarlyReturnTest {
        value class TestClass {
            private int x;

            TestClass(ForceEarlyReturnTest test, int where) {
                x = 100;

                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();

                // after super
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @BeforeAll()
        static void verifyPreconditions() throws Exception {
            assertTrue(TestClass.class.getDeclaredField("x").isStrictInit(),
                    "expected to be strict field");
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 1), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 2), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor of a value class with no instance fields.
     */
    @Nested
    class ValueClassConstructor2 extends ForceEarlyReturnTest {
        value class TestClass {
            TestClass(ForceEarlyReturnTest test, int where) {
                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();

                // after super
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @Test
        void testBeforeSuper() throws Exception {
            // no strict fields
            int err = test(() -> new TestClass(this, 1), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            // no strict fields
            int err = test(() -> new TestClass(this, 2), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_NONE, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the
     * constructor of a record with an instance field. Record fields are strict
     * fields when compiled with preview features enabled.
     */
    @Nested
    class RecordConstructor extends ForceEarlyReturnTest {
        record TestClass(int x) {
            TestClass(ForceEarlyReturnTest test, int where, int x) {
                // before this
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                this(x);

                // after this
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        @BeforeAll()
        static void verifyPreconditions() throws Exception {
            assertTrue(TestClass.class.getDeclaredField("x").isStrictInit(),
                    "expected to be strict field");
        }

        @Test
        void testBeforeThis() throws Exception {
            int err = test(() -> new TestClass(this, 1, 100), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testAfterThis() throws Exception {
            int err = test(() -> new TestClass(this, 2, 100), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is a method
     * invoked by the constructor of a class with a strictly-initialized instance
     * field after super() is invoked. This test exercises the implementation for the
     * case that the caller of the method that returns early is a constructor of a
     * class with strictly-initialized fields.
     */
    @Nested
    class MethodAfterSuper extends ForceEarlyReturnTest {
        static volatile boolean postInitFinished;
        static volatile boolean initFinished;

        class TestClass {
            @StrictInit
            private int x;

            TestClass(MethodAfterSuper test) {
                x = 100;
                super();
                postInit(test);
                initFinished = true;
            }

            void postInit(MethodAfterSuper test) {
                // spin here until ForceEarlyReturnVoid executes
                test.ready = true;
                while (!test.canContinue) {}

                // should not get here
                postInitFinished = true;
            }
        }

        @Test
        void test() throws Exception {
            int err = test(() -> new TestClass(this), TestClass.class, "postInit");
            assertEquals(JVMTI_ERROR_NONE, err);
            assertFalse(postInitFinished, "postInit method should have returned early");
            assertTrue(initFinished, "<init> did not finish");
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is a method of a
     * class with a strictly-initialized instance field. This test ensures that force
     * early is allowed when the top frame is a method of a class with
     * strictly-initialized fields.
     */
    @Nested
    class MethodAfterInit extends ForceEarlyReturnTest {
        static volatile boolean runFinished;

        class TestClass {
            @StrictInit
            private int x;

            TestClass() {
                x = 100;
                super();
            }

            void run(MethodAfterInit test) {
                // spin here until ForceEarlyReturnVoid executes
                test.ready = true;
                while (!test.canContinue) {}

                // should not get here
                runFinished = true;
            }
        }

        @Test
        void test() throws Exception {
            var obj = new TestClass();
            int err = test(() -> obj.run(this), TestClass.class, "run");
            assertEquals(JVMTI_ERROR_NONE, err);
            assertFalse(runFinished, "run method should have returned early");
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the class
     * initializer of a class with a strictly-initialized static field before
     * the field is set.
     */
    @Nested
    class ClassInitializerBeforeSet {
        static volatile boolean ready;
        static volatile boolean canContinue;
        static volatile boolean finished;
        static volatile Throwable exception;

        class TestClass {
            @StrictInit
            private static int x;

            static {
                // before strict field is initialized
                ready = true;
                while (!canContinue) {}

                x = 100;
                finished = true;
            }
        }

        @Test
        void test() throws Exception {
            Thread thread = Thread.ofPlatform().start(() -> {
                try {
                    MethodHandles.lookup().ensureInitialized(TestClass.class);
                } catch (Throwable ex) {
                    exception = ex;
                }
            });
            boolean suspended = false;
            try {
                // wait for target thread to spin in class initializer
                while (!ready) {
                    Thread.sleep(10);
                }
                assertEquals(JVMTI_ERROR_NONE, suspendThread(thread));
                suspended = true;
                assertTopFrame(thread, TestClass.class, "<clinit>");
                assertEquals(JVMTI_ERROR_OPAQUE_FRAME, forceEarlyReturnVoid(thread));
            } finally {
                canContinue = true;
                if (suspended) resumeThread(thread);
                thread.join();
            }
            assertTrue(finished, "<clinit> should have completed");
            assertNull(exception, "no exception expected");
            assertEquals(100, TestClass.x);
        }
    }

    /**
     * Test ForceEarlyReturnVoid when the target thread's top frame is the class
     * initializer of a class with a strictly-initialized static field after
     * the field is set.
     */
    @Nested
    class ClassInitializerAfterSet {
        static volatile boolean ready;
        static volatile boolean canContinue;
        static volatile boolean finished;
        static volatile Throwable exception;

        class TestClass {
            @StrictInit
            private static int x;

            static {
                x = 100;

                // after strict field is initialized
                ready = true;
                while (!canContinue) {}

                finished = true;
            }
        }

        @Test
        void test() throws Exception {
            Thread thread = Thread.ofPlatform().start(() -> {
                try {
                    MethodHandles.lookup().ensureInitialized(TestClass.class);
                } catch (Throwable ex) {
                    exception = ex;
                }
            });
            boolean suspended = false;
            try {
                // wait for target thread to spin in class initializer
                while (!ready) {
                    Thread.sleep(10);
                }
                assertEquals(JVMTI_ERROR_NONE, suspendThread(thread));
                suspended = true;
                assertTopFrame(thread, TestClass.class, "<clinit>");
                assertEquals(JVMTI_ERROR_OPAQUE_FRAME, forceEarlyReturnVoid(thread));
            } finally {
                canContinue = true;
                if (suspended) resumeThread(thread);
                thread.join();
            }
            assertTrue(finished, "<clinit> should have completed");
            assertNull(exception, "no exception expected");
            assertEquals(100, TestClass.x);
        }
    }

    /**
     * Asserts that the given thread's top frame is the expected class/method.
     */
    static void assertTopFrame(Thread thread, Class<?> clazz, String methodName) {
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length > 0);
        assertEquals(clazz.getName(), stack[0].getClassName());
        assertEquals(methodName, stack[0].getMethodName());
    }

    static native int suspendThread(Thread thread);
    static native int resumeThread(Thread thread);
    static native int forceEarlyReturnVoid(Thread thread);
}
