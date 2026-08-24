/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8380166
 * @summary C2: crash in compiled code due to zero division because of widened CastII
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -Xcomp -XX:CompileOnly=TestDeadPathManyDeadDataNodes::test1
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=inline,TestDeadPathManyDeadDataNodes::inlined1
 *                   -XX:MaxRecursiveInlineLevel=1000 -XX:MaxInlineLevel=1000
 *                   -XX:-TieredCompilation -XX:+AlwaysIncrementalInline
 *                   -XX:+DelayAfterInliningCutoff -XX:+IncrementalInlineForceCleanup
 *                   -XX:NodeCountInliningCutoff=100000 -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.c2;

public class TestDeadPathManyDeadDataNodes {
    private static int field;
    private static boolean boolField2;
    private static int arrayLengthField;

    public static void main(String[] args) {
        Object o = new Object();
        try {
            test1(false, 0);
        } catch (NegativeArraySizeException nase) {
        }
    }

    private static int test1(boolean boolParam, int intParam) {
        int length;
        int res = 0;
        length = -1;
        for (int i = 0; i < 2; i++) {
            if (boolParam) {
                field = 42;
            }
            int[] array = new int[length];
            arrayLengthField = array.length;
            while(true) {
                Object o = new Object();
                int arrayLength = arrayLengthField;
                arrayLengthField = 0;
                switch (intParam) {
                    case 0:
                        if (boolField2) {
                            break;
                        }
                        field = 42;
                        continue;
                    case 1:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 2:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 3:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 4:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 5:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 6:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 7:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 8:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 9:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 10:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 11:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 12:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 13:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 14:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 15:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 16:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 17:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 18:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 19:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 20:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 21:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 22:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 23:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 24:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 25:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 26:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 27:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 28:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 29:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 30:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 31:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 32:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 33:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 34:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 35:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 36:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 37:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 38:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 39:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 40:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 41:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 42:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 43:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 44:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 45:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 46:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 47:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 48:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 49:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 50:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 51:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 52:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 53:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 54:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 55:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 56:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 57:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 58:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 59:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 60:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 61:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 62:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 63:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 64:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 65:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 66:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 67:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 68:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 69:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 70:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 71:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 72:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 73:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 74:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 75:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 76:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 77:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 78:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 79:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 80:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 81:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 82:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 83:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 84:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 85:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 86:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 87:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 88:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 89:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 90:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 91:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 92:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 93:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 94:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 95:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 96:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 97:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    case 98:
                        if (boolParam) {
                            res += arrayLength * 1;
                        }
                        field = 42;
                        continue;
                    case 99:
                        if (boolParam) {
                            res += arrayLength * 2;
                        }
                        field = 42;
                        continue;
                    default:
                        res += inlined1(boolParam, intParam/100, arrayLength, 92);
                        continue;
                }
                field = 42;
                break;
            }
            length = lastInlined();
        }
        return res;
    }

    static int lastInlined() {
        return -1;
    }

    static int inlined1(boolean boolParam, int intParam,  int arrayLength, int count) {
        int res = 0;
        switch (intParam) {
            case 0:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 1:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 2:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 3:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 4:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 5:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 6:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 7:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 8:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 9:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 10:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 11:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 12:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 13:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 14:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 15:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 16:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 17:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 18:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 19:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 20:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 21:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 22:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 23:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 24:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 25:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 26:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 27:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 28:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 29:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 30:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 31:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 32:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 33:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 34:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 35:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 36:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 37:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 38:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 39:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 40:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 41:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 42:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 43:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 44:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 45:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 46:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 47:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 48:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 49:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 50:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 51:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 52:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 53:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 54:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 55:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 56:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 57:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 58:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 59:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 60:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 61:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 62:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 63:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 64:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 65:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 66:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 67:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 68:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 69:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 70:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 71:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 72:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 73:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 74:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 75:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 76:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 77:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 78:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 79:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 80:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 81:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 82:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 83:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 84:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 85:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 86:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 87:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 88:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 89:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 90:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 91:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 92:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 93:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 94:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 95:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 96:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 97:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            case 98:
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
            case 99:
                if (boolParam) {
                    res += arrayLength * 2;
                }
                field = 42;
                return res;
            default:
                if (count == 0) {
                if (boolParam) {
                    res += arrayLength * 1;
                }
                field = 42;
                return res;
                } else {
                    return inlined1(boolParam, intParam / 100, arrayLength, count-1);
                }
        }
    }
}
