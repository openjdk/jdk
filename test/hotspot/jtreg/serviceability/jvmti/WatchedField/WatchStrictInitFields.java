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
 * @bug 8390009
 * @summary Test FieldAccess/FieldModification events sent for fields in objects and
 *    classes with strictly-initialized fields
 * @enablePreview
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *     WatchStrictInitFields$InstanceField2$TestClass
 *     WatchStrictInitFields$InstanceField3$TestClass
 *     WatchStrictInitFields$InstanceField4$SuperClass
 *     WatchStrictInitFields$InstanceField5$TestClass
 *     WatchStrictInitFields$InstanceField6$TestClass
 *     WatchStrictInitFields$StaticField2$TestClass
 *     WatchStrictInitFields$StaticField3$TestClass
 *     WatchStrictInitFields$StaticField4$TestClass
 * @run junit/othervm/native --enable-native-access=ALL-UNNAMED -agentlib:WatchStrictInitFields ${test.main.class}
 */

import java.lang.invoke.MethodHandles;
import jdk.test.lib.helpers.StrictInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class WatchStrictInitFields {

    /**
     * Test events posted from the constructor of a class with no strict fields.
     */
    @Nested
    class InstanceField1 {
        class TestClass {
            int x;

            TestClass(int v1) {
                x = v1;                // modification event
                super();
                assertEquals(v1, x);   // access event
            }

            TestClass(int v1, int v2) {
                this(v1);
                x = v2;                // modification event
                assertEquals(v2, x);   // access event
            }

            TestClass(int v1, int v2, int v3) {
                this(v1, v2);
                x(v3);                   // this-escape
                assertEquals(v3, x());   // this-escape
            }

            void x(int newX) {
                x = newX;    // modification event
            }

            int x() {
                return x;   // access event
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());

                // modify after construction
                obj.x = 200;
                assertEquals(2, modCount());
                assertEquals(1, accessCount());

                // access after construction
                assertEquals(200, obj.x);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());

                // mutator method
                obj.x(300);
                assertEquals(3, modCount());
                assertEquals(2, accessCount());

                // accessor method
                assertEquals(300, obj.x());
                assertEquals(3, modCount());
                assertEquals(3, accessCount());
            }
        }

        @Test
        void testConstructorChaining() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 101);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }

        @Test
        void testThisEscape() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 101, 102);
                assertEquals(3, modCount());
                assertEquals(3, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of a class with a strict field.
     */
    @Nested
    class InstanceField2 {
        class TestClass {
            @StrictInit int x;

            TestClass(int v1) {
                x = v1;                // modification event not sent
                super();
                assertEquals(v1, x);   // access event not sent
            }

            TestClass(int v1, int v2) {
                this(v1);
                x = v2;                // modification event not sent
                assertEquals(v2, x);   // access event not sent
            }

            TestClass(int v1, int v2, int v3) {
                this(v1, v2);
                x(v3);                   // this-escape
                assertEquals(v3, x());   // this-escape
            }

            void x(int newX) {
                x = newX;   // modification event
            }

            int x() {
                return x;   // access event
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // modify after construction
                obj.x = 200;
                assertEquals(1, modCount());
                assertEquals(0, accessCount());

                // access after construction
                assertEquals(200, obj.x);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());

                // mutator method
                obj.x(300);
                assertEquals(2, modCount());
                assertEquals(1, accessCount());

                // accessor method
                assertEquals(300, obj.x());
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }

        @Test
        void testConstructorChaining() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 200);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());
            }
        }

        @Test
        void testThisEscape() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 200, 300);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of a class with a strict field and a
     * non-strict field.
     */
    @Nested
    class InstanceField3 {
        class TestClass {
            int x;
            @StrictInit int y;

            TestClass(int v1, int v2) {
                x = v1;                // modification event not sent
                y = v2;                // modification event not sent
                super();
                assertEquals(v1, x);   // access event not sent
                assertEquals(v2, y);   // access event not sent
            }

            TestClass(int v1, int v2, int v3, int v4) {
                this(v1, v2);
                x = v3;                // modification event not sent
                y = v4;                // modification event not sent
                assertEquals(v3, x);   // access event
                assertEquals(v4, y);   // access event
            }

            TestClass(int v1, int v2, int v3, int v4, int v5, int v6) {
                this(v1, v2, v3, v4);
                x(v5);                   // this-escape
                assertEquals(v5, x());   // this-escape
                y(v6);                   // this-escape
                assertEquals(v6, y());   // this-escape
            }

            void x(int newX) {
                x = newX;    // modification event
            }

            int x() {
                return x;    // access event
            }

            void y(int newY) {
                y = newY;    // modification event
            }

            int y() {
                return y;    // access event
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x");
                 var _ = watch(TestClass.class, "y")) {

                var obj = new TestClass(100, 101);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // modify after construction
                obj.x = 200;
                assertEquals(1, modCount());
                assertEquals(0, accessCount());
                obj.y = 201;
                assertEquals(2, modCount());
                assertEquals(0, accessCount());

                // access after construction
                assertEquals(200, obj.x);
                assertEquals(2, modCount());
                assertEquals(1, accessCount());
                assertEquals(201, obj.y);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());

                // mutator methods
                obj.x(300);
                assertEquals(3, modCount());
                assertEquals(2, accessCount());
                obj.y(301);
                assertEquals(4, modCount());
                assertEquals(2, accessCount());

                // accessor methods
                assertEquals(300, obj.x());
                assertEquals(4, modCount());
                assertEquals(3, accessCount());
                assertEquals(301, obj.y());
                assertEquals(4, modCount());
                assertEquals(4, accessCount());
            }
        }

        @Test
        void testConstructorChaining() {
            try (var _ = watch(TestClass.class, "x");
                 var _ = watch(TestClass.class, "y")) {

                var obj = new TestClass(100, 101, 200, 201);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());
            }
        }

        @Test
        void testThisEscape() {
            try (var _ = watch(TestClass.class, "x");
                 var _ = watch(TestClass.class, "y")) {

                var obj = new TestClass(100, 101, 200, 201, 300, 301);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of a class with a strict field
     * declared in a super class.
     */
    @Nested
    class InstanceField4 {
        class SuperClass {
            @StrictInit int x;

            SuperClass(int v1) {
                x = v1;                // modification event not sent
                super();
                assertEquals(v1, x);   // access event not sent
            }

            SuperClass(int v1, int v2) {
                this(v1);
                x = v2;                // modification event not sent
                assertEquals(v2, x);   // access event not sent
            }

            void x(int newX) {
                x = newX;    // modification event
            }

            int x() {
                return x;    // access event
            }
        }

        class TestClass extends SuperClass {
            TestClass(int v1) {
                super(v1);
            }

            TestClass(int v1, int v2, int v3) {
                super(v1, v2);
                x = v3;                // modification event not sent
                assertEquals(v3, x);   // access event not sent
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(SuperClass.class, "x")) {
                var obj = new TestClass(100);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // modify after construction
                obj.x = 200;
                assertEquals(1, modCount());
                assertEquals(0, accessCount());

                // access after construction
                assertEquals(200, obj.x);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());

                // mutator method
                obj.x(300);
                assertEquals(2, modCount());
                assertEquals(1, accessCount());

                // accessor method
                assertEquals(300, obj.x());
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }

        @Test
        void testConstructorChaining() {
            try (var _ = watch(SuperClass.class, "x")) {
                var obj = new TestClass(100, 200, 300);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of a class with a strict field
     * declared in a subclass.
     */
    @Nested
    class InstanceField5 {
        class SuperClass {
            SuperClass(int value) {
                var thisObject = (TestClass) this;
                thisObject.x = value;                // modification event sent
                assertEquals(value, thisObject.x);   // access event sent
            }
        }

        class TestClass extends SuperClass {
            @StrictInit int x;

            TestClass(int v1, int v2) {
                x = v1;                // modification event not sent
                super(v2);
                assertEquals(v2, x);   // access event not sent
            }

            TestClass(int v1, int v2, int v3) {
                this(v1, v2);
                x = v3;                // modification event not sent
                assertEquals(v3, x);   // access event not sent
            }

            void x(int newX) {
                x = newX;    // modification event
            }

            int x() {
                return x;    // access event
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 200);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());

                // modify after construction
                obj.x = 300;
                assertEquals(2, modCount());
                assertEquals(1, accessCount());

                // access after construction
                assertEquals(300, obj.x);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());

                // mutator method
                obj.x(400);
                assertEquals(3, modCount());
                assertEquals(2, accessCount());

                // accessor method
                assertEquals(400, obj.x());
                assertEquals(3, modCount());
                assertEquals(3, accessCount());
            }
        }

        @Test
        void testThisEscape() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100, 200, 300);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of the class with a strict field when
     * construction posts modification or access events for a class no strict fields.
     */
    @Nested
    class InstanceField6 {
        class Box {
            int value;
            Box(int value) {
                this.value = value;                // modification event
                super();
                assertEquals(value,  this.value);  // access event
            }
        }

        class TestClass {
            @StrictInit Box x;

            TestClass(int v1) {
                x = new Box(v1);      // no modification event sent
                super();
                var _ = x;            // no access event sent
            }

            TestClass(int v1, int v2) {
                this(v1);
                x = new Box(v2);         // no modification event sent
                var _ = x;               // no access event sent
            }

            TestClass(int v1, int v2, int v3) {
                this(v1, v2);
                x(v3);                      // this-escape
                assertEquals(v3, x());      // this-escape
            }

            void x(int v3) {
                x = new Box(v3);     // 2 x modification events
            }

            int x() {
                return x.value;       // 2 x access events
            }
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(Box.class, "value");
                 var _ = watch(TestClass.class, "x")) {

                var obj = new TestClass(100);
                assertEquals(1, modCount());       // putfield Box.value
                assertEquals(1, accessCount());    // getfield Box.value

                // modify after construction
                obj.x = new Box(200);
                assertEquals(3, modCount());       // putfield Box.value && obj.x
                assertEquals(2, accessCount());    // getfield Box.value

                // access after construction
                var _ = obj.x;
                assertEquals(3, modCount());
                assertEquals(3, accessCount());    // getfield obj.x

                // mutator method
                obj.x(300);
                assertEquals(5, modCount());       // putfield Box.value && obj.x
                assertEquals(4, accessCount());    // getfield Box.value && obj.x

                // accessor method
                assertEquals(300, obj.x());         // getfield Box.value && obj.x
                assertEquals(5, modCount());
                assertEquals(6, accessCount());
            }
        }

        @Test
        void testConstructorChaining() {
            try (var _ = watch(Box.class, "value");
                 var _ = watch(TestClass.class, "x")) {

                var obj = new TestClass(100, 200);
                assertEquals(2, modCount());      // 2 * putfield Box.value
                assertEquals(2, accessCount());   // 2 * getfield Box.value
            }
        }

        @Test
        void testThisEscape() {
            try (var _ = watch(Box.class, "value");
                 var _ = watch(TestClass.class, "x")) {

                var obj = new TestClass(100, 200, 300);
                assertEquals(4, modCount());      // 3 * putfield Box.value + x(int)
                assertEquals(5, accessCount());   // 4 * getfield Box.value + x()
            }
        }
    }

    /**
     * Test events posted from the constructor of a value class.
     */
    @Nested
    class ValueClass {
        value class TestClass {
            int x;

            TestClass(int v1) {
                x = v1;               // modification event not sent
                super();
                assertEquals(v1, x);  // access event not sent
            }

            int x() {
                return x;    // access event
            }
        }

        @BeforeAll()
        static void verifyPreconditions() throws Exception {
            assertTrue(TestClass.class.getDeclaredField("x").isStrictInit(),
                    "expected to be strict field");
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // access after construction
                assertEquals(100, obj.x);
                assertEquals(0, modCount());
                assertEquals(1, accessCount());

                // accessor method
                assertEquals(100, obj.x());
                assertEquals(0, modCount());
                assertEquals(2, accessCount());
            }
        }
    }

    /**
     * Test events posted from the constructor of a record class.
     */
    @Nested
    class RecordClass {
        record TestClass(int x) {
            TestClass(int x) {
                this.x = x;                // modification event not sent
                super();
                assertEquals(x, this.x);   // access event not sent
            }
        }

        @BeforeAll()
        static void verifyPreconditions() throws Exception {
            assertTrue(TestClass.class.getDeclaredField("x").isStrictInit(),
                    "expected to be strict field");
        }

        @Test
        void testFieldAccess() {
            try (var _ = watch(TestClass.class, "x")) {
                var obj = new TestClass(100);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // access after construction
                assertEquals(100, obj.x);
                assertEquals(0, modCount());
                assertEquals(1, accessCount());

                // (compiler-generated) accessor method
                assertEquals(100, obj.x());
                assertEquals(0, modCount());
                assertEquals(2, accessCount());
            }
        }
    }

    /**
     * Test events posted from the class initializer of a class with no strict static fields.
     */
    @Nested
    class StaticField1 {
        class TestClass {
            static int x;

            static {
                x = 100;                // modification event
                assertEquals(100, x);   // access event
                postSet(200);
                x = 300;                // modification event
                assertEquals(300, x);   // access event
            }

            static void postSet(int newX) {
                x = newX;                // modification event
                assertEquals(newX, x);   // access event
            }
        }

        @Test
        void testFieldAccess() throws Exception {
            try (var _ = watch(TestClass.class, "x")) {
                MethodHandles.lookup().ensureInitialized(TestClass.class);
                assertEquals(3, modCount());
                assertEquals(3, accessCount());

                // modify after class initializer has run
                TestClass.x = 400;
                assertEquals(4, modCount());
                assertEquals(3, accessCount());

                // access after class initializer has run
                assertEquals(400, TestClass.x);
                assertEquals(4, modCount());
                assertEquals(4, accessCount());
            }
        }
    }

    /**
     * Test events posted from the class initializer of a class with a strict static field.
     */
    @Nested
    class StaticField2 {
        class TestClass {
            @StrictInit static int x;

            static {
                x = 100;                // modification event not sent
                assertEquals(100, x);   // access event not sent
                postSet(200);
                x = 300;                // modification event not sent
                assertEquals(300, x);   // access event not sent
            }

            static void postSet(int newX) {
                x = newX;                   // modification event not sent
                assertEquals(newX, x);      // access event not sent
            }
        }

        @Test
        void testFieldAccess() throws Exception {
            try (var _ = watch(TestClass.class, "x")) {
                MethodHandles.lookup().ensureInitialized(TestClass.class);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // modify after class initializer has run
                TestClass.x = 400;
                assertEquals(1, modCount());
                assertEquals(0, accessCount());

                // access after class initializer has run
                assertEquals(400, TestClass.x);
                assertEquals(1, modCount());
                assertEquals(1, accessCount());
            }
        }
    }

    /**
     * Test events posted from the class initializer of a class with a strict static
     * field and a non-strict static field.
     */
    @Nested
    class StaticField3 {
        class TestClass {
            static int x;
            @StrictInit static int y;

            static {
                x = 100;                 // modification event not sent
                y = 101;                 // modification event not sent
                assertEquals(100, x);    // access event not sent
                assertEquals(101, y);    // access event not sent
                postSet(200, 201);
                x = 300;                 // modification event not sent
                y = 301;                 // modification event not sent
                assertEquals(300, x);    // access event not sent
                assertEquals(301, y);    // access event not sent
            }

            static void postSet(int newX, int newY) {
                x = newX;                // modification event not sent
                y = newY;                // modification event not sent
                assertEquals(newX, x);   // access event not sent
                assertEquals(newY, y);   // access event not sent
            }
        }

        @Test
        void testFieldAccess() throws Exception {
            try (var _ = watch(TestClass.class, "x");
                 var _ = watch(TestClass.class, "y")) {
                MethodHandles.lookup().ensureInitialized(TestClass.class);
                assertEquals(0, modCount());
                assertEquals(0, accessCount());

                // modify after class initializer has run
                TestClass.x = 400;
                assertEquals(1, modCount());
                assertEquals(0, accessCount());
                TestClass.y = 400;
                assertEquals(2, modCount());
                assertEquals(0, accessCount());

                // access after class initializer has run
                assertEquals(400, TestClass.x);
                assertEquals(2, modCount());
                assertEquals(1, accessCount());
                assertEquals(400, TestClass.y);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }
    }

    /**
     * Test events posted from the class initializer of a class with a strict static
     * field and where the initializer causes another class (no strict fields) to be
     * initialized.
     */
    @Nested
    class StaticField4 {
        class TestClass {
            @StrictInit static int x;

            static {
                x = 100;                // modification event not sent
                assertEquals(100, x);   // access event not sent

                // force OtherClass class initializer to execute
                try {
                    MethodHandles.lookup().ensureInitialized(OtherClass.class);
                } catch (Exception e) {
                    fail(e);
                }
            }
        }

        class OtherClass {
            static int y;
            static {
                y = 100;                // modification event
                assertEquals(100, y);   // access event
            }
        }

        @Test
        void testFieldAccess() throws Exception {
            try (var _ = watch(TestClass.class, "x");
                 var _ = watch(OtherClass.class, "y")) {
                MethodHandles.lookup().ensureInitialized(TestClass.class);
                assertEquals(1, modCount());          // putstatic OtherClass.y
                assertEquals(1, accessCount());       // getstatic OtherClass.y

                // modify after class initializer has run
                TestClass.x = 200;
                assertEquals(2, modCount());
                assertEquals(1, accessCount());

                // access after class initializer has run
                assertEquals(200, TestClass.x);
                assertEquals(2, modCount());
                assertEquals(2, accessCount());
            }
        }
    }

    record Watch(Class<?> clazz, long fieldID) implements AutoCloseable {
        @Override
        public void close() {
            clearFieldAccessWatch(clazz, fieldID);
            clearFieldModificationWatch(clazz, fieldID);
        }
    }

    Watch watch(Class<?> clazz, String fieldName) {
        long fieldID = fieldID(clazz, fieldName);
        setFieldModificationWatch(clazz, fieldID);
        setFieldAccessWatch(clazz, fieldID);
        resetModCount();
        resetAccessCount();
        return new Watch(clazz, fieldID);
    }

    /**
     * Return the jfieldID for a static or instance field. This function does not
     * cause the class to be initialized.
     */
    private static native long fieldID(Class<?> clazz, String fieldName);

    /**
     * JVMTI SetFieldModificationWatch, SetFieldAccessWatch, ClearFieldModificationWatch
     * and ClearFieldAccessWatch.
     */
    private static native void setFieldModificationWatch(Class<?> clazz, long fieldID);
    private static native void clearFieldModificationWatch(Class<?> clazz, long fieldID);
    private static native void setFieldAccessWatch(Class<?> clazz, long fieldID);
    private static native void clearFieldAccessWatch(Class<?> clazz, long fieldID);

    /**
     * Counters.
     */
    private static native void resetModCount();
    private static native void resetAccessCount();
    private static native int modCount();
    private static native int accessCount();
}
