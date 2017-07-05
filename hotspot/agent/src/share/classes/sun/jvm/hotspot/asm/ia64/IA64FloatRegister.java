/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm.ia64;

import sun.jvm.hotspot.asm.Register;
import sun.jvm.hotspot.utilities.Assert;

public class IA64FloatRegister extends IA64Register {

    public IA64FloatRegister(int number) {
        super(number);
    }

    public int getNumber() {
        return number;
    }

    public static final int SINGLE_PRECISION = 1;
    public static final int DOUBLE_PRECISION = 2;
    public static final int QUAD_PRECISION = 3;

    public int getNumber(int width) {
        return number;
    }

    private static final int nofRegisters = 128;
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

    public boolean isValid() {
        return number >= 0 && number < nofRegisters;
    }

    public String toString() {
        return IA64FloatRegisters.getRegisterName(number);
    }

}
