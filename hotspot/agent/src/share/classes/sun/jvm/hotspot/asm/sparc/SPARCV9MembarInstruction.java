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

import sun.jvm.hotspot.asm.SymbolFinder;
import java.util.Vector;

public class SPARCV9MembarInstruction extends SPARCInstruction
                  implements SPARCV9Instruction {
    final private int mmask;
    final private int cmask;
    final private String description;

    public SPARCV9MembarInstruction(int mmask, int cmask) {
        super("membar");
        this.mmask = mmask & 0xF;
        this.cmask = cmask & 0x7;
        description = initDescription();
    }

    private String initDescription() {
        StringBuffer buf = new StringBuffer();
        buf.append(getName());
        buf.append(spaces);

        Vector masks = new Vector();
        if ((mmask & 0x1) != 0)
            masks.add("#LoadLoad");
        if ((mmask & 0x2) != 0)
            masks.add("#StoreLoad");
        if ((mmask & 0x4) != 0)
            masks.add("#LoadStore");
        if ((mmask & 0x8) != 0)
            masks.add("#StoreStore");

        if ((cmask & 0x1) != 0)
            masks.add("#Lookaside");
        if ((cmask & 0x2) != 0)
            masks.add("#MemIssue");
        if ((cmask & 0x4) != 0)
            masks.add("#Sync");

        // add all masks
        Object[] tempMasks = masks.toArray();
        for (int i=0; i < tempMasks.length - 1; i++) {
            buf.append((String)tempMasks[i]);
            buf.append("| ");
        }
        buf.append((String)tempMasks[tempMasks.length - 1]);

        return buf.toString();
    }

    public int getMMask() {
        return mmask;
    }

    public int getCMask() {
        return cmask;
    }

    public String asString(long currentPc, SymbolFinder symFinder) {
        return description;
    }
}
