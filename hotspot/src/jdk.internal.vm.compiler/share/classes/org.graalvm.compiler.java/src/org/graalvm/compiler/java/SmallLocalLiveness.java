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

import org.graalvm.compiler.java.BciBlockMapping.BciBlock;

public final class SmallLocalLiveness extends LocalLiveness {
    /*
     * local n is represented by the bit accessible as (1 << n)
     */

    private final long[] localsLiveIn;
    private final long[] localsLiveOut;
    private final long[] localsLiveGen;
    private final long[] localsLiveKill;
    private final long[] localsChangedInLoop;
    private final int maxLocals;

    public SmallLocalLiveness(BciBlock[] blocks, int maxLocals, int loopCount) {
        super(blocks);
        this.maxLocals = maxLocals;
        int blockSize = blocks.length;
        localsLiveIn = new long[blockSize];
        localsLiveOut = new long[blockSize];
        localsLiveGen = new long[blockSize];
        localsLiveKill = new long[blockSize];
        localsChangedInLoop = new long[loopCount];
    }

    private String debugString(long value) {
        StringBuilder str = new StringBuilder("{");
        long current = value;
        for (int i = 0; i < maxLocals; i++) {
            if ((current & 1L) == 1L) {
                if (str.length() > 1) {
                    str.append(", ");
                }
                str.append(i);
            }
            current >>= 1;
        }
        return str.append('}').toString();
    }

    @Override
    protected String debugLiveIn(int blockID) {
        return debugString(localsLiveIn[blockID]);
    }

    @Override
    protected String debugLiveOut(int blockID) {
        return debugString(localsLiveOut[blockID]);
    }

    @Override
    protected String debugLiveGen(int blockID) {
        return debugString(localsLiveGen[blockID]);
    }

    @Override
    protected String debugLiveKill(int blockID) {
        return debugString(localsLiveKill[blockID]);
    }

    @Override
    protected int liveOutCardinality(int blockID) {
        return Long.bitCount(localsLiveOut[blockID]);
    }

    @Override
    protected void propagateLiveness(int blockID, int successorID) {
        localsLiveOut[blockID] |= localsLiveIn[successorID];
    }

    @Override
    protected void updateLiveness(int blockID) {
        localsLiveIn[blockID] = (localsLiveOut[blockID] & ~localsLiveKill[blockID]) | localsLiveGen[blockID];
    }

    @Override
    protected void loadOne(int blockID, int local) {
        long bit = 1L << local;
        if ((localsLiveKill[blockID] & bit) == 0L) {
            localsLiveGen[blockID] |= bit;
        }
    }

    @Override
    protected void storeOne(int blockID, int local) {
        long bit = 1L << local;
        if ((localsLiveGen[blockID] & bit) == 0L) {
            localsLiveKill[blockID] |= bit;
        }

        BciBlock block = blocks[blockID];
        long tmp = block.loops;
        int pos = 0;
        while (tmp != 0) {
            if ((tmp & 1L) == 1L) {
                this.localsChangedInLoop[pos] |= bit;
            }
            tmp >>>= 1;
            ++pos;
        }
    }

    @Override
    public boolean localIsLiveIn(BciBlock block, int local) {
        int blockID = block.getId();
        return blockID >= Integer.MAX_VALUE ? false : (localsLiveIn[blockID] & (1L << local)) != 0L;
    }

    @Override
    public boolean localIsLiveOut(BciBlock block, int local) {
        int blockID = block.getId();
        return blockID >= Integer.MAX_VALUE ? false : (localsLiveOut[blockID] & (1L << local)) != 0L;
    }

    @Override
    public boolean localIsChangedInLoop(int loopId, int local) {
        return (localsChangedInLoop[loopId] & (1L << local)) != 0L;
    }
}
