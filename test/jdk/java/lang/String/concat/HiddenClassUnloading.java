/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.StringBuilder;

import java.lang.invoke.*;
import java.lang.management.ManagementFactory;

/**
 * @test
 * @summary Test whether the hidden class unloading of StringConcatFactory works
 *
 * @requires vm.flagless
 * @run main/othervm -Xmx8M -Xms8M -Xverify:all HiddenClassUnloading
 * @run main/othervm -Xmx8M -Xms8M -Xverify:all -XX:-CompactStrings HiddenClassUnloading
 */
public class HiddenClassUnloading {
    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Class<?>[] types = new Class[] {
                int.class, long.class, double.class, float.class, char.class, boolean.class, String.class,
        };
        Object[] values = new Object[] {
                1, 1L, 1D, 1F, 'C', true, "A",
        };

        for (int i = 0; i < 10_000; i++) {
            int radix = types.length;
            String str = Integer.toString(i, radix);
            int length = str.length();
            String recipe = "\1".repeat(length);
            Class<?>[] ptypes = new Class[length];
            Object[] pvalues = new Object[length];
            for (int j = 0; j < length; j++) {
                int index = Integer.parseInt(str.substring(j, j + 1), radix);
                ptypes[j] = types[index];
                pvalues[j] = values[index];
            }
            MethodType concatType = MethodType.methodType(String.class, ptypes);
            CallSite callSite = StringConcatFactory.makeConcatWithConstants(
                    lookup,
                    "concat",
                    concatType,
                    recipe,
                    new Object[0]
            );
            MethodHandle mh = callSite.dynamicInvoker();
            String result = switch (length) {
                case 1  -> (String) mh.invoke(pvalues[0]);
                case 2  -> (String) mh.invoke(pvalues[0], pvalues[1]);
                case 3  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2]);
                case 4  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3]);
                case 5  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3], pvalues[4]);
                case 6  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3], pvalues[4], pvalues[5]);
                case 7  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3], pvalues[4], pvalues[5], pvalues[6]);
                case 8  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3], pvalues[4], pvalues[5], pvalues[6], pvalues[7]);
                case 9  -> (String) mh.invoke(pvalues[0], pvalues[1], pvalues[2], pvalues[3], pvalues[4], pvalues[5], pvalues[6], pvalues[7], pvalues[8]);
                default -> throw new RuntimeException("length too large " + length);
            };

            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < pvalues.length; j++) {
                sb.append(pvalues[j]);
            }
            assertEquals(sb.toString(), result);
        }

        long unloadedClassCount = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
        if (unloadedClassCount == 0) {
            throw new RuntimeException("unloadedClassCount is zero");
        }
    }

    static void assertEquals(String expected, String actual) {
       if (!expected.equals(actual)) {
           StringBuilder sb = new StringBuilder();
           sb.append("Expected = ");
           sb.append(expected);
           sb.append(", actual = ");
           sb.append(actual);
           throw new IllegalStateException(sb.toString());
       }
    }
}
