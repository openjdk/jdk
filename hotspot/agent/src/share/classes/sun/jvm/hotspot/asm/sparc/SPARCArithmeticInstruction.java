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

public class SPARCArithmeticInstruction extends SPARCFormat3AInstruction
    implements ArithmeticInstruction {
    final private int operation;

    public SPARCArithmeticInstruction(String name, int opcode, int operation, SPARCRegister rs1,
                                      ImmediateOrRegister operand2, SPARCRegister rd) {
        super(name, opcode, rs1, operand2, rd);
        this.operation = operation;
    }

    protected String getDescription() {
        if (rd == rs1 && operand2.isImmediate()) {
            int value = ((Immediate)operand2).getNumber().intValue();
            StringBuffer buf = new StringBuffer();
            switch (opcode) {
                case ADD:
                    buf.append("inc");
                    break;
                case ADDcc:
                    buf.append("inccc");
                    break;
                case SUB:
                    buf.append("dec");
                    break;
                case SUBcc:
                    buf.append("deccc");
                    break;
                default:
                    return super.getDescription();
            }
            buf.append(spaces);
            if (value != 1) {
                buf.append(getOperand2String()); buf.append(comma);
            }
            buf.append(rd.toString());
            return buf.toString();
        } else if (rd == SPARCRegisters.G0 && opcode == SUBcc) {
            StringBuffer buf = new StringBuffer();
            buf.append("cmp");
            buf.append(spaces);
            buf.append(rs1.toString());
            buf.append(comma);
            buf.append(getOperand2String());
            return buf.toString();
        } else if (rs1 == SPARCRegisters.G0 && opcode == SUB && operand2.isRegister()) {
            StringBuffer buf = new StringBuffer();
            buf.append("neg");
            buf.append(spaces);
            buf.append(operand2.toString());
            if (operand2 != rd) {
                buf.append(comma);
                buf.append(rd.toString());
            }
            return buf.toString();
        }

        return super.getDescription();
    }

    public Operand getArithmeticDestination() {
        return getDestinationRegister();
    }

    public Operand[] getArithmeticSources() {
        return (new Operand[] { rs1, operand2 });
    }

    public int getOperation() {
        return operation;
    }

    public boolean isArithmetic() {
        return true;
    }
}
