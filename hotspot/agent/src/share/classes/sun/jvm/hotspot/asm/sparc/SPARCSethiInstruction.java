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

public class SPARCSethiInstruction extends SPARCInstruction
    implements MoveInstruction {
    final private SPARCRegister register;
    final private ImmediateOrRegister value;
    final private String description;

    public SPARCSethiInstruction(int value, SPARCRegister register) {
        super("sethi");
        this.register = register;
        value <<= 10;
        this.value = new Immediate(new Integer(value));
        description = initDescription(value);
    }

    private String initDescription(int val) {
        if (val == 0 && register == SPARCRegisters.G0) {
            return "nop";
        } else {
            StringBuffer buf = new StringBuffer();
            buf.append(getName());
            buf.append(spaces);
            buf.append("%hi(0x");
            buf.append(Integer.toHexString(val));
            buf.append(')');
            buf.append(comma);
            buf.append(register.toString());
            return buf.toString();
        }
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        return description;
    }

    public Register getMoveDestination() {
        return register;
    }

    public ImmediateOrRegister getMoveSource() {
        return value;
    }

    public boolean isConditional() {
        return false;
    }
}
