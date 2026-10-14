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
 * @bug 8392533 8393322
 * @summary Test null checks for field accesses with empty value classes.
 * @enablePreview
 * @requires vm.compiler1.enabled
 * @requires (vm.opt.PreloadClasses == null | vm.opt.PreloadClasses == "true")
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class}
 * @run main/othervm -XX:TieredStopAtLevel=1 ${test.main.class}
 * @run main/othervm -XX:TieredStopAtLevel=1
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-EliminateNullChecks
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

public class TestEmptyValueFieldNullChecks {
    static boolean initialized = false;

    static value class Empty { }

    static value class UninitializedEmpty {
        static {
            initialized = true;
        }
    }

    static class UninitializedHolder {
        @NullRestricted
        UninitializedEmpty empty;
        int value;

        UninitializedHolder() {
            empty = new UninitializedEmpty();
            super();
        }
    }

    // Recursively empty value class (only contains null marker)
    static value class Holder1 {
        Empty empty;

        Holder1() {
            empty = new Empty();
            super();
        }
    }

    // Same as Holder1 but with null-restricted empty field
    static value class Holder2 {
        @NullRestricted
        Empty empty;

        Holder2() {
            empty = new Empty();
            super();
        }
    }

    Empty empty;

    @NullRestricted
    Empty emptyNullFree;

    Holder1 holder1;

    Holder2 holder2;

    @NullRestricted
    Holder1 holder1NullFree;

    @NullRestricted
    Holder2 holder2NullFree;

    TestEmptyValueFieldNullChecks() {
        empty = new Empty();
        emptyNullFree = new Empty();
        holder1 = new Holder1();
        holder2 = new Holder2();
        holder1NullFree = new Holder1();
        holder2NullFree = new Holder2();
        super();
    }

    // A null holder must throw before buffering initializes its empty field class.
    static Object testUninitializedEmptyLoad(UninitializedHolder outerHolder) {
        Object empty = outerHolder.empty;
        // This later access must not absorb the explicit null check across the buffer allocation.
        return outerHolder.value == 0 ? empty : null;
    }

    // An empty nullable store only updates its null marker with Unsafe and must null-check its holder.
    static void testEmptyStore(TestEmptyValueFieldNullChecks outerHolder, Empty value) {
        outerHolder.empty = value;
    }

    // An empty null-free store emits no access but must null-check both operands.
    static void testEmptyNullFreeStore(TestEmptyValueFieldNullChecks outerHolder, Empty value) {
        outerHolder.emptyNullFree = value;
    }

    // A marker-only atomic store must null-check its holder.
    static void testRecursivelyEmptyStore(TestEmptyValueFieldNullChecks outerHolder, Holder1 value) {
        outerHolder.holder1 = value;
    }

    // A recursively empty nullable store only updates its null marker with Unsafe and must null-check its holder.
    static void testRecursivelyEmptyStore2(TestEmptyValueFieldNullChecks outerHolder, Holder2 value) {
        outerHolder.holder2 = value;
    }

    // A marker-only null-free store only emits Unsafe accesses and must null-check both operands.
    static void testRecursivelyEmptyNullFreeStore(TestEmptyValueFieldNullChecks outerHolder, Holder1 value) {
        outerHolder.holder1NullFree = value;
    }

    // A recursively empty null-free store emits no access but must null-check both operands.
    static void testRecursivelyEmptyNullFreeStore2(TestEmptyValueFieldNullChecks outerHolder, Holder2 value) {
        outerHolder.holder2NullFree = value;
    }

    // An empty nullable load only reads its null marker with Unsafe and must null-check its holder.
    static Empty testEmptyLoad(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.empty;
    }

    // An empty null-free load emits no access but must null-check its holder.
    static Empty testEmptyNullFreeLoad(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.emptyNullFree;
    }

    // A marker-only atomic load must null-check its holder.
    static Holder1 testRecursivelyEmptyLoad(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.holder1;
    }

    // A recursively empty nullable load only reads its null marker with Unsafe and must null-check its holder.
    static Holder2 testRecursivelyEmptyLoad2(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.holder2;
    }

    // A marker-only null-free load only emits Unsafe accesses and must null-check its holder.
    static Holder1 testRecursivelyEmptyNullFreeLoad(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.holder1NullFree;
    }

    // A recursively empty null-free load emits no access but must null-check its holder.
    static Holder2 testRecursivelyEmptyNullFreeLoad2(TestEmptyValueFieldNullChecks outerHolder) {
        return outerHolder.holder2NullFree;
    }

    public static void main(String[] args) {
        Empty empty = new Empty();
        TestEmptyValueFieldNullChecks outerHolder = new TestEmptyValueFieldNullChecks();
        Holder1 innerHolder = new Holder1();
        Holder2 innerHolder2 = new Holder2();
        for (int i = 0; i < 20_000; i++) {
            Asserts.assertThrows(NullPointerException.class, () -> testUninitializedEmptyLoad(null));
            testEmptyStore(outerHolder, null);
            testEmptyNullFreeStore(outerHolder, empty);
            testRecursivelyEmptyStore(outerHolder, innerHolder);
            testRecursivelyEmptyStore2(outerHolder, innerHolder2);
            testRecursivelyEmptyNullFreeStore(outerHolder, innerHolder);
            testRecursivelyEmptyNullFreeStore2(outerHolder, innerHolder2);
            Asserts.assertEquals(testEmptyLoad(outerHolder), outerHolder.empty);
            Asserts.assertEquals(testEmptyNullFreeLoad(outerHolder), outerHolder.emptyNullFree);
            Asserts.assertEquals(testRecursivelyEmptyLoad(outerHolder), outerHolder.holder1);
            Asserts.assertEquals(testRecursivelyEmptyLoad2(outerHolder), outerHolder.holder2);
            Asserts.assertEquals(testRecursivelyEmptyNullFreeLoad(outerHolder), outerHolder.holder1NullFree);
            Asserts.assertEquals(testRecursivelyEmptyNullFreeLoad2(outerHolder), outerHolder.holder2NullFree);
        }
        Asserts.assertFalse(initialized, "Should not have been initialized!");

        Asserts.assertThrows(NullPointerException.class, () -> testEmptyStore(null, null));
        Asserts.assertThrows(NullPointerException.class, () -> testEmptyStore(null, empty));
        Asserts.assertThrows(NullPointerException.class, () -> testEmptyNullFreeStore(null, empty));
        Asserts.assertThrows(NullPointerException.class, () -> testEmptyNullFreeStore(outerHolder, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyStore(null, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyStore(null, innerHolder));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyStore2(null, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyStore2(null, innerHolder2));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore(null, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore(null, innerHolder));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore(outerHolder, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore2(null, null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore2(null, innerHolder2));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeStore2(outerHolder, null));
        Asserts.assertThrows(NullPointerException.class, () -> testEmptyLoad(null));
        Asserts.assertThrows(NullPointerException.class, () -> testEmptyNullFreeLoad(null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyLoad(null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyLoad2(null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeLoad(null));
        Asserts.assertThrows(NullPointerException.class, () -> testRecursivelyEmptyNullFreeLoad2(null));
    }
}

