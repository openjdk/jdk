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

import sun.jvm.hotspot.asm.RTLDataTypes;

class RegisterDecoder implements /* imports */ RTLDataTypes {
    // refer to page 40 - section 5.1.4.1 - Floating-point Register Number Encoding
    private static SPARCFloatRegister decodeDouble(int num) {
        // 6 bit double precision registers are encoded in 5 bits as
        // b<4> b<3> b<2> b<1> b<5>.

        boolean lsb = (0x1 & num) != 0;
        if (lsb)
            num |= 0x20;  // 10000b

        if ((num % 2) != 0)
            return null;

        return SPARCFloatRegisters.getRegister(num);
    }

    private static SPARCFloatRegister decodeQuad(int num) {
        // 6 bit quad precision registers are encoded in 5 bits as
        // b<4> b<3> b<2> 0 b<5>

        boolean lsb = (0x1 & num) != 0;
        if (lsb)
            num |= 0x20; // 10000b

        if ((num % 4) != 0)
            return null;

        return SPARCFloatRegisters.getRegister(num);
    }

    static SPARCRegister decode(int dataType, int regNum) {
        regNum &= 0x1F; // mask out all but lsb 5 bits
        SPARCRegister result = null;
        switch (dataType) {
            case RTLDT_FL_SINGLE:
                result = SPARCFloatRegisters.getRegister(regNum);
                break;

            case RTLDT_FL_DOUBLE:
                result = decodeDouble(regNum);
                break;

            case RTLDT_FL_QUAD:
                result = decodeQuad(regNum);
                break;

            case RTLDT_UNKNOWN:
                result = null;
                break;

            default: // some integer register
                result = SPARCRegisters.getRegister(regNum);
                break;
        }
        return result;
    }
}
