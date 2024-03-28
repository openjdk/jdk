/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.internal.vm.ConstantSupport;

/*
 * @test
 * @bug 8324433
 * @summary Test that isCompileConstant is able to constant-fold the computation
 *          regarding constant inputs.
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /
 * @run driver compiler.c2.irTests.IsCompileConstantTests
 */
public class IsCompileConstantTests {
    private record Point(int x, int y) {}

    private static final boolean BOOL_CONSTANT = true;
    private static final byte BYTE_CONSTANT = 3;
    private static final short SHORT_CONSTANT = 3;
    private static final char CHAR_CONSTANT = 3;
    private static final int INT_CONSTANT = 3;
    private static final long LONG_CONSTANT = 3;
    private static final float FLOAT_CONSTANT = 3;
    private static final double DOUBLE_CONSTANT = 3;
    private static final Point POINT_CONSTANT = new Point(1, 2);

    private static final int[] LOOKUP_TABLE;
    static {
        LOOKUP_TABLE = new int[4];
        LOOKUP_TABLE[0] = 125;
        LOOKUP_TABLE[1] = 341;
        LOOKUP_TABLE[2] = 97;
        LOOKUP_TABLE[3] = 460;
    }

    private boolean boolVariable = true;
    private byte byteVariable = 3;
    private short shortVariable = 3;
    private char charVariable = 3;
    private int intVariable = 3;
    private long longVariable = 3;
    private float floatVariable = 3;
    private double doubleVariable = 3;
    private Point pointVariable = new Point(1, 2);
    private int hashCache = pointVariable.hashCode();

    @Test
    @IR(failOn = IRNode.LOAD)
    public int boolConstant() {
        return process(BOOL_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int boolVariable() {
        return process(boolVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int byteConstant() {
        return process(BYTE_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int byteVariable() {
        return process(byteVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int shortConstant() {
        return process(SHORT_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int shortVariable() {
        return process(shortVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int charConstant() {
        return process(CHAR_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int charVariable() {
        return process(charVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int intConstant() {
        return process(INT_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int intVariable() {
        return process(intVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int longConstant() {
        return process(LONG_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int longVariable() {
        return process(longVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int floatConstant() {
        return process(FLOAT_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int floatVariable() {
        return process(floatVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int doubleConstant() {
        return process(DOUBLE_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"})
    public int doubleVariable() {
        return process(doubleVariable);
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int objectConstant() {
        return process(POINT_CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    public int objectVariable() {
        return process(pointVariable);
    }

    @ForceInline
    public int process(boolean input) {
        if (ConstantSupport.isCompileConstant(input)) {
            if (input) {
                return 125;
            } else {
                return 341;
            }
        }

        return LOOKUP_TABLE[input ? 1 : 0];
    }

    @ForceInline
    public int process(byte input) {
        if (ConstantSupport.isCompileConstant(input)) {
            return switch(input) {
                case 0 -> 125;
                case 1 -> 341;
                case 2 -> 97;
                case 3 -> 460;
                default -> throw new AssertionError();
            };
        }

        return LOOKUP_TABLE[input];
    }

    @ForceInline
    public int process(short input) {
        if (ConstantSupport.isCompileConstant(input)) {
            return switch(input) {
                case 0 -> 125;
                case 1 -> 341;
                case 2 -> 97;
                case 3 -> 460;
                default -> throw new AssertionError();
            };
        }

        return LOOKUP_TABLE[input];
    }

    @ForceInline
    public int process(char input) {
        if (ConstantSupport.isCompileConstant(input)) {
            return switch(input) {
                case 0 -> 125;
                case 1 -> 341;
                case 2 -> 97;
                case 3 -> 460;
                default -> throw new AssertionError();
            };
        }

        return LOOKUP_TABLE[input];
    }

    @ForceInline
    public int process(int input) {
        if (ConstantSupport.isCompileConstant(input)) {
            return switch(input) {
                case 0 -> 125;
                case 1 -> 341;
                case 2 -> 97;
                case 3 -> 460;
                default -> throw new AssertionError();
            };
        }

        return LOOKUP_TABLE[input];
    }

    @ForceInline
    public int process(long input) {
        if (ConstantSupport.isCompileConstant(input)) {
            if (input == 0) {
                return 125;
            } else if (input == 1) {
                return 341;
            } else if (input == 2) {
                return 97;
            } else if (input == 3) {
                return 460;
            } else {
                throw new AssertionError();
            }
        }

        return LOOKUP_TABLE[(int)input];
    }

    @ForceInline
    public int process(float input) {
        if (ConstantSupport.isCompileConstant(input)) {
            if (input == 0) {
                return 125;
            } else if (input == 1) {
                return 341;
            } else if (input == 2) {
                return 97;
            } else if (input == 3) {
                return 460;
            } else {
                throw new AssertionError();
            }
        }

        return LOOKUP_TABLE[(int)input];
    }

    @ForceInline
    public int process(double input) {
        if (ConstantSupport.isCompileConstant(input)) {
            if (input == 0) {
                return 125;
            } else if (input == 1) {
                return 341;
            } else if (input == 2) {
                return 97;
            } else if (input == 3) {
                return 460;
            } else {
                throw new AssertionError();
            }
        }

        return LOOKUP_TABLE[(int)input];
    }

    @ForceInline
    public int process(Point input) {
        if (ConstantSupport.isCompileConstant(input)) {
            return input.hashCode();
        }

        return hashCache;
    }

    public static void main(String[] args) {
        var test = new TestFramework(IsCompileConstantTests.class);
        test.addFlags("--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED");
        test.start();
    }
}
