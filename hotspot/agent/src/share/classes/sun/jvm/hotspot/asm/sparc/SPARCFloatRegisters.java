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

import sun.jvm.hotspot.utilities.Assert;

public class SPARCFloatRegisters {
    public static int getNumRegisters() {
        return 64;
    }

    public static SPARCFloatRegister getRegister(int i) {
        Assert.that(i >= 0 && i < 64, "float register number is invalid");
        return registers[i];
    }

    public static String getRegisterName(int i) {
        return "%f" + i;
    }

    public static final SPARCFloatRegister F0;
    public static final SPARCFloatRegister F1;
    public static final SPARCFloatRegister F2;
    public static final SPARCFloatRegister F3;
    public static final SPARCFloatRegister F4;
    public static final SPARCFloatRegister F5;
    public static final SPARCFloatRegister F6;
    public static final SPARCFloatRegister F7;
    public static final SPARCFloatRegister F8;
    public static final SPARCFloatRegister F9;
    public static final SPARCFloatRegister F10;
    public static final SPARCFloatRegister F11;
    public static final SPARCFloatRegister F12;
    public static final SPARCFloatRegister F13;
    public static final SPARCFloatRegister F14;
    public static final SPARCFloatRegister F15;
    public static final SPARCFloatRegister F16;
    public static final SPARCFloatRegister F17;
    public static final SPARCFloatRegister F18;
    public static final SPARCFloatRegister F19;
    public static final SPARCFloatRegister F20;
    public static final SPARCFloatRegister F21;
    public static final SPARCFloatRegister F22;
    public static final SPARCFloatRegister F23;
    public static final SPARCFloatRegister F24;
    public static final SPARCFloatRegister F25;
    public static final SPARCFloatRegister F26;
    public static final SPARCFloatRegister F27;
    public static final SPARCFloatRegister F28;
    public static final SPARCFloatRegister F29;
    public static final SPARCFloatRegister F30;
    public static final SPARCFloatRegister F31;
    public static final SPARCFloatRegister F32;
    public static final SPARCFloatRegister F34;
    public static final SPARCFloatRegister F36;
    public static final SPARCFloatRegister F38;
    public static final SPARCFloatRegister F40;
    public static final SPARCFloatRegister F42;
    public static final SPARCFloatRegister F44;
    public static final SPARCFloatRegister F46;
    public static final SPARCFloatRegister F48;
    public static final SPARCFloatRegister F50;
    public static final SPARCFloatRegister F52;
    public static final SPARCFloatRegister F54;
    public static final SPARCFloatRegister F56;
    public static final SPARCFloatRegister F58;
    public static final SPARCFloatRegister F60;
    public static final SPARCFloatRegister F62;
    public static final int NUM_REGISTERS = 64;
    private static final SPARCFloatRegister registers[];

    static {
        F0 = new SPARCFloatRegister(0);
        F1 = new SPARCFloatRegister(1);
        F2 = new SPARCFloatRegister(2);
        F3 = new SPARCFloatRegister(3);
        F4 = new SPARCFloatRegister(4);
        F5 = new SPARCFloatRegister(5);
        F6 = new SPARCFloatRegister(6);
        F7 = new SPARCFloatRegister(7);
        F8 = new SPARCFloatRegister(8);
        F9 = new SPARCFloatRegister(9);
        F10 = new SPARCFloatRegister(10);
        F11 = new SPARCFloatRegister(11);
        F12 = new SPARCFloatRegister(12);
        F13 = new SPARCFloatRegister(13);
        F14 = new SPARCFloatRegister(14);
        F15 = new SPARCFloatRegister(15);
        F16 = new SPARCFloatRegister(16);
        F17 = new SPARCFloatRegister(17);
        F18 = new SPARCFloatRegister(18);
        F19 = new SPARCFloatRegister(19);
        F20 = new SPARCFloatRegister(20);
        F21 = new SPARCFloatRegister(21);
        F22 = new SPARCFloatRegister(22);
        F23 = new SPARCFloatRegister(23);
        F24 = new SPARCFloatRegister(24);
        F25 = new SPARCFloatRegister(25);
        F26 = new SPARCFloatRegister(26);
        F27 = new SPARCFloatRegister(27);
        F28 = new SPARCFloatRegister(28);
        F29 = new SPARCFloatRegister(29);
        F30 = new SPARCFloatRegister(30);
        F31 = new SPARCFloatRegister(31);
        F32 = new SPARCFloatRegister(32);
        F34 = new SPARCFloatRegister(34);
        F36 = new SPARCFloatRegister(36);
        F38 = new SPARCFloatRegister(38);
        F40 = new SPARCFloatRegister(40);
        F42 = new SPARCFloatRegister(42);
        F44 = new SPARCFloatRegister(44);
        F46 = new SPARCFloatRegister(46);
        F48 = new SPARCFloatRegister(48);
        F50 = new SPARCFloatRegister(50);
        F52 = new SPARCFloatRegister(52);
        F54 = new SPARCFloatRegister(54);
        F56 = new SPARCFloatRegister(56);
        F58 = new SPARCFloatRegister(58);
        F60 = new SPARCFloatRegister(60);
        F62 = new SPARCFloatRegister(62);
        registers = (new SPARCFloatRegister[] {
            F0, F2, F3, F4, F5, F6, F7, F8, F9, F10,
            F11, F12, F13, F14, F15, F16, F17, F18, F19, F20,
            F21, F22, F23, F24, F25, F26, F27, F28, F29, F30,
            F31, F32, null, F34, null, F36, null, F38, null, F40,
            null, F42, null, F44, null, F46, null, F48, null, F50,
            null, F52, null, F54, null, F56, null, F58, null, F60,
            null, F62, null
        });
    }
}
