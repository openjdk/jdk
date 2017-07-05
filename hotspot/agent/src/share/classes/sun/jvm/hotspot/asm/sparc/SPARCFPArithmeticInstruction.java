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

public class SPARCFPArithmeticInstruction extends SPARCFormat3AInstruction
                    implements ArithmeticInstruction {
    final private SPARCRegister rs2;
    final private int rtlOperation;

    public SPARCFPArithmeticInstruction(String name, int opcode, int rtlOperation,
                                 SPARCRegister rs1, SPARCRegister rs2,
                                 SPARCRegister rd) {
        super(name, opcode, rs1, rs2, rd);
        this.rs2 = rs2;
        this.rtlOperation = rtlOperation;
    }

    protected String getDescription() {
        StringBuffer buf = new StringBuffer();
        buf.append(getName());
        buf.append(spaces);
        buf.append(rs1.toString());
        buf.append(comma);
        buf.append(rs2.toString());
        buf.append(comma);
        buf.append(rd.toString());
        return buf.toString();
    }

    public int getOperation() {
        return rtlOperation;
    }

    public Operand[] getArithmeticSources() {
        return new Operand[] { rs1, rs2 };
    }

    public Operand getArithmeticDestination() {
        return rd;
    }

    public boolean isFloat() {
        return true;
    }
}
