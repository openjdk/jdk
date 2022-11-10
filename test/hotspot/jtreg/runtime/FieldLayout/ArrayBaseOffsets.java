/*
 * Copyright (C2 2022, Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test id=no-ccp
 * @library /test/lib
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:-UseCompressedClassPointers ArrayBaseOffsets
 */
/*
 * @test id=with-ccp
 * @library /test/lib
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:+UseCompressedClassPointers ArrayBaseOffsets
 */
/*
 * @test id=32bit
 * @library /test/lib
 * @requires vm.bits == "32"
 * @modules java.base/jdk.internal.misc
 * @run main/othervm ArrayBaseOffsets
 */

import jdk.internal.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class ArrayBaseOffsets {

    static public void main(String[] args) {
        Unsafe unsafe = Unsafe.getUnsafe();
        int intOffset, longOffset;
        if (Platform.is64bit()) {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            List<String> vmargs = runtime.getInputArguments();
            if (vmargs.contains("-XX:-UseCompressedClassPointers")) {
                intOffset = 20;
                longOffset = 24;
            } else {
                intOffset = 16;
                longOffset = 16;
            }
        } else {
            intOffset = 12;
            longOffset = 16;
        }
        Asserts.assertEquals(unsafe.arrayBaseOffset(boolean[].class), intOffset,  "Misplaced boolean array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(byte[].class),    intOffset,  "Misplaced byte    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(char[].class),    intOffset,  "Misplaced char    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(short[].class),   intOffset,  "Misplaced short   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(int[].class),     intOffset,  "Misplaced int     array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(long[].class),    longOffset, "Misplaced long    array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(float[].class),   intOffset,  "Misplaced float   array base");
        Asserts.assertEquals(unsafe.arrayBaseOffset(double[].class),  longOffset, "Misplaced double  array base");
        boolean narrowOops = System.getProperty("java.vm.compressedOopsMode") != null ||
                             !Platform.is64bit();
        int expected_objary_offset = narrowOops ? intOffset : longOffset;
        Asserts.assertEquals(unsafe.arrayBaseOffset(Object[].class),  expected_objary_offset, "Misplaced object  array base");
    }
}
