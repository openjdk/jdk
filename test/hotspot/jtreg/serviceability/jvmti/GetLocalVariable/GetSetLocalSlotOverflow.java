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
 * @bug 8387718
 * @summary VM_GetOrSetLocal slot bounds check overflows for long/double slots,
 *          allowing an out-of-bounds StackValueCollection access when slot == INT_MAX.
 * @requires vm.jvmti
 * @compile GetSetLocalSlotOverflow.java
 * @run main/othervm/native -agentlib:GetSetLocalSlotOverflow GetSetLocalSlotOverflow
 */

/*
 * Regression test / reproducer for the signed-overflow in
 * VM_BaseGetOrSetLocal::check_slot_type_no_lvt (jvmtiImpl.cpp).
 *
 * For a T_LONG/T_DOUBLE access, the bounds check is
 *     if (_index < 0 || _index + extra_slot >= method->max_locals())
 * with extra_slot == 1. When the agent passes slot == INT_MAX, the
 * sub-expression _index + extra_slot overflows to INT_MIN, which is < max_locals(),
 * so the guard passes and the code goes on to index locals->at(INT_MAX).
 *
 * Expected (fixed) behavior: GetLocalLong/Double and SetLocalLong/Double with
 * slot == INT_MAX return JVMTI_ERROR_INVALID_SLOT. JVMTI only supports SetLocal
 * on the topmost frame of a virtual thread, and the targeted runner() frame is
 * not topmost, so on a virtual thread SetLocal is rejected before the slot check
 * is reached and cannot exercise the overflow; the set sub-tests are skipped there.
 *
 * On an unfixed VM this test does not merely fail: the out-of-bounds access
 * crashes the VM (assertion failure in fastdebug, SIGSEGV / silent corruption
 * in product). A clean PASS is only possible once the bounds check is fixed.
 */

public class GetSetLocalSlotOverflow {

    // Invoked from runner(); the agent inspects the runner() frame at depth 1.
    // JVMTI only supports SetLocal on the topmost frame of a virtual thread, so
    // the agent skips the set sub-tests when the thread is virtual.
    // Returns false if any accessor did not return JVMTI_ERROR_INVALID_SLOT.
    static native boolean testOverflow(Thread thread, boolean isVirtual);

    public static void main(String[] args) throws Exception {
        if (!runner()) {
            throw new RuntimeException("Test GetSetLocalSlotOverflow failed");
        }
    }

    // A Java frame holding a few locals. The agent targets this frame (depth 1)
    // with slot == INT_MAX. The actual local contents are irrelevant: the
    // overflow happens in the slot bounds check, before any local is read.
    public static boolean runner() {
        long l = 0xCAFEBABEL;
        double d = 3.14d;
        Thread self = Thread.currentThread();
        boolean ok = testOverflow(self, self.isVirtual());
        // Keep locals live across the native call.
        if (l == 0 && d == 0) {
            throw new AssertionError("unreachable");
        }
        return ok;
    }
}
