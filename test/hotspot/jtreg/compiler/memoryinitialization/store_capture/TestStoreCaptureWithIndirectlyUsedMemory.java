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
 * @bug 8396039
 * @summary ReduceFieldZeroing doesn't check deep enough for dependent load and can lead to incorrect execution
 * @library /test/lib
 * @run main ${test.main.class}
 */

package compiler.memoryinitialization.store_capture;

import jdk.test.lib.Asserts;

public class TestStoreCaptureWithIndirectlyUsedMemory {
    static class Holder {
        Object value;
    }

    static final Object PRE_ALLOCATED_OBJECT = new Object();
    static final Holder PRE_ALLOCATED_HOLDER = new Holder();
    static {
        PRE_ALLOCATED_HOLDER.value = PRE_ALLOCATED_OBJECT;
    }

    // Something global wrt. test to make assignment leak outside, so we can't optimize them away.
    static Holder sink;

    static Object test(boolean usePreAllocated, Holder preAllocated, Object fresh) {
        if (preAllocated == null) {
            throw new NullPointerException();
        }
        Holder holder = new Holder();
        // holder.value == null
        // preAllocated.value != null
        Holder selectedHolder = usePreAllocated ? preAllocated : holder;  // Simple enough to be made a CMove.
        // usePreAllocated <=> selectedHolder.value != null
        Object result = selectedHolder.value;  // selectedHolder can be holder. The base of the load is the previous CMove node.
        // usePreAllocated <=> result != null
        holder.value = fresh;  // This store shouldn't be captured because the previous can read from holder.
        // holder.value != null, so selectedHolder.value != null, but result shouldn't be changed, and we must still have
        // usePreAllocated <=> result != null
        // observing !usePreAllocated && result != null is wrong!
        // But because of excessive capturing, we can load `fresh` into result (through holder), instead of null.
        // If usePreAllocated is true, then the load reads from preAllocated while this store writes in holder
        // which are different object, so we can't observe the wrong capture.

        // Let's use holder, so it's not optimized away
        sink = holder;
        return result;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; i++) {
            boolean usePreAllocated = i % 2 == 0;
            Object expected = usePreAllocated ? PRE_ALLOCATED_OBJECT : null;
            Object fresh = new Object();
            Object result = test(usePreAllocated, PRE_ALLOCATED_HOLDER, fresh);
            // We should have usePreAllocated <=> result != null,
            // and always expected = result
            Asserts.assertEQ(
                expected,
                result,
                "iteration=" + i +
                ", usePreAllocated=" + usePreAllocated +
                ", expected=" + expected +
                ", result=" + result +
                ", fresh=" + fresh
            );
        }
    }
}
