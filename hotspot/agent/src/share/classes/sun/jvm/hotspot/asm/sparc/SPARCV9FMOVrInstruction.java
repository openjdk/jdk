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

public class SPARCV9FMOVrInstruction extends SPARCFPMoveInstruction
                     implements SPARCV9Instruction {
    final private int regConditionCode;
    final private SPARCRegister rs1;

    public SPARCV9FMOVrInstruction(String name, int opf, SPARCRegister rs1,
                                   SPARCFloatRegister rs2, SPARCFloatRegister rd,
                                   int regConditionCode) {
        super(name, opf, rs2, rd);
        this.regConditionCode = regConditionCode;
        this.rs1 = rs1;
    }

    protected String getDescription() {
        StringBuffer buf = new StringBuffer();
        buf.append(getName());
        buf.append(spaces);
        buf.append(rs1.toString());
        buf.append(comma);
        buf.append(rs.toString());
        buf.append(comma);
        buf.append(rd.toString());
        return buf.toString();
    }

    public int getRegisterConditionCode() {
        return regConditionCode;
    }

    public boolean isConditional() {
        return true;
    }

    public Register getConditionRegister() {
        return rs1;
    }
}
