/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.aarch64;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;

/**
 * AArch64 specific frame map.
 * <p/>
 * This is the format of an AArch64 stack frame:
 * <p/>
 *
 * <pre>
 *   Base       Contents
 *
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *   ---------+--------------------------------+-------------------------
 *            | return address                 |    |            ^
 *            | prev. frame pointer            |    |            |
 *            +--------------------------------+    |            |
 *            | spill slot 0                   |    | negative   |      ^
 *    callee  :     ...                        :    v offsets    |      |
 *    frame   | spill slot n                   |  -----        total  frame
 *            +--------------------------------+               frame  size
 *            | alignment padding              |               size     |
 *            +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |      |
 *            :     ...                        :    | positive   |      |
 *            | outgoing overflow argument 0   |    | offsets    v      v
 *    %sp-->  +--------------------------------+---------------------------
 *
 * </pre>
 *
 * The spill slot area also includes stack allocated memory blocks (ALLOCA blocks). The size of such
 * a block may be greater than the size of a normal spill slot or the word size.
 * <p/>
 * A runtime can reserve space at the beginning of the overflow argument area. The calling
 * convention can specify that the first overflow stack argument is not at offset 0, but at a
 * specified offset. Use {@link CodeCacheProvider#getMinimumOutgoingSize()} to make sure that
 * call-free methods also have this space reserved. Then the VM can use the memory at offset 0
 * relative to the stack pointer.
 * <p/>
 */
public class AArch64FrameMap extends FrameMap {
    // Note: Spill size includes callee save area

    /**
     * Creates a new frame map for the specified method.
     */
    public AArch64FrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        super(codeCache, registerConfig, referenceMapFactory);
        initialSpillSize = frameSetupSize();
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        // frameSize + return address + frame pointer
        return frameSize() + frameSetupSize();
    }

    private int frameSetupSize() {
        // Size of return address and frame pointer that are saved in function prologue
        return getTarget().arch.getWordSize() * 2;
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize);
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        assert spillSize == initialSpillSize : "Deoptimization rescue slot must be the first stack slot";
        return allocateSpillSlot(LIRKind.value(AArch64Kind.QWORD));
    }
}
