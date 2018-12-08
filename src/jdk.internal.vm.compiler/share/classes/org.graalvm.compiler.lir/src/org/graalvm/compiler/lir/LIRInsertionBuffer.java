/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A buffer to enqueue updates to a list. This avoids frequent re-sizing of the list and copying of
 * list elements when insertions are done at multiple positions of the list. Additionally, it
 * ensures that the list is not modified while it is, e.g., iterated, and instead only modified once
 * after the iteration is done.
 * <p>
 * The buffer uses internal data structures to store the enqueued updates. To avoid allocations, a
 * buffer can be re-used. Call the methods in the following order: {@link #init}, {@link #append},
 * {@link #append}, ..., {@link #finish()}, {@link #init}, ...
 * <p>
 * Note: This class does not depend on LIRInstruction, so we could make it a generic utility class.
 */
public final class LIRInsertionBuffer {

    /**
     * The lir list where ops of this buffer should be inserted later (null when uninitialized).
     */
    private List<LIRInstruction> lir;

    /**
     * List of insertion points. index and count are stored alternately: indexAndCount[i * 2]: the
     * index into lir list where "count" ops should be inserted indexAndCount[i * 2 + 1]: the number
     * of ops to be inserted at index
     */
    private int[] indexAndCount;
    private int indexAndCountSize;

    /**
     * The LIROps to be inserted.
     */
    private final List<LIRInstruction> ops;

    public LIRInsertionBuffer() {
        indexAndCount = new int[8];
        ops = new ArrayList<>(4);
    }

    /**
     * Initialize this buffer. This method must be called before using {@link #append}.
     */
    public void init(List<LIRInstruction> newLir) {
        assert !initialized() : "already initialized";
        assert indexAndCountSize == 0 && ops.size() == 0;
        this.lir = newLir;
    }

    public boolean initialized() {
        return lir != null;
    }

    public List<LIRInstruction> lirList() {
        return lir;
    }

    /**
     * Enqueue a new instruction that will be appended to the instruction list when
     * {@link #finish()} is called. The new instruction is added <b>before</b> the existing
     * instruction with the given index. This method can only be called with increasing values of
     * index, e.g., once an instruction was appended with index 4, subsequent instructions can only
     * be appended with index 4 or higher.
     */
    public void append(int index, LIRInstruction op) {
        int i = numberOfInsertionPoints() - 1;
        if (i < 0 || indexAt(i) < index) {
            appendNew(index, 1);
        } else {
            assert indexAt(i) == index : "can append LIROps in ascending order only";
            assert countAt(i) > 0 : "check";
            setCountAt(i, countAt(i) + 1);
        }
        ops.add(op);

        assert verify();
    }

    /**
     * Append all enqueued instructions to the instruction list. After that, {@link #init(List)} can
     * be called again to re-use this buffer.
     */
    public void finish() {
        if (ops.size() > 0) {
            int n = lir.size();
            // increase size of instructions list
            for (int i = 0; i < ops.size(); i++) {
                lir.add(null);
            }
            // insert ops from buffer into instructions list
            int opIndex = ops.size() - 1;
            int ipIndex = numberOfInsertionPoints() - 1;
            int fromIndex = n - 1;
            int toIndex = lir.size() - 1;
            while (ipIndex >= 0) {
                int index = indexAt(ipIndex);
                // make room after insertion point
                while (fromIndex >= index) {
                    lir.set(toIndex--, lir.get(fromIndex--));
                }
                // insert ops from buffer
                for (int i = countAt(ipIndex); i > 0; i--) {
                    lir.set(toIndex--, ops.get(opIndex--));
                }
                ipIndex--;
            }
            indexAndCountSize = 0;
            ops.clear();
        }
        lir = null;
    }

    private void appendNew(int index, int count) {
        int oldSize = indexAndCountSize;
        int newSize = oldSize + 2;
        if (newSize > this.indexAndCount.length) {
            indexAndCount = Arrays.copyOf(indexAndCount, newSize * 2);
        }
        indexAndCount[oldSize] = index;
        indexAndCount[oldSize + 1] = count;
        this.indexAndCountSize = newSize;
    }

    private void setCountAt(int i, int value) {
        indexAndCount[(i << 1) + 1] = value;
    }

    private int numberOfInsertionPoints() {
        assert indexAndCount.length % 2 == 0 : "must have a count for each index";
        return indexAndCountSize >> 1;
    }

    private int indexAt(int i) {
        return indexAndCount[(i << 1)];
    }

    private int countAt(int i) {
        return indexAndCount[(i << 1) + 1];
    }

    private boolean verify() {
        int sum = 0;
        int prevIdx = -1;

        for (int i = 0; i < numberOfInsertionPoints(); i++) {
            assert prevIdx < indexAt(i) : "index must be ordered ascending";
            sum += countAt(i);
        }
        assert sum == ops.size() : "wrong total sum";
        return true;
    }
}
