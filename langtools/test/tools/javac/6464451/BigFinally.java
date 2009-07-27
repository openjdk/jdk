/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug     6464451
 * @summary javac in 5.0ux can not compile try-finally block which has a lot of "return"
 * @author  Wei Tao
 * @compile -source 5 -target 5 BigFinally.java
 * @clean BigFinally
 * @compile/fail BigFinally.java
 */

public class BigFinally {
    static public int func(int i) {
        try {
            if(i == 1) return 1;
        } finally {
            try {
                if(i == 2) return 2;
                if(i == 3 ) return 3;
                if(i == 4 ) return 4;
                if(i == 5 ) return 5;
                if(i == 6 ) return 6;
                if(i == 7 ) return 7;
                if(i == 8 ) return 8;
                if(i == 9 ) return 9;
                if(i == 10 ) return 10;
                if(i == 11 ) return 11;
                if(i == 12 ) return 12;
                if(i == 13 ) return 13;
                if(i == 14 ) return 14;
                if(i == 15 ) return 15;
                if(i == 16 ) return 16;
                if(i == 17 ) return 17;
                if(i == 18 ) return 18;
                if(i == 19 ) return 19;
                if(i == 20 ) return 20;
                if(i == 21 ) return 21;
                if(i == 22 ) return 22;
                if(i == 23 ) return 23;
                if(i == 24 ) return 24;
                if(i == 25 ) return 25;
                if(i == 26 ) return 26;
                if(i == 27 ) return 27;
                if(i == 28 ) return 28;
                if(i == 29 ) return 29;
                if(i == 30 ) return 30;
                if(i == 31 ) return 31;
                if(i == 32 ) return 32;
                if(i == 33 ) return 33;
                if(i == 34 ) return 34;
                if(i == 35 ) return 35;
                if(i == 36 ) return 36;
                if(i == 37 ) return 37;
                if(i == 38 ) return 38;
                if(i == 39 ) return 39;
                if(i == 40 ) return 40;
                if(i == 41 ) return 41;
                if(i == 42 ) return 42;
                if(i == 43 ) return 43;
                if(i == 44 ) return 44;
                if(i == 45 ) return 45;
                if(i == 46 ) return 46;
                if(i == 47 ) return 47;
                if(i == 48 ) return 48;
                if(i == 49 ) return 49;
                if(i == 50 ) return 50;
                if(i == 51 ) return 51;
                if(i == 52 ) return 52;
                if(i == 53 ) return 53;
                if(i == 54 ) return 54;
                if(i == 55 ) return 55;
                if(i == 56 ) return 56;
                if(i == 57 ) return 57;
                if(i == 58 ) return 58;
                if(i == 59 ) return 59;
                if(i == 60 ) return 60;
                if(i == 61 ) return 61;
                if(i == 62 ) return 62;
                if(i == 63 ) return 63;
                if(i == 64 ) return 64;
                if(i == 65 ) return 65;
                if(i == 66 ) return 66;
                if(i == 67 ) return 67;
                if(i == 68 ) return 68;
                if(i == 69 ) return 69;
                if(i == 70 ) return 70;
                if(i == 71 ) return 71;
                if(i == 72 ) return 72;
                if(i == 73 ) return 73;
                if(i == 74 ) return 74;
                if(i == 75 ) return 75;
                if(i == 76 ) return 76;
                if(i == 77 ) return 77;
                if(i == 78 ) return 78;
                if(i == 79 ) return 79;
                if(i == 80 ) return 80;
                if(i == 81 ) return 81;
                if(i == 82 ) return 82;
                if(i == 83 ) return 83;
                if(i == 84 ) return 84;
                if(i == 85 ) return 85;
                if(i == 86 ) return 86;
                if(i == 87 ) return 87;
                if(i == 88 ) return 88;
                if(i == 89 ) return 89;
                if(i == 90 ) return 90;
                if(i == 91 ) return 91;
                if(i == 92 ) return 92;
                if(i == 93 ) return 93;
                if(i == 94 ) return 94;
                if(i == 95 ) return 95;
                if(i == 96 ) return 96;
                if(i == 97 ) return 97;
                if(i == 98 ) return 98;
                if(i == 99 ) return 99;
                if(i == 100 ) return 100;
            } finally {
                int x = 0;
                x += 1;
                x += 2;
                x += 3;
                x += 4;
                x += 5;
                x += 6;
                x += 7;
                x += 8;
                x += 9;
                x += 10;
                x += 11;
                x += 12;
                x += 13;
                x += 14;
                x += 15;
                x += 16;
                x += 17;
                x += 18;
                x += 19;
                x += 20;
                x += 21;
                x += 22;
                x += 23;
                x += 24;
                x += 25;
                x += 26;
                x += 27;
                x += 28;
                x += 29;
                x += 30;
                x += 31;
                x += 32;
                x += 33;
                x += 34;
                x += 35;
                x += 36;
                x += 37;
                x += 38;
                x += 39;
                x += 40;
                x += 41;
                x += 42;
                x += 43;
                x += 44;
                x += 45;
                x += 46;
                x += 47;
                x += 48;
                x += 49;
                x += 50;
                x += 51;
                x += 52;
                x += 53;
                x += 54;
                x += 55;
                x += 56;
                x += 57;
                x += 58;
                x += 59;
                x += 60;
                x += 61;
                x += 62;
                x += 63;
                x += 64;
                x += 65;
                x += 66;
                x += 67;
                x += 68;
                x += 69;
                x += 70;
                x += 71;
                x += 72;
                x += 73;
                x += 74;
                x += 75;
                x += 76;
                x += 77;
                x += 78;
                x += 79;
                x += 80;
                x += 81;
                x += 82;
                x += 83;
                x += 84;
                x += 85;
                x += 86;
                x += 87;
                x += 88;
                x += 89;
                x += 90;
                x += 91;
                x += 92;
                x += 93;
                x += 94;
                x += 95;
                x += 96;
                x += 97;
                x += 98;
                x += 99;
                x += 100;
            }
        }
        return 0;
    }
}
