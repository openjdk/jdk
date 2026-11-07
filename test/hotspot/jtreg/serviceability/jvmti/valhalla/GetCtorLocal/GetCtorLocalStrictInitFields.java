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
 * @bug 8395356
 * @summary Test GetLocalInstance/GetLocalObject when the target thread's top frame is
 *          constructor or method of a class with strictly-initialized fields.
 * @enablePreview
 * @requires vm.jvmti
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *     GetCtorLocalStrictInitFields$ConstructorBeforeAndAfterSuper1$TestClass
 *     GetCtorLocalStrictInitFields$ConstructorBeforeAndAfterSuper2$SuperClass
 *     GetCtorLocalStrictInitFields$ConstructorBeforeAndAfterSuper3$TestClass
 *     GetCtorLocalStrictInitFields$ConstructorBeforeAndAfterThis$TestClass
 *     GetCtorLocalStrictInitFields$MethodAfterSuper$TestClass
 *     GetCtorLocalStrictInitFields$MethodAfterInit$TestClass
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED -agentlib:GetCtorLocalStrictInitFields ${test.main.class}
 */

import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

class GetCtorLocalStrictInitFields {

    private static final int JVMTI_ERROR_NONE = 0;
    private static final int JVMTI_ERROR_OPAQUE_FRAME = 32;

    /**
     * Base class to test GetLocalInstance/GetLocalObject.
     */
    abstract class GetCtorLocalTest {
        volatile boolean ready;
        volatile boolean canContinue;
        volatile Throwable exception;

        /**
         * Starts a thread to execute the given action. The action is expected to spin
         * at the given class/method. Once spinning, the thread is suspended and testGetLocalInstance
         * is invoked to attempt to test GetLocalInstance/GetLocalObject.
         * @return the error code from testGetLocalInstance
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
                err = testGetLocalInstance(thread);
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
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor of a class with a strictly-initialized instance field.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper1 extends GetCtorLocalTest {
        class TestClass {
            @StrictInit
            private int x;

            TestClass(GetCtorLocalTest test, int where) {
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
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor or super-constructor of a class with a strictly-initialized
     * instance field. The strict field is declared in the super class.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper2 extends GetCtorLocalTest {
        class SuperClass {
            @StrictInit
            private int x;

            SuperClass(GetCtorLocalTest test, int where) {
                x = 100;

                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();
                this.superClassMethod(test, where);

                // after super
                if (where == 3) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }

            void superClassMethod(GetCtorLocalTest test, int where) {
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        class TestClass extends SuperClass {
            TestClass(GetCtorLocalTest test, int where) {
                // before super
                if (where == 4) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super(test, where);
                this.testClassMethod(test, where);

                // after super
                if (where == 6) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }

            void testClassMethod(GetCtorLocalTest test, int where) {
                if (where == 5) {
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
        void testSuperClassMethod() throws Exception {
            int err = test(() -> new TestClass(this, 2), SuperClass.class, "superClassMethod");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuperSuper() throws Exception {
            int err = test(() -> new TestClass(this, 3), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 4), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testTestClassMethod() throws Exception {
            int err = test(() -> new TestClass(this, 5), TestClass.class, "testClassMethod");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 6), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor or super-constructor of a class with a strictly-initialized
     * instance field. The strict field is declared in the sub class.
     */
    @Nested
    class ConstructorBeforeAndAfterSuper3 extends GetCtorLocalTest {
        class SuperClass {
            SuperClass(GetCtorLocalTest test, int where) {
                // before super
                if (where == 1) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super();
                this.superClassMethod(test, where);

                // after super
                if (where == 3) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }

            void superClassMethod(GetCtorLocalTest test, int where) {
                if (where == 2) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }
        }

        class TestClass extends SuperClass {
            @StrictInit
            private int x;

            TestClass(GetCtorLocalTest test, int where) {
                x = 100;

                // before super
                if (where == 4) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }

                super(test, where);
                this.testClassMethod(test, where);

