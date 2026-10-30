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
 * @bug 8392838 8394450
 * @summary Test that C2 propagates the correct escape state from
 *          null-restricted array allocations to their initialization values.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run driver ${test.main.class}
 */

class TestNullRestrictedArrayEscapeState {

    public static void main(String[] args) {
        Scenario defaultLayout =
            new Scenario(0);
        Scenario referenceLayout =
            new Scenario(1, "-XX:-UseArrayFlattening");
        Scenario nonAtomicFlatLayout =
            new Scenario(2, "-XX:+UseArrayFlattening",
                            "-XX:+UnlockExperimentalVMOptions",
                            "-XX:+UseNullFreeNonAtomicValueFlattening");
        Scenario atomicFlatLayout
            = new Scenario(3, "-XX:+UseArrayFlattening",
                              "-XX:+UnlockExperimentalVMOptions",
                              "-XX:-UseNullFreeNonAtomicValueFlattening",
                              "-XX:+UseNullFreeAtomicValueFlattening");
        TestFramework testFramework = new TestFramework();
        testFramework
            .addFlags("--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                      "--enable-preview")
            .addScenarios(defaultLayout, referenceLayout, nonAtomicFlatLayout, atomicFlatLayout);
        testFramework.start();
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

    static IdentityObject globalObj = null;
    static ValueHolder globalHolder = null;
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

    @ForceInline
    static ValueHolder[] createNullRestrictedAtomicArray(ValueHolder holder) {
        return (ValueHolder[])ValueClass.newNullRestrictedAtomicArray(ValueHolder.class, 1, holder);
    }

    @ForceInline
    static ValueHolder[] createNullRestrictedNonAtomicArray(ValueHolder holder) {
        return (ValueHolder[])ValueClass.newNullRestrictedNonAtomicArray(ValueHolder.class, 1, holder);
    }

    @DontInline
    static void escapeFirstHolder(ValueHolder[] holders) {
        globalHolder = holders[0];
    }

    @DontInline
    static void dontUse(ValueHolder[] holders) {}

    // Allocate a null-restricted atomic array, initialize it with a holder that
    // references an identity object, and make it escape globally. C2 should
    // propagate the array's global escape state to the referenced identity
    // object, and preserve the synchronization operations.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testAllocateEscapingNullRestrictedAtomicArray() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders = createNullRestrictedAtomicArray(holder);
        globalHolders = holders;
    }

    // Variant of the above test using a null-restricted non-atomic array.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testAllocateEscapingNullRestrictedNonAtomicArray() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders = createNullRestrictedNonAtomicArray(holder);
        globalHolders = holders;
    }

    // Variant of the above test where the holder, rather than the array itself,
    // escapes within a non-inlined call. C2 should propagate the global escape
    // state of the array's fields to the referenced identity object, and
    // preserve the synchronization operations.
    @Test
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    static void testAllocateNullRestrictedAtomicArrayWithEscapingElements() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders = createNullRestrictedAtomicArray(holder);
        escapeFirstHolder(holders);
    }

    // Allocate a null-restricted atomic array and initialize it with a holder
    // that references an identity object. Since the non-inlined call does not
    // access the array at all, C2 should classify the identity object as not
    // globally escaping and optimize away the synchronization operations.
    @Test
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    static void testAllocateNullRestrictedAtomicArrayWithNonEscapingElements() {
        ValueHolder holder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] holders = createNullRestrictedAtomicArray(holder);
        dontUse(holders);
    }

    // Allocate two null-restricted atomic arrays and initialize the source
    // array with a holder that references an identity object. Then array-copy
    // the source array into the destination array, load the holder, and store
    // its referent identity object globally, causing it to escape. C2 should
    // propagate the global escape state all the way from the destination
    // array's load to the identity object, and preserve the synchronization
    // operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static void testAllocateNullRestrictedArraysThenCopyThenLoadAndEscape() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedAtomicArray(srcHolder);
        ValueHolder dstHolder = new ValueHolder(null);
        ValueHolder[] dst = createNullRestrictedAtomicArray(dstHolder);
        System.arraycopy(src, 0, dst, 0, 1);
        globalObj = dst[0].obj;
    }

    // Variant of the above test where the identity object escapes indirectly
    // through its holder. C2 should propagate the global escape state and
    // preserve the synchronization operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static void testAllocateNullRestrictedArraysThenCopyThenLoadAndEscapeHolder() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedAtomicArray(srcHolder);
        ValueHolder dstHolder = new ValueHolder(null);
        ValueHolder[] dst = createNullRestrictedAtomicArray(dstHolder);
        System.arraycopy(src, 0, dst, 0, 1);
        globalHolder = dst[0];
    }

    // Variant of the above tests using array cloning instead of copying. C2
    // should preserve the synchronization operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static void testAllocateNullRestrictedArraysThenCloneThenLoadAndEscape() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedAtomicArray(srcHolder);
        ValueHolder[] dst = src.clone();
        globalObj = dst[0].obj;
    }

    // Variant of the above tests using non-atomic arrays. C2 should preserve
    // the synchronization operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.FAST_LOCK, "> 0", IRNode.FAST_UNLOCK, "> 0"})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static void testAllocateNullRestrictedNonAtomicArraysThenCopyThenLoadAndEscape() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedNonAtomicArray(srcHolder);
        ValueHolder dstHolder = new ValueHolder(null);
        ValueHolder[] dst = createNullRestrictedNonAtomicArray(dstHolder);
        System.arraycopy(src, 0, dst, 0, 1);
        globalObj = dst[0].obj;
    }

    // Variant of the above tests where the loaded identity object does not
    // escape. C2 should optimize away the synchronization operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static int testAllocateNullRestrictedArraysThenCopyThenLoadButDontEscape() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedAtomicArray(srcHolder);
        ValueHolder dstHolder = new ValueHolder(null);
        ValueHolder[] dst = createNullRestrictedAtomicArray(dstHolder);
        System.arraycopy(src, 0, dst, 0, 1);
        return dst[0].obj.val;
    }

    // Variant of the above tests using array cloning instead of copying. C2
    // should optimize away the synchronization operations.
    @Test
    // Test precondition: the array copy is fully expanded.
    @IR(counts = {IRNode.ARRAY_COPY, "= 1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.FAST_LOCK, IRNode.FAST_UNLOCK})
    // Ensure we test the compilation where the array copy is fully expanded.
    @Warmup(10_000)
    static int testAllocateNullRestrictedArraysThenCloneThenLoadButDontEscape() {
        ValueHolder srcHolder = createValueHolderWithSynchronizedFieldWrite();
        ValueHolder[] src = createNullRestrictedAtomicArray(srcHolder);
        ValueHolder[] dst = src.clone();
        return dst[0].obj.val;
    }
}
