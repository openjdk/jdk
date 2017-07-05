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

import sun.jvm.hotspot.utilities.Assert;

public class IA64FloatRegisters {
    public static int getNumRegisters() {
        return 128;
    }

    public static IA64FloatRegister getRegister(int i) {
        Assert.that(i >= 0 && i < 128, "float register number is invalid");
        return registers[i];
    }

    public static String getRegisterName(int i) {
        return "%f" + i;
    }

    public static final IA64FloatRegister F0;
    public static final IA64FloatRegister F1;
    public static final IA64FloatRegister F2;
    public static final IA64FloatRegister F3;
    public static final IA64FloatRegister F4;
    public static final IA64FloatRegister F5;
    public static final IA64FloatRegister F6;
    public static final IA64FloatRegister F7;
    public static final IA64FloatRegister F8;
    public static final IA64FloatRegister F9;
    public static final IA64FloatRegister F10;
    public static final IA64FloatRegister F11;
    public static final IA64FloatRegister F12;
    public static final IA64FloatRegister F13;
    public static final IA64FloatRegister F14;
    public static final IA64FloatRegister F15;
    public static final IA64FloatRegister F16;
    public static final IA64FloatRegister F17;
    public static final IA64FloatRegister F18;
    public static final IA64FloatRegister F19;
    public static final IA64FloatRegister F20;
    public static final IA64FloatRegister F21;
    public static final IA64FloatRegister F22;
    public static final IA64FloatRegister F23;
    public static final IA64FloatRegister F24;
    public static final IA64FloatRegister F25;
    public static final IA64FloatRegister F26;
    public static final IA64FloatRegister F27;
    public static final IA64FloatRegister F28;
    public static final IA64FloatRegister F29;
    public static final IA64FloatRegister F30;
    public static final IA64FloatRegister F31;
    public static final IA64FloatRegister F32;
    public static final IA64FloatRegister F33;
    public static final IA64FloatRegister F34;
    public static final IA64FloatRegister F35;
    public static final IA64FloatRegister F36;
    public static final IA64FloatRegister F37;
    public static final IA64FloatRegister F38;
    public static final IA64FloatRegister F39;
    public static final IA64FloatRegister F40;
    public static final IA64FloatRegister F41;
    public static final IA64FloatRegister F42;
    public static final IA64FloatRegister F43;
    public static final IA64FloatRegister F44;
    public static final IA64FloatRegister F45;
    public static final IA64FloatRegister F46;
    public static final IA64FloatRegister F47;
    public static final IA64FloatRegister F48;
    public static final IA64FloatRegister F49;
    public static final IA64FloatRegister F50;
    public static final IA64FloatRegister F51;
    public static final IA64FloatRegister F52;
    public static final IA64FloatRegister F53;
    public static final IA64FloatRegister F54;
    public static final IA64FloatRegister F55;
    public static final IA64FloatRegister F56;
    public static final IA64FloatRegister F57;
    public static final IA64FloatRegister F58;
    public static final IA64FloatRegister F59;
    public static final IA64FloatRegister F60;
    public static final IA64FloatRegister F61;
    public static final IA64FloatRegister F62;
    public static final IA64FloatRegister F63;
    public static final IA64FloatRegister F64;
    public static final IA64FloatRegister F65;
    public static final IA64FloatRegister F66;
    public static final IA64FloatRegister F67;
    public static final IA64FloatRegister F68;
    public static final IA64FloatRegister F69;
    public static final IA64FloatRegister F70;
    public static final IA64FloatRegister F71;
    public static final IA64FloatRegister F72;
    public static final IA64FloatRegister F73;
    public static final IA64FloatRegister F74;
    public static final IA64FloatRegister F75;
    public static final IA64FloatRegister F76;
    public static final IA64FloatRegister F77;
    public static final IA64FloatRegister F78;
    public static final IA64FloatRegister F79;
    public static final IA64FloatRegister F80;
    public static final IA64FloatRegister F81;
    public static final IA64FloatRegister F82;
    public static final IA64FloatRegister F83;
    public static final IA64FloatRegister F84;
    public static final IA64FloatRegister F85;
    public static final IA64FloatRegister F86;
    public static final IA64FloatRegister F87;
    public static final IA64FloatRegister F88;
    public static final IA64FloatRegister F89;
    public static final IA64FloatRegister F90;
    public static final IA64FloatRegister F91;
    public static final IA64FloatRegister F92;
    public static final IA64FloatRegister F93;
    public static final IA64FloatRegister F94;
    public static final IA64FloatRegister F95;
    public static final IA64FloatRegister F96;
    public static final IA64FloatRegister F97;
    public static final IA64FloatRegister F98;
    public static final IA64FloatRegister F99;
    public static final IA64FloatRegister F100;
    public static final IA64FloatRegister F101;
    public static final IA64FloatRegister F102;
    public static final IA64FloatRegister F103;
    public static final IA64FloatRegister F104;
    public static final IA64FloatRegister F105;
    public static final IA64FloatRegister F106;
    public static final IA64FloatRegister F107;
    public static final IA64FloatRegister F108;
    public static final IA64FloatRegister F109;
    public static final IA64FloatRegister F110;
    public static final IA64FloatRegister F111;
    public static final IA64FloatRegister F112;
    public static final IA64FloatRegister F113;
    public static final IA64FloatRegister F114;
    public static final IA64FloatRegister F115;
    public static final IA64FloatRegister F116;
    public static final IA64FloatRegister F117;
    public static final IA64FloatRegister F118;
    public static final IA64FloatRegister F119;
    public static final IA64FloatRegister F120;
    public static final IA64FloatRegister F121;
    public static final IA64FloatRegister F122;
    public static final IA64FloatRegister F123;
    public static final IA64FloatRegister F124;
    public static final IA64FloatRegister F125;
    public static final IA64FloatRegister F126;
    public static final IA64FloatRegister F127;
    public static final int NUM_REGISTERS = 128;
    private static final IA64FloatRegister registers[];

