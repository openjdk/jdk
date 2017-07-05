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

public abstract class SPARCV9PrivilegedRegisterInstruction extends SPARCInstruction
                          implements SPARCV9Instruction, /* imports */ SPARCV9PrivilegedRegisters {
    protected static final String regNames[] = {
        "%tpc", "%tnpc", "%tstate", "%tt", "%tick", "%tba", "%pstate", "%tl",
        "%pil", "%cwp",  "%cansave", "%canrestore", "%cleanwin", "%otherwin", "%wstate", "%fq"
    };

    protected static String getPrivilegedRegisterName(int regNum) {
        if ((regNum > 15 && regNum < 31) || regNum > 31)
            return null;
        return (regNum == 31)? "%ver" : regNames[regNum];
    }

    final protected int regNum;

    protected abstract String getDescription();

    protected SPARCV9PrivilegedRegisterInstruction(String name, int regNum) {
        super(name);
        this.regNum = regNum;
    }

    public int getPrivilegedRegisterNumber() {
        return regNum;
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        return getDescription();
    }
}
