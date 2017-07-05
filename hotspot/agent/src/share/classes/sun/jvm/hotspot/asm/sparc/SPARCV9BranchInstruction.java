/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;

public class SPARCV9BranchInstruction extends SPARCBranchInstruction
                    implements SPARCV9Instruction {
    final private boolean predictTaken;
    final private int conditionFlag; // icc, xcc or fccn - condition bits selected

    public SPARCV9BranchInstruction(String name, PCRelativeAddress addr,
              boolean isAnnuled, int conditionCode, boolean predictTaken, int conditionFlag) {
        super((name += (predictTaken)? ",pt" : ",pn"), addr, isAnnuled, conditionCode);
        this.predictTaken = predictTaken;
        this.conditionFlag = conditionFlag;
    }

    public boolean getPredictTaken() {
        return predictTaken;
    }

    public String getConditionFlagName() {
        return SPARCV9ConditionFlags.getFlagName(conditionFlag);
    }

    public int getConditionFlag() {
        return conditionFlag;
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        long address = addr.getDisplacement() + currentPc;
        StringBuffer buf = new StringBuffer();
        buf.append(getName());
        buf.append(spaces);

        // add conditionFlag bit used.
        buf.append(getConditionFlagName());
        buf.append(comma);
        buf.append(symFinder.getSymbolFor(address));
        return buf.toString();
    }
}