    static {
     F0 = new IA64FloatRegister(0);
     F1 = new IA64FloatRegister(1);
     F2 = new IA64FloatRegister(2);
     F3 = new IA64FloatRegister(3);
     F4 = new IA64FloatRegister(4);
     F5 = new IA64FloatRegister(5);
     F6 = new IA64FloatRegister(6);
     F7 = new IA64FloatRegister(7);
     F8 = new IA64FloatRegister(8);
     F9 = new IA64FloatRegister(9);
     F10 = new IA64FloatRegister(10);
     F11 = new IA64FloatRegister(11);
     F12 = new IA64FloatRegister(12);
     F13 = new IA64FloatRegister(13);
     F14 = new IA64FloatRegister(14);
     F15 = new IA64FloatRegister(15);
     F16 = new IA64FloatRegister(16);
     F17 = new IA64FloatRegister(17);
     F18 = new IA64FloatRegister(18);
     F19 = new IA64FloatRegister(19);
     F20 = new IA64FloatRegister(20);
     F21 = new IA64FloatRegister(21);
     F22 = new IA64FloatRegister(22);
     F23 = new IA64FloatRegister(23);
     F24 = new IA64FloatRegister(24);
     F25 = new IA64FloatRegister(25);
     F26 = new IA64FloatRegister(26);
     F27 = new IA64FloatRegister(27);
     F28 = new IA64FloatRegister(28);
     F29 = new IA64FloatRegister(29);
     F30 = new IA64FloatRegister(30);
     F31 = new IA64FloatRegister(31);
     F32 = new IA64FloatRegister(32);
     F33 = new IA64FloatRegister(33);
     F34 = new IA64FloatRegister(34);
     F35 = new IA64FloatRegister(35);
     F36 = new IA64FloatRegister(36);
     F37 = new IA64FloatRegister(37);
     F38 = new IA64FloatRegister(38);
     F39 = new IA64FloatRegister(39);
     F40 = new IA64FloatRegister(40);
     F41 = new IA64FloatRegister(41);
     F42 = new IA64FloatRegister(42);
     F43 = new IA64FloatRegister(43);
     F44 = new IA64FloatRegister(44);
     F45 = new IA64FloatRegister(45);
     F46 = new IA64FloatRegister(46);
     F47 = new IA64FloatRegister(47);
     F48 = new IA64FloatRegister(48);
     F49 = new IA64FloatRegister(49);
     F50 = new IA64FloatRegister(50);
     F51 = new IA64FloatRegister(51);
     F52 = new IA64FloatRegister(52);
     F53 = new IA64FloatRegister(53);
     F54 = new IA64FloatRegister(54);
     F55 = new IA64FloatRegister(55);
     F56 = new IA64FloatRegister(56);
     F57 = new IA64FloatRegister(57);
     F58 = new IA64FloatRegister(58);
     F59 = new IA64FloatRegister(59);
     F60 = new IA64FloatRegister(60);
     F61 = new IA64FloatRegister(61);
     F62 = new IA64FloatRegister(62);
     F63 = new IA64FloatRegister(63);
     F64 = new IA64FloatRegister(64);
     F65 = new IA64FloatRegister(65);
     F66 = new IA64FloatRegister(66);
     F67 = new IA64FloatRegister(67);
     F68 = new IA64FloatRegister(68);
     F69 = new IA64FloatRegister(69);
     F70 = new IA64FloatRegister(70);
     F71 = new IA64FloatRegister(71);
     F72 = new IA64FloatRegister(72);
     F73 = new IA64FloatRegister(73);
     F74 = new IA64FloatRegister(74);
     F75 = new IA64FloatRegister(75);
     F76 = new IA64FloatRegister(76);
     F77 = new IA64FloatRegister(77);
     F78 = new IA64FloatRegister(78);
     F79 = new IA64FloatRegister(79);
     F80 = new IA64FloatRegister(80);
     F81 = new IA64FloatRegister(81);
     F82 = new IA64FloatRegister(82);
     F83 = new IA64FloatRegister(83);
     F84 = new IA64FloatRegister(84);
     F85 = new IA64FloatRegister(85);
     F86 = new IA64FloatRegister(86);
     F87 = new IA64FloatRegister(87);
     F88 = new IA64FloatRegister(88);
     F89 = new IA64FloatRegister(89);
     F90 = new IA64FloatRegister(90);
     F91 = new IA64FloatRegister(91);
     F92 = new IA64FloatRegister(92);
     F93 = new IA64FloatRegister(93);
     F94 = new IA64FloatRegister(94);
     F95 = new IA64FloatRegister(95);
     F96 = new IA64FloatRegister(96);
     F97 = new IA64FloatRegister(97);
     F98 = new IA64FloatRegister(98);
     F99 = new IA64FloatRegister(99);
     F100 = new IA64FloatRegister(100);
     F101 = new IA64FloatRegister(101);
     F102 = new IA64FloatRegister(102);
     F103 = new IA64FloatRegister(103);
     F104 = new IA64FloatRegister(104);
     F105 = new IA64FloatRegister(105);
     F106 = new IA64FloatRegister(106);
     F107 = new IA64FloatRegister(107);
     F108 = new IA64FloatRegister(108);
     F109 = new IA64FloatRegister(109);
     F110 = new IA64FloatRegister(110);
     F111 = new IA64FloatRegister(111);
     F112 = new IA64FloatRegister(112);
     F113 = new IA64FloatRegister(113);
     F114 = new IA64FloatRegister(114);
     F115 = new IA64FloatRegister(115);
     F116 = new IA64FloatRegister(116);
     F117 = new IA64FloatRegister(117);
     F118 = new IA64FloatRegister(118);
     F119 = new IA64FloatRegister(119);
     F120 = new IA64FloatRegister(120);
     F121 = new IA64FloatRegister(121);
     F122 = new IA64FloatRegister(122);
     F123 = new IA64FloatRegister(123);
     F124 = new IA64FloatRegister(124);
     F125 = new IA64FloatRegister(125);
     F126 = new IA64FloatRegister(126);
     F127 = new IA64FloatRegister(127);

        registers = (new IA64FloatRegister[] {
            F0, F1, F2, F3, F4, F5, F6, F7, F8, F9,
            F10, F11, F12, F13, F14, F15, F16, F17, F18, F19,
            F20, F21, F22, F23, F24, F25, F26, F27, F28, F29,
            F30, F31, F32, F33, F34, F35, F36, F37, F38, F39,
            F40, F41, F42, F43, F44, F45, F46, F47, F48, F49,
            F50, F51, F52, F53, F54, F55, F56, F57, F58, F59,
            F60, F61, F62, F63, F64, F65, F66, F67, F68, F69,
            F70, F71, F72, F73, F74, F75, F76, F77, F78, F79,
            F80, F81, F82, F83, F84, F85, F86, F87, F88, F89,
            F90, F91, F92, F93, F94, F95, F96, F97, F98, F99,
            F100, F101, F102, F103, F104, F105, F106, F107, F108, F109,
            F110, F111, F112, F113, F114, F115, F116, F117, F118, F119,
            F120, F121, F122, F123, F124, F125, F126, F127
        });
    }
}
