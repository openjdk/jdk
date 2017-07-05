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

public class SPARCV9RegisterBranchInstruction extends SPARCInstruction
    implements SPARCV9Instruction, BranchInstruction {
    final protected PCRelativeAddress addr;
    final protected boolean isAnnuled;
    final protected int regConditionCode;
    final protected SPARCRegister conditionRegister;
    final protected boolean predictTaken;

    public SPARCV9RegisterBranchInstruction(String name, PCRelativeAddress addr,
                               boolean isAnnuled, int regConditionCode,
                               SPARCRegister conditionRegister, boolean predictTaken) {
        super(name);
        this.addr = addr;
        this.isAnnuled = isAnnuled;
        this.regConditionCode = regConditionCode;
        this.conditionRegister = conditionRegister;
        this.predictTaken = predictTaken;
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        long address = addr.getDisplacement() + currentPc;
        StringBuffer buf = new StringBuffer();
        buf.append(getName());
        buf.append(spaces);
        buf.append(symFinder.getSymbolFor(address));
        return buf.toString();
    }

    public boolean isBranch() {
        return true;
    }

    public Address getBranchDestination() {
        return addr;
    }

    public boolean isAnnuledBranch() {
        return isAnnuled;
    }

    public boolean isConditional() {
        return true;
    }

    public int getRegisterConditionCode() {
        return regConditionCode;
    }

    public SPARCRegister getConditionRegister() {
        return conditionRegister;
    }

    public boolean getPredictTaken() {
        return predictTaken;
    }
}
