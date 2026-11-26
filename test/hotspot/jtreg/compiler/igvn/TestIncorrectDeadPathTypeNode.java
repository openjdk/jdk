/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8376587
 * @summary Fatal "dead path discovered by TypeNode during igvn" after C2 compilation
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=24476278 ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.igvn;

public class TestIncorrectDeadPathTypeNode {
    boolean ltIndirect(int p0, int p1) {
        return lt(p0, p1);
    }
    static boolean lt(int p0, int p1) { return p0 < p1; }
    boolean ltInstance(int p0, int p1) { return p0 < p1; }

    boolean _mutatorToggle;
    boolean _mutatorFlip() { return _mutatorToggle; }

    static class MyInteger {
        int v;
        MyInteger(int v) {
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 8; j++)
                    switch (i) {
                    case -2, -3:
                    case 0:
                        this.v = v;
                    }
            int limit = 2;
            for (; limit < 4; limit *= 2) {}
            for (int peel = 2; peel < limit; peel++) {}
        }
    }

    void test() {
        Byte x = 1;
        boolean flag = _mutatorFlip();
        int limit = 2;
        for (; limit < 4; limit *= 2) {}
        int zero = 4;
        for (int peel = 2; peel < limit; peel++) {
            zero = 0;
        }
        for (int i = 0; i < 10000; i++) {
            if (flag) {}
            if (zero == 0) {
                if ((i & 1) == 0) {
                    for (int j = 0; j < 32; j++) {
                        if (j < 10) {
                            x = (byte)x;
                        }
                    }
                    for (int j = 0; j < 2; j++) {
                        if (ltIndirect(j, 10)) {
                            x = (byte)x;
                        }
                    }
                    int Nj = 32;
                    for (int j = 0; j < Nj; j++) {
                        if (j < 10) {
                            Object o = "";
                            for (int k = 0; k < 32; k++) {
                                o.toString();
                            }
                            Integer.valueOf(1).toString();
                            for (int k = 0; k < 32; k++) {
                                if (k < 10) {
                                    x = (byte)x;
                                }
                            }
                            x = (byte)x;
                        }
                    }
                    for (int j = 0; j < 4; j++) {
                        for (int k = 0; k < 8; k++) {
                            switch (j) {
                            case -2, -3:
                            case 0:
                                x = (byte)x;
                            }
                        }
                    }
                    for (int j = 0; j < 4; j++) {
                        for (int k = 0; lt(new MyInteger(k).v, 8); k++) {
                            switch (j) {
                            case -2, -3:
                            case 0:
                                x = (byte)x;
                            }
                        }
                    }
                    for (int j = 0; ltInstance(j, 4); j++) {
                        for (int k = 0; k < 8; k++) {
                            switch (j) {
                            case -2, -3:
                            case 0:
                                x = (byte)x;
                            }
                        }
                    }
                    x = (byte)x;
                }
            }
        }
    }

    public static void main(String args[]) {
        TestIncorrectDeadPathTypeNode t = new TestIncorrectDeadPathTypeNode();
        t.test();
    }
}

