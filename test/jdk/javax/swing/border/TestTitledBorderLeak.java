/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.border.TitledBorder;

/*
 * @test
 * @bug 8204963
 * @summary Verifies TitledBorder's memory leak
 * @run main/othervm -Xmx10M TestTitledBorderLeak
 */
public class TestTitledBorderLeak {
    public static void main(String[] args) throws Exception {
        int max = 100000;
        long initialFreeMemory = 0L;
        long currentFreeMemory;
        try {
            for (int i = 1; i <= max; i++) {
                new TitledBorder("");
                if ((i % 1000) == 0) {
                    System.gc();
                    currentFreeMemory = dumpMemoryStatus("After " + i);
                    if(initialFreeMemory == 0L) {
                        initialFreeMemory = currentFreeMemory;
                    } else if( currentFreeMemory < initialFreeMemory/2) {
                        throw new RuntimeException("Memory halved: there's a leak");
                    }

                }
            }
        }catch(OutOfMemoryError e) {
            // Don't think it would work; should not happen
            System.gc();
            throw new RuntimeException("There was OOM");
        }
        System.out.println("Passed");
    }
    private static long dumpMemoryStatus(String msg) {
        Runtime rt = Runtime.getRuntime();
        long freeMem = rt.freeMemory();
        System.out.println(msg + ": " + freeMem + " free");
        return freeMem;
    }
}

