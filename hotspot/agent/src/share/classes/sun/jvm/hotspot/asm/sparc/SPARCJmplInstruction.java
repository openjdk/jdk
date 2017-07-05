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

public class SPARCJmplInstruction extends SPARCInstruction
    implements BranchInstruction {
    final protected SPARCRegisterIndirectAddress addr;
    final protected SPARCRegister rd;

    protected SPARCJmplInstruction(String name, SPARCRegisterIndirectAddress addr, SPARCRegister rd) {
        super(name);
        this.addr = addr;
        this.rd = rd;
    }

    public SPARCJmplInstruction(SPARCRegisterIndirectAddress addr, SPARCRegister rd) {
        this("jmpl", addr, rd);
    }

    protected String getDescription() {
        StringBuffer buf = new StringBuffer();
        String addrStr = addr.toString();
        // remove '[' & ']' from address
        addrStr = addrStr.substring(1, addrStr.length() - 1);
        if (rd == SPARCRegisters.G0) {
            buf.append("jmp");
            buf.append(spaces);
            buf.append(addrStr);
        } else {
            buf.append(getName());
            buf.append(spaces);
            buf.append(addrStr);
            buf.append(comma);
            buf.append(rd.toString());
        }

        return buf.toString();
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        return getDescription();
    }

    public Address getBranchDestination() {
        return addr;
    }

    public SPARCRegister getReturnAddressRegister() {
        return rd;
    }

    public boolean isAnnuledBranch() {
        return false;
    }

    public boolean isBranch() {
        return true;
    }

    public boolean isConditional() {
        return false;
    }
}
