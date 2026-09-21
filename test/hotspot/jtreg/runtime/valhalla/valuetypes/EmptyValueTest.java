/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package runtime.valhalla.valuetypes;

import jdk.test.lib.Asserts;

import java.lang.reflect.Field;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;


/*
 * @test
 * @summary Test support for empty value types (no instance fields)
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @compile EmptyValueTest.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseFieldFlattening runtime.valhalla.valuetypes.EmptyValueTest
 */

public class EmptyValueTest {

    @LooselyConsistentValue
    static value class EmptyValue {
        public boolean isEmpty() {
            return true;
        }
    }

    @LooselyConsistentValue
    static value class EmptyField {
        @NullRestricted
        EmptyValue empty;

        EmptyField() {
            this.empty = new EmptyValue();
        }
    }

    static class WithInt {
        int i;
    }

    static class WithEmptyField extends WithInt  {
        // With current layout strategy for reference classs, the empty
        // value field would be placed between the int and the Object
        // fields, along with some padding.
        Object o;
        @NullRestricted
        EmptyValue empty;

        WithEmptyField() {
            empty = new EmptyValue();
            super();
        }
    }

    public static void main(String[] args) {
        // Create an empty value
        EmptyValue empty = new EmptyValue();
        Asserts.assertTrue(empty.isEmpty());

        // Create a value with an empty value field
        EmptyField emptyField = new EmptyField();
        Asserts.assertEquals(emptyField.empty.getClass(), EmptyValue.class);
        Asserts.assertTrue(emptyField.empty.isEmpty());
        System.out.println(emptyField.empty.isEmpty());

        // Regular instance with an empty field inside
        WithEmptyField w = new WithEmptyField();
        Asserts.assertEquals(w.empty.getClass(), EmptyValue.class);
        Asserts.assertTrue(w.empty.isEmpty());
        w.empty = new EmptyValue();
        Asserts.assertEquals(w.empty.getClass(), EmptyValue.class);
        Asserts.assertTrue(w.empty.isEmpty());

        // Create an array of empty values
        EmptyValue[] emptyArray = (EmptyValue[])ValueClass.newNullRestrictedNonAtomicArray(EmptyValue.class, 100, new EmptyValue());
        for(EmptyValue element : emptyArray) {
            Asserts.assertEquals(element.getClass(), EmptyValue.class);
            Asserts.assertTrue(element.isEmpty());
        }

        // Testing arrayCopy
        EmptyValue[] array2 = (EmptyValue[])ValueClass.newNullRestrictedNonAtomicArray(EmptyValue.class, 100, new EmptyValue());
        // with two arrays
        System.arraycopy(emptyArray, 10, array2, 20, 50);
        for(EmptyValue element : array2) {
            Asserts.assertEquals(element.getClass(), EmptyValue.class);
            Asserts.assertTrue(element.isEmpty());
        }
        // single array, no overlap
        System.arraycopy(emptyArray, 10, emptyArray, 50, 20);
        for(EmptyValue element : emptyArray) {
            Asserts.assertEquals(element.getClass(), EmptyValue.class);
            Asserts.assertTrue(element.isEmpty());
        }
        // single array with overlap
        System.arraycopy(emptyArray, 10, emptyArray, 20, 50);
        for(EmptyValue element : emptyArray) {
            Asserts.assertEquals(element.getClass(), EmptyValue.class);
            Asserts.assertTrue(element.isEmpty());
        }

        // Passing an empty value in argument
        assert isEmpty(empty);

        // Returning an empty value
        assert getEmpty().isEmpty();

        // Checking fields with reflection
        Class<?> c = empty.getClass();
        try {
            Field[] fields = c.getDeclaredFields();
            Asserts.assertTrue(fields.length == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        WithEmptyField w0 = new WithEmptyField();
        Class<?> c2 = w0.getClass();
        try {
            Field emptyfield = c2.getDeclaredField("empty");
            EmptyValue e = (EmptyValue)emptyfield.get(w0);
            Asserts.assertEquals(e.getClass(), EmptyValue.class);
            Asserts.assertTrue(e.isEmpty());
            emptyfield.set(w0, new EmptyValue());
            e = (EmptyValue)emptyfield.get(w0);
            Asserts.assertEquals(e.getClass(), EmptyValue.class);
            Asserts.assertTrue(e.isEmpty());
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Reflection tests failed: " + t);
        }

        // Testing JIT compiler
        // for(int i=0; i < 100000; i++) {
        //     test();
        // }
    }

    static boolean isEmpty(EmptyValue empty) {
        return empty.isEmpty();
    }

    static EmptyValue getEmpty() {
        return new EmptyValue();
    }

    static void test() {
        for(int i=0; i < 10000; i++) {
            Asserts.assertTrue(getEmpty().isEmpty());
        }
    }
}
