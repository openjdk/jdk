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

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;
import jdk.internal.value.ValueClass;

/**
 * @test
 * @bug 8392838
 * @summary Test that C2 propagates the correct escape state from
 *          null-restricted array allocations to their initialization values.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run driver ${test.main.class}
 */

class TestNullRestrictedArrayEscapeState {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                                   "--enable-preview");
    }

    static class IdentityObject {
        int val;
    }

    static value class ValueHolder {
        IdentityObject obj;
        ValueHolder(IdentityObject obj) {
            this.obj = obj;
        }
    }

    static ValueHolder[] globalHolders = null;

    @ForceInline
    static ValueHolder createValueHolderWithSynchronizedFieldWrite() {
        IdentityObject obj = new IdentityObject();
        ValueHolder holder = new ValueHolder(obj);
        synchronized (obj) {
            obj.val = 42;
        }
        return holder;
    }

    // Allocate a null-restricted atomic array, initialize it with a holder that
    // references an identity object, and make it escape globally. C2 should
    // propagate the array's global escape state to the referenced identity
    // object, and preserve the synchronization operations.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testAllocateEscapingNullRestrictedAtomicArray() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders =
            (ValueHolder[])ValueClass.newNullRestrictedAtomicArray(ValueHolder.class, 1, holder);
        globalHolders = holders;
    }

    // Variant of the above test using a null-restricted non-atomic array.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testAllocateEscapingNullRestrictedNonAtomicArray() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders =
            (ValueHolder[])ValueClass.newNullRestrictedNonAtomicArray(ValueHolder.class, 1, holder);
        globalHolders = holders;
    }
}
