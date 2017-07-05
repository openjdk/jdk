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

import sun.jvm.hotspot.asm.Register;
import sun.jvm.hotspot.utilities.Assert;

public class SPARCFloatRegister extends SPARCRegister {

    public SPARCFloatRegister(int number) {
        super(number);
    }

    public int getNumber() {
        return number;
    }

    public static final int SINGLE_PRECISION = 1;
    public static final int DOUBLE_PRECISION = 2;
    public static final int QUAD_PRECISION = 3;

    public int getNumber(int width) {
        switch (width) {
        case SINGLE_PRECISION:
            Assert.that(number < 32, "bad single-prec fp register");
            return number;

        case DOUBLE_PRECISION:
            Assert.that(number < 64 && (number & 1) == 0, "bad double-prec fp register");
            return number & 0x1e | (number & 0x20) >> 5;

        case QUAD_PRECISION:
            Assert.that(number < 64 && (number & 3) == 0, "bad quad-prec fp register");
            return number & 0x1c | (number & 0x20) >> 5;
        }
        throw new RuntimeException("Invalid floating point width supplied");
    }

    private static final int nofRegisters = 63;
    public int getNumberOfRegisters() {
        return nofRegisters;
    }

    public boolean isFloat() {
        return true;
    }

    public boolean isFramePointer() {
        return false;
    }

    public boolean isStackPointer() {
        return false;
    }

    public boolean isV9Only() {
        return number > 31;
    }

    public boolean isValid() {
        return number >= 0 && number < nofRegisters;
    }

    public String toString() {
        return SPARCFloatRegisters.getRegisterName(number);
    }

}
