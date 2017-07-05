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

import sun.jvm.hotspot.utilities.*;

public class IA64Registers {

    public static final IA64Register GR0;
    public static final IA64Register GR1;
    public static final IA64Register GR2;
    public static final IA64Register GR3;
    public static final IA64Register GR4;
    public static final IA64Register GR5;
    public static final IA64Register GR6;
    public static final IA64Register GR7;
    public static final IA64Register GR8;
    public static final IA64Register GR9;
    public static final IA64Register GR10;
    public static final IA64Register GR11;
    public static final IA64Register GR12;
    public static final IA64Register GR13;
    public static final IA64Register GR14;
    public static final IA64Register GR15;
    public static final IA64Register GR16;
    public static final IA64Register GR17;
    public static final IA64Register GR18;
    public static final IA64Register GR19;
    public static final IA64Register GR20;
    public static final IA64Register GR21;
    public static final IA64Register GR22;
    public static final IA64Register GR23;
    public static final IA64Register GR24;
    public static final IA64Register GR25;
    public static final IA64Register GR26;
    public static final IA64Register GR27;
    public static final IA64Register GR28;
    public static final IA64Register GR29;
    public static final IA64Register GR30;
    public static final IA64Register GR31;
    public static final IA64Register GR32;
    public static final IA64Register GR33;
    public static final IA64Register GR34;
    public static final IA64Register GR35;
    public static final IA64Register GR36;
    public static final IA64Register GR37;
    public static final IA64Register GR38;
    public static final IA64Register GR39;
    public static final IA64Register GR40;
    public static final IA64Register GR41;
    public static final IA64Register GR42;
    public static final IA64Register GR43;
    public static final IA64Register GR44;
    public static final IA64Register GR45;
    public static final IA64Register GR46;
    public static final IA64Register GR47;
    public static final IA64Register GR48;
    public static final IA64Register GR49;
    public static final IA64Register GR50;
    public static final IA64Register GR51;
    public static final IA64Register GR52;
    public static final IA64Register GR53;
    public static final IA64Register GR54;
    public static final IA64Register GR55;
    public static final IA64Register GR56;
    public static final IA64Register GR57;
    public static final IA64Register GR58;
    public static final IA64Register GR59;
    public static final IA64Register GR60;
    public static final IA64Register GR61;
    public static final IA64Register GR62;
    public static final IA64Register GR63;
    public static final IA64Register GR64;
    public static final IA64Register GR65;
    public static final IA64Register GR66;
    public static final IA64Register GR67;
    public static final IA64Register GR68;
    public static final IA64Register GR69;
    public static final IA64Register GR70;
    public static final IA64Register GR71;
    public static final IA64Register GR72;
    public static final IA64Register GR73;
    public static final IA64Register GR74;
    public static final IA64Register GR75;
    public static final IA64Register GR76;
    public static final IA64Register GR77;
    public static final IA64Register GR78;
    public static final IA64Register GR79;
    public static final IA64Register GR80;
    public static final IA64Register GR81;
    public static final IA64Register GR82;
    public static final IA64Register GR83;
    public static final IA64Register GR84;
    public static final IA64Register GR85;
    public static final IA64Register GR86;
    public static final IA64Register GR87;
    public static final IA64Register GR88;
    public static final IA64Register GR89;
    public static final IA64Register GR90;
    public static final IA64Register GR91;
    public static final IA64Register GR92;
    public static final IA64Register GR93;
    public static final IA64Register GR94;
    public static final IA64Register GR95;
    public static final IA64Register GR96;
    public static final IA64Register GR97;
    public static final IA64Register GR98;
    public static final IA64Register GR99;
    public static final IA64Register GR100;
    public static final IA64Register GR101;
    public static final IA64Register GR102;
    public static final IA64Register GR103;
    public static final IA64Register GR104;
    public static final IA64Register GR105;
    public static final IA64Register GR106;
    public static final IA64Register GR107;
    public static final IA64Register GR108;
    public static final IA64Register GR109;
    public static final IA64Register GR110;
    public static final IA64Register GR111;
    public static final IA64Register GR112;
    public static final IA64Register GR113;
    public static final IA64Register GR114;
    public static final IA64Register GR115;
    public static final IA64Register GR116;
    public static final IA64Register GR117;
    public static final IA64Register GR118;
    public static final IA64Register GR119;
    public static final IA64Register GR120;
    public static final IA64Register GR121;
    public static final IA64Register GR122;
    public static final IA64Register GR123;
    public static final IA64Register GR124;
    public static final IA64Register GR125;
    public static final IA64Register GR126;
    public static final IA64Register GR127;

