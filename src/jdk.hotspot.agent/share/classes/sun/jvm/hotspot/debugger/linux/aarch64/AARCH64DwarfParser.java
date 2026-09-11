/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

package sun.jvm.hotspot.debugger.linux.aarch64;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.linux.DwarfParser;


public class AARCH64DwarfParser extends DwarfParser {

    private static native long createDwarfContext(long lib);

    public AARCH64DwarfParser(Address lib) {
        super(createDwarfContext(lib.asLongValue()));
    }

    /**
     * @return true if return address (RA) is signed by PAC.
     */
    public boolean isRASigned() {
        return isRASigned0(p_dwarf_context);
    }

    public native boolean isRASigned0(long inst);

}
