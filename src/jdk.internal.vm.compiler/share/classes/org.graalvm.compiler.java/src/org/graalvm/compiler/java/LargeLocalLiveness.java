/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import java.util.BitSet;

import org.graalvm.compiler.java.BciBlockMapping.BciBlock;

public final class LargeLocalLiveness extends LocalLiveness {
    private BitSet[] localsLiveIn;
    private BitSet[] localsLiveOut;
    private BitSet[] localsLiveGen;
    private BitSet[] localsLiveKill;
    private BitSet[] localsChangedInLoop;

    public LargeLocalLiveness(BciBlock[] blocks, int maxLocals, int loopCount) {
        super(blocks);
        int blocksSize = blocks.length;
        localsLiveIn = new BitSet[blocksSize];
        localsLiveOut = new BitSet[blocksSize];
        localsLiveGen = new BitSet[blocksSize];
        localsLiveKill = new BitSet[blocksSize];
        for (int i = 0; i < blocksSize; i++) {
            localsLiveIn[i] = new BitSet(maxLocals);
            localsLiveOut[i] = new BitSet(maxLocals);
            localsLiveGen[i] = new BitSet(maxLocals);
            localsLiveKill[i] = new BitSet(maxLocals);
        }
        localsChangedInLoop = new BitSet[loopCount];
        for (int i = 0; i < loopCount; ++i) {
            localsChangedInLoop[i] = new BitSet(maxLocals);
        }
    }

    @Override
    protected String debugLiveIn(int blockID) {
        return localsLiveIn[blockID].toString();
    }

    @Override
    protected String debugLiveOut(int blockID) {
        return localsLiveOut[blockID].toString();
    }

    @Override
    protected String debugLiveGen(int blockID) {
        return localsLiveGen[blockID].toString();
    }

    @Override
    protected String debugLiveKill(int blockID) {
        return localsLiveKill[blockID].toString();
    }

    @Override
    protected int liveOutCardinality(int blockID) {
        return localsLiveOut[blockID].cardinality();
    }

    @Override
    protected void propagateLiveness(int blockID, int successorID) {
        localsLiveOut[blockID].or(localsLiveIn[successorID]);
    }

    @Override
    protected void updateLiveness(int blockID) {
        BitSet liveIn = localsLiveIn[blockID];
        liveIn.clear();
        liveIn.or(localsLiveOut[blockID]);
        liveIn.andNot(localsLiveKill[blockID]);
        liveIn.or(localsLiveGen[blockID]);
    }

    @Override
    protected void loadOne(int blockID, int local) {
        if (!localsLiveKill[blockID].get(local)) {
            localsLiveGen[blockID].set(local);
        }
    }

    @Override
    protected void storeOne(int blockID, int local) {
        if (!localsLiveGen[blockID].get(local)) {
            localsLiveKill[blockID].set(local);
        }

        BciBlock block = blocks[blockID];
        long tmp = block.loops;
        int pos = 0;
        while (tmp != 0) {
            if ((tmp & 1L) == 1L) {
                this.localsChangedInLoop[pos].set(local);
            }
            tmp >>>= 1;
            ++pos;
        }
    }

    @Override
    public boolean localIsLiveIn(BciBlock block, int local) {
        return block.getId() >= Integer.MAX_VALUE ? true : localsLiveIn[block.getId()].get(local);
    }

    @Override
    public boolean localIsLiveOut(BciBlock block, int local) {
        return block.getId() >= Integer.MAX_VALUE ? true : localsLiveOut[block.getId()].get(local);
    }

    @Override
    public boolean localIsChangedInLoop(int loopId, int local) {
        return localsChangedInLoop[loopId].get(local);
    }
}
