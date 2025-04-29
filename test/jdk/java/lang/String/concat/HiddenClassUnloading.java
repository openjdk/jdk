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

import jdk.test.whitebox.WhiteBox;

/**
 * @test
 * @summary Test whether the hidden class unloading of StringConcatFactory works
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @requires vm.flagless
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xverify:all HiddenClassUnloading
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xverify:all -XX:-CompactStrings HiddenClassUnloading
 */
public class HiddenClassUnloading {
    public static void main(String[] args) throws Throwable {
        var lookup = MethodHandles.lookup();
        var types  = new Class<?>[] {
                int.class, long.class, double.class, float.class, char.class, boolean.class, String.class,
        };

        long initUnloadedClassCount = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();

        for (int i = 0; i < 2000; i++) {
            int radix = types.length;
            String str = Integer.toString(i, radix);
            int length = str.length();
            var ptypes = new Class[length];
            for (int j = 0; j < length; j++) {
                int index = Integer.parseInt(str.substring(j, j + 1), radix);
                ptypes[j] = types[index];
            }
            StringConcatFactory.makeConcatWithConstants(
                    lookup,
                    "concat",
                    MethodType.methodType(String.class, ptypes),
                    "\1".repeat(length), // recipe
                    new Object[0]
            );
        }

        // Request GC which performs class unloading
        WhiteBox.getWhiteBox().fullGC();

        long unloadedClassCount = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
        if (initUnloadedClassCount == unloadedClassCount) {
            throw new RuntimeException("unloadedClassCount is zero");
        }
    }
}
