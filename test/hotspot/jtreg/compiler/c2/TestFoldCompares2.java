/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8286104
 * @summary Test Fold-compares are safe when C2 optimizes unstable_if traps
 *          (-XX:+OptimizeUnstableIf)
 *
 * @run main/othervm -XX:CompileCommand=compileOnly,java.lang.Short::valueOf
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestFoldCompares2$Numbers::isSupported
 *                   -Xbatch compiler.c2.TestFoldCompares2
 */

package compiler.c2;

public class TestFoldCompares2 {
    public static Short value = Short.valueOf((short) 0);
    static void testShort() {
        // trigger compilation and bias to a cached value.
        for (int i=0; i<20_000; ++i) {
            value = Short.valueOf((short) 0);
        }

        // trigger deoptimization on purpose
        // the size of ShortCache.cache is hard-coded in java.lang.Short
        Short x = Short.valueOf((short) 128);
        if (x != 128) {
            throw new RuntimeException("wrong result!");
        }
    }

    static enum Numbers {
        One,
        Two,
        Three,
        Four,
        Five;

        boolean isSupported() {
            // ordinal() is inlined and leaves a copy region node, which blocks
            // fold-compares in the 1st iterGVN.
            return ordinal() >= Two.ordinal() && ordinal() <= Four.ordinal();
        }
    }

    static void testEnumValues() {
        Numbers local = Numbers.Two;

        for (int i = 0; i < 2_000_000; ++i) {
            local.isSupported();
        }
        // deoptimize
        Numbers.Five.isSupported();
    }

    public static void main(String[] args) {
        testShort();
        testEnumValues();
        System.out.println("Test passed.");
    }
}
