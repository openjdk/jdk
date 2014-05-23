/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestDynMinHeapFreeRatio
 * @bug 8028391
 * @summary Verify that MinHeapFreeRatio flag is manageable
 * @library /testlibrary
 * @run main TestDynMinHeapFreeRatio
 * @run main/othervm -XX:MinHeapFreeRatio=0 -XX:MaxHeapFreeRatio=100 TestDynMinHeapFreeRatio
 * @run main/othervm -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=50 -XX:-UseAdaptiveSizePolicy TestDynMinHeapFreeRatio
 * @run main/othervm -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=50 TestDynMinHeapFreeRatio
 * @run main/othervm -XX:MinHeapFreeRatio=51 -XX:MaxHeapFreeRatio=52 TestDynMinHeapFreeRatio
 * @run main/othervm -XX:MinHeapFreeRatio=75 -XX:MaxHeapFreeRatio=100 TestDynMinHeapFreeRatio
 */
import static com.oracle.java.testlibrary.Asserts.assertEQ;
import static com.oracle.java.testlibrary.Asserts.assertFalse;
import static com.oracle.java.testlibrary.Asserts.assertTrue;
import com.oracle.java.testlibrary.DynamicVMOption;

public class TestDynMinHeapFreeRatio {

    public static void main(String args[]) throws Exception {

        // high boundary value
        int maxValue = DynamicVMOption.getInt("MaxHeapFreeRatio");
        System.out.println("MaxHeapFreeRatio= " + maxValue);

        String badValues[] = {
            null,
            "",
            "not a number",
            "8.5", "-0.01",
            Integer.toString(Integer.MIN_VALUE),
            Integer.toString(Integer.MAX_VALUE),
            Integer.toString(maxValue + 1),
            "-1024", "-1", "101", "1997"
        };

        String goodValues[] = {
            Integer.toString(maxValue),
            Integer.toString(maxValue - 1),
            Integer.toString(maxValue / 2),
            "0", "1"
        };

        // option under test
        DynamicVMOption option = new DynamicVMOption("MinHeapFreeRatio");

        assertTrue(option.isWriteable(), "Option " + option.name
                + " is expected to be writable");

        for (String v : badValues) {
            assertFalse(option.isValidValue(v),
                    "'" + v + "' is expected to be illegal for flag " + option.name);
        }

        for (String v : goodValues) {
            option.setValue(v);
            String newValue = option.getValue();
            assertEQ(v, newValue);
        }
    }
}
