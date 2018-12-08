/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asStackSlot;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;

/**
 * AMD64 specific frame map.
 *
 * This is the format of an AMD64 stack frame:
 *
 * <pre>
 *   Base       Contents
 *
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *   ---------+--------------------------------+---------------------
 *            | return address                 |    |            ^
 *   current  +--------------------------------+    |            |    -----
 *   frame    |                                |    |            |      ^
 *            : callee save area               :    |            |      |
 *            |                                |    |            |      |
 *            +--------------------------------+    |            |      |
 *            | spill slot 0                   |    | negative   |      |
 *            :     ...                        :    v offsets    |      |
 *            | spill slot n                   |  -----        total  frame
 *            +--------------------------------+               frame  size
 *            | alignment padding              |               size     |
 *            +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |      |
 *            :     ...                        :    | positive   |      |
 *            | outgoing overflow argument 0   |    | offsets    v      v
 *    %sp--&gt;  +--------------------------------+---------------------------
 *
 * </pre>
 *
 * The spill slot area also includes stack allocated memory blocks (ALLOCA blocks). The size of such
 * a block may be greater than the size of a normal spill slot or the word size.
 * <p>
 * A runtime can reserve space at the beginning of the overflow argument area. The calling
 * convention can specify that the first overflow stack argument is not at offset 0, but at a
 * specified offset. Use {@link CodeCacheProvider#getMinimumOutgoingSize()} to make sure that
 * call-free methods also have this space reserved. Then the VM can use the memory at offset 0
 * relative to the stack pointer.
 */
public class AMD64FrameMap extends FrameMap {

    private StackSlot rbpSpillSlot;

    public AMD64FrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        this(codeCache, registerConfig, referenceMapFactory, false);
    }

    public AMD64FrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory, boolean useBasePointer) {
        super(codeCache, registerConfig, referenceMapFactory);
        // (negative) offset relative to sp + total frame size
        initialSpillSize = returnAddressSize() + (useBasePointer ? getTarget().arch.getWordSize() : 0);
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        int result = frameSize() + initialSpillSize;
        assert result % getTarget().stackAlignment == 0 : "Total frame size not aligned: " + result;
        return result;
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize - initialSpillSize);
    }

    @Override
    protected int alignFrameSize(int size) {
        return NumUtil.roundUp(size + initialSpillSize, getTarget().stackAlignment) - initialSpillSize;
    }

    @Override
    public int offsetForStackSlot(StackSlot slot) {
        // @formatter:off
        assert (!slot.getRawAddFrameSize() && slot.getRawOffset() <  outgoingSize) ||
               (slot.getRawAddFrameSize() && slot.getRawOffset()  <  0 && -slot.getRawOffset() <= spillSize) ||
               (slot.getRawAddFrameSize() && slot.getRawOffset()  >= 0) :
                   String.format("RawAddFrameSize: %b RawOffset: 0x%x spillSize: 0x%x outgoingSize: 0x%x", slot.getRawAddFrameSize(), slot.getRawOffset(), spillSize, outgoingSize);
        // @formatter:on
        return super.offsetForStackSlot(slot);
    }

    /**
     * For non-leaf methods, RBP is preserved in the special stack slot required by the HotSpot
     * runtime for walking/inspecting frames of such methods.
     */
    StackSlot allocateRBPSpillSlot() {
        assert spillSize == initialSpillSize : "RBP spill slot must be the first allocated stack slots";
        rbpSpillSlot = allocateSpillSlot(LIRKind.value(AMD64Kind.QWORD));
        assert asStackSlot(rbpSpillSlot).getRawOffset() == -16 : asStackSlot(rbpSpillSlot).getRawOffset();
        return rbpSpillSlot;
    }

    void freeRBPSpillSlot() {
        int size = spillSlotSize(LIRKind.value(AMD64Kind.QWORD));
        assert spillSize == NumUtil.roundUp(initialSpillSize + size, size) : "RBP spill slot can not be freed after allocation other stack slots";
        spillSize = initialSpillSize;
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        assert spillSize == initialSpillSize || spillSize == initialSpillSize +
                        spillSlotSize(LIRKind.value(AMD64Kind.QWORD)) : "Deoptimization rescue slot must be the first or second (if there is an RBP spill slot) stack slot";
        return allocateSpillSlot(LIRKind.value(AMD64Kind.QWORD));
    }
}