                // after super
                if (where == 6) {
                    test.ready = true;
                    while (!test.canContinue) {}
                }
            }

            void testClassMethod(GetCtorLocalTest test, int where) {
                if (where == 5) {
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
        void testSuperClassMethod() throws Exception {
            int err = test(() -> new TestClass(this, 2), SuperClass.class, "superClassMethod");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuperSuper() throws Exception {
            // no strict fields in SuperClass or its superclasses
            int err = test(() -> new TestClass(this, 3), SuperClass.class, "<init>");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testBeforeSuper() throws Exception {
            int err = test(() -> new TestClass(this, 4), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }

        @Test
        void testTestClassMethod() throws Exception {
            int err = test(() -> new TestClass(this, 5), TestClass.class, "testClassMethod");
            assertEquals(JVMTI_ERROR_NONE, err);
        }

        @Test
        void testAfterSuper() throws Exception {
            int err = test(() -> new TestClass(this, 6), TestClass.class, "<init>");
            assertEquals(JVMTI_ERROR_OPAQUE_FRAME, err);
        }
    }

    /**
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor of a class with a strictly-initialized instance field
     * before it chains to another constructor to set the field.
     */
    @Nested
    class ConstructorBeforeAndAfterThis extends GetCtorLocalTest {
        class TestClass {
            @StrictInit
            private int x;

            TestClass() {
                x = 100;
                super();
            }

            TestClass(GetCtorLocalTest test, int where) {
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
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor of a value class with an instance field. Value classes
     * rely upon strict field initialization.
     */
    @Nested
    class ValueClassConstructor1 extends GetCtorLocalTest {
        value class TestClass {
            private int x;

            TestClass(GetCtorLocalTest test, int where) {
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
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor of a value class with no instance fields.
     */
    @Nested
    class ValueClassConstructor2 extends GetCtorLocalTest {
        static value class TestClass {
            TestClass(GetCtorLocalTest test, int where) {
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
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is the
     * constructor of a record with an instance field. Record fields are strict
     * fields when compiled with preview features enabled.
     */
    @Nested
    class RecordConstructor extends GetCtorLocalTest {
        record TestClass(int x) {
            TestClass(GetCtorLocalTest test, int where, int x) {
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
     * Test that GetLocalInstance/GetLocalObject can get the THIS object from an instance
     * method invoked by a constructor after super(), even though the object's class
     * has strictly-initialized fields and construction is ongoing.
     */
    @Nested
    class MethodAfterSuper extends GetCtorLocalTest {
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
                // spin here until testGetLocalInstance executes
                test.ready = true;
                while (!test.canContinue) {}

                postInitFinished = true;
            }
        }

        @Test
        void test() throws Exception {
            int err = test(() -> new TestClass(this), TestClass.class, "postInit");
            assertEquals(JVMTI_ERROR_NONE, err);
            assertTrue(postInitFinished, "postInit method should not have returned early");
            assertTrue(initFinished, "<init> did not finish");
        }
    }

    /**
     * Test GetLocalInstance/GetLocalObject when the target thread's top frame is a method of a
     * class with a strictly-initialized instance field. This test ensures that
     * GetLocalInstance/GetLocalObject return normally when the top frame is a method of
     * a class with strictly-initialized fields.
     */
    @Nested
    class MethodAfterInit extends GetCtorLocalTest {
        static volatile boolean runFinished;

        class TestClass {
            @StrictInit
            private int x;

            TestClass() {
                x = 100;
                super();
            }

            void run(MethodAfterInit test) {
                // spin here until testGetLocalInstance executes
                test.ready = true;
                while (!test.canContinue) {}

                runFinished = true;
            }
        }

        @Test
        void test() throws Exception {
            var obj = new TestClass();
            int err = test(() -> obj.run(this), TestClass.class, "run");
            assertEquals(JVMTI_ERROR_NONE, err);
            assertTrue(runFinished, "run method should not have returned early");
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
    static native int testGetLocalInstance(Thread thread);
}
