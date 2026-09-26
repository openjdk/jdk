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
 * @summary Test Field access to a strictly-initialized static field when initializing
 *     the field's class.
 * @enablePreview
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor AccessStrictInitInClinit$TestClass
 * @run junit/othervm ${test.main.class}
 */

import java.io.InputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import jdk.test.lib.helpers.StrictInit;

import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AccessStrictInitInClinit {

    static ThrowingConsumer<Field> thunk;

    /**
     * Test class with a strictly-initialized static field.
     */
    static class TestClass {
        @StrictInit
        static Object aStaticField;

        static {
            if (thunk != null) {
                try {
                    Field f = TestClass.class.getDeclaredField("aStaticField");
                    thunk.accept(f);
                } catch (Throwable e) {
                    fail(e);
                }
            }
            aStaticField = new Object();
        }

        /**
         * The class bytes for this class.
         */
        private final static LazyConstant<byte[]> CLASS_BYTES = LazyConstant.of(() -> {
            String rn = TestClass.class.getName() + ".class";
            InputStream in = TestClass.class.getResourceAsStream(rn);
            assertNotNull(in);
            try (in) {
                return in.readAllBytes();
            } catch (IOException ioe) {
                fail(ioe);
                return null;
            }
        });

        /**
         * Define a class with a strictly-initialized static field. Perform the given
         * action with the reflected Field before the field has been initialized.
         */
        static void executeBeforeFieldSet(ThrowingConsumer<Field> consumer) {
            thunk = consumer;
            try {
                var _ = MethodHandles.lookup().defineHiddenClass(CLASS_BYTES.get(), true);
            } catch (Throwable e) {
                fail(e);
            } finally {
                thunk = null;
            }
        }
    }

    /**
     * Test Field.set/get to access field when initializing the field's class.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testFieldSetGet(boolean accessible) throws Exception {
        TestClass.executeBeforeFieldSet(f -> {
            assertTrue(f.isStrictInit());
            f.setAccessible(accessible);

            // Field.get before initialized
            assertThrows(IllegalStateException.class, () -> f.get(null));

            // initialize with Field.set
            Object value = new Object();
            f.set(null, value);
            assertSame(value, f.get(null));
        });
    }

    /**
     * Test setter/getter method handles produced from a Field to access field when
     * initializing the field's class.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testUnreflectSetterGetter(boolean accessible) {
        TestClass.executeBeforeFieldSet(f -> {
            assertTrue(f.isStrictInit());
            f.setAccessible(accessible);
            MethodHandle setter = MethodHandles.lookup().unreflectSetter(f);
            MethodHandle getter = MethodHandles.lookup().unreflectGetter(f);

            // invoke MH getter before initialized
            assertThrows(IllegalStateException.class, () -> {
                var _ = (Object) getter.invokeExact();
            });

            // initialize with MH setter
            Object value = new Object();
            setter.invokeExact(value);
            assertSame(value, f.get(null));
            assertSame(value, (Object) getter.invokeExact());
        });
    }

    /**
     * Test VarHandle produced from a Field to access field when initializing the field's class.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void testUnreflectVarHandle(boolean accessible) {
        TestClass.executeBeforeFieldSet(f -> {
            assertTrue(f.isStrictInit());
            f.setAccessible(accessible);
            VarHandle vh = MethodHandles.lookup().unreflectVarHandle(f);

            // use VH get before initialized
            assertThrows(IllegalStateException.class, () -> vh.get());
            assertThrows(IllegalStateException.class, () -> vh.getAndSet(new Object()));

            // initialize with VH set
            Object value1 = new Object();
            Object value2 = new Object();
            vh.set(value1);
            assertSame(value1, f.get(null));
            assertSame(value1, vh.get());
            assertSame(value1, vh.getAndSet(value2));
            assertSame(value2, f.get(null));
            assertSame(value2, vh.get());
        });
    }
}