    public static final IA64Register AR_BSP;

    public static final int NUM_REGISTERS = 129;
    private static final IA64Register registers[];

    static {
     GR0 = new IA64Register(0);
     GR1 = new IA64Register(1);
     GR2 = new IA64Register(2);
     GR3 = new IA64Register(3);
     GR4 = new IA64Register(4);
     GR5 = new IA64Register(5);
     GR6 = new IA64Register(6);
     GR7 = new IA64Register(7);
     GR8 = new IA64Register(8);
     GR9 = new IA64Register(9);
     GR10 = new IA64Register(10);
     GR11 = new IA64Register(11);
     GR12 = new IA64Register(12);
     GR13 = new IA64Register(13);
     GR14 = new IA64Register(14);
     GR15 = new IA64Register(15);
     GR16 = new IA64Register(16);
     GR17 = new IA64Register(17);
     GR18 = new IA64Register(18);
     GR19 = new IA64Register(19);
     GR20 = new IA64Register(20);
     GR21 = new IA64Register(21);
     GR22 = new IA64Register(22);
     GR23 = new IA64Register(23);
     GR24 = new IA64Register(24);
     GR25 = new IA64Register(25);
     GR26 = new IA64Register(26);
     GR27 = new IA64Register(27);
     GR28 = new IA64Register(28);
     GR29 = new IA64Register(29);
     GR30 = new IA64Register(30);
     GR31 = new IA64Register(31);
     GR32 = new IA64Register(32);
     GR33 = new IA64Register(33);
     GR34 = new IA64Register(34);
     GR35 = new IA64Register(35);
     GR36 = new IA64Register(36);
     GR37 = new IA64Register(37);
     GR38 = new IA64Register(38);
     GR39 = new IA64Register(39);
     GR40 = new IA64Register(40);
     GR41 = new IA64Register(41);
     GR42 = new IA64Register(42);
     GR43 = new IA64Register(43);
     GR44 = new IA64Register(44);
     GR45 = new IA64Register(45);
     GR46 = new IA64Register(46);
     GR47 = new IA64Register(47);
     GR48 = new IA64Register(48);
     GR49 = new IA64Register(49);
     GR50 = new IA64Register(50);
     GR51 = new IA64Register(51);
     GR52 = new IA64Register(52);
     GR53 = new IA64Register(53);
     GR54 = new IA64Register(54);
     GR55 = new IA64Register(55);
     GR56 = new IA64Register(56);
     GR57 = new IA64Register(57);
     GR58 = new IA64Register(58);
     GR59 = new IA64Register(59);
     GR60 = new IA64Register(60);
     GR61 = new IA64Register(61);
     GR62 = new IA64Register(62);
     GR63 = new IA64Register(63);
     GR64 = new IA64Register(64);
     GR65 = new IA64Register(65);
     GR66 = new IA64Register(66);
     GR67 = new IA64Register(67);
     GR68 = new IA64Register(68);
     GR69 = new IA64Register(69);
     GR70 = new IA64Register(70);
     GR71 = new IA64Register(71);
     GR72 = new IA64Register(72);
     GR73 = new IA64Register(73);
     GR74 = new IA64Register(74);
     GR75 = new IA64Register(75);
     GR76 = new IA64Register(76);
     GR77 = new IA64Register(77);
     GR78 = new IA64Register(78);
     GR79 = new IA64Register(79);
     GR80 = new IA64Register(80);
     GR81 = new IA64Register(81);
     GR82 = new IA64Register(82);
     GR83 = new IA64Register(83);
     GR84 = new IA64Register(84);
     GR85 = new IA64Register(85);
     GR86 = new IA64Register(86);
     GR87 = new IA64Register(87);
     GR88 = new IA64Register(88);
     GR89 = new IA64Register(89);
     GR90 = new IA64Register(90);
     GR91 = new IA64Register(91);
     GR92 = new IA64Register(92);
     GR93 = new IA64Register(93);
     GR94 = new IA64Register(94);
     GR95 = new IA64Register(95);
     GR96 = new IA64Register(96);
     GR97 = new IA64Register(97);
     GR98 = new IA64Register(98);
     GR99 = new IA64Register(99);
     GR100 = new IA64Register(100);
     GR101 = new IA64Register(101);
     GR102 = new IA64Register(102);
     GR103 = new IA64Register(103);
     GR104 = new IA64Register(104);
     GR105 = new IA64Register(105);
     GR106 = new IA64Register(106);
     GR107 = new IA64Register(107);
     GR108 = new IA64Register(108);
     GR109 = new IA64Register(109);
     GR110 = new IA64Register(110);
     GR111 = new IA64Register(111);
     GR112 = new IA64Register(112);
     GR113 = new IA64Register(113);
     GR114 = new IA64Register(114);
     GR115 = new IA64Register(115);
     GR116 = new IA64Register(116);
     GR117 = new IA64Register(117);
     GR118 = new IA64Register(118);
     GR119 = new IA64Register(119);
     GR120 = new IA64Register(120);
     GR121 = new IA64Register(121);
     GR122 = new IA64Register(122);
     GR123 = new IA64Register(123);
     GR124 = new IA64Register(124);
     GR125 = new IA64Register(125);
     GR126 = new IA64Register(126);
     GR127 = new IA64Register(127);

     AR_BSP = new IA64Register(128);

        registers = (new IA64Register[] {
            GR0, GR1, GR2, GR3, GR4, GR5, GR6, GR7, GR8, GR9,
            GR10, GR11, GR12, GR13, GR14, GR15, GR16, GR17, GR18, GR19,
            GR20, GR21, GR22, GR23, GR24, GR25, GR26, GR27, GR28, GR29,
            GR30, GR31, GR32, GR33, GR34, GR35, GR36, GR37, GR38, GR39,
            GR40, GR41, GR42, GR43, GR44, GR45, GR46, GR47, GR48, GR49,
            GR50, GR51, GR52, GR53, GR54, GR55, GR56, GR57, GR58, GR59,
            GR60, GR61, GR62, GR63, GR64, GR65, GR66, GR67, GR68, GR69,
            GR70, GR71, GR72, GR73, GR74, GR75, GR76, GR77, GR78, GR79,
            GR80, GR81, GR82, GR83, GR84, GR85, GR86, GR87, GR88, GR89,
            GR90, GR91, GR92, GR93, GR94, GR95, GR96, GR97, GR98, GR99,
            GR100, GR101, GR102, GR103, GR104, GR105, GR106, GR107, GR108, GR109,
            GR110, GR111, GR112, GR113, GR114, GR115, GR116, GR117, GR118, GR119,
            GR120, GR121, GR122, GR123, GR124, GR125, GR126, GR127, AR_BSP
        });

  }

  public static final IA64Register FP = AR_BSP;
  public static final IA64Register SP = GR12;


  /** Prefer to use this instead of the constant above */
  public static int getNumRegisters() {
    return NUM_REGISTERS;
  }


  public static String getRegisterName(int regNum) {
    if (regNum < 0 || regNum >= NUM_REGISTERS) {
      return "[Illegal register " + regNum + "]";
    }

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid integer register number!");
    }

    if (regNum == 128 ) {
      return "BSP";
    }

    if (regNum == 12) {
      return "SP";
    }

    return "R" + regNum;

  }

  public static IA64Register getRegister(int regNum) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(regNum > -1 && regNum < NUM_REGISTERS, "invalid register number!");
    }

    return registers[regNum];
  }
}
