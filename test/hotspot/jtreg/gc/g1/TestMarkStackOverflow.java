/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

import java.util.LinkedHashMap;

/* @test
 * @bug 8313212
 * @summary Finalizing objects may create new concurrent marking work during reference processing.
 * If the marking work overflows the global mark stack, we should resize the global mark stack
 * until MarkStackSizeMax if possible.
 * @requires vm.gc.G1
 * @run main/othervm -XX:ActiveProcessorCount=2 -XX:MarkStackSize=1 -Xmx250m gc.g1.TestMarkStackOverflow
 */

public class TestMarkStackOverflow {
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10; i++) {
            Finalizable holder1 = new Finalizable();
            System.out.printf("Used mem %.2f MB\n", getUsedMem());
        }
    }

    private static double getUsedMem() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (double) (1024 * 1024);
    }

    private static class Finalizable {
        public static final int NUM_OBJECTS = 200_000;
        private final LinkedHashMap<Object, Object> list = new LinkedHashMap<>();

        public Finalizable() {
            for (int i = 0; i < NUM_OBJECTS; i++) {
                Object entry = new Object();
                list.put(entry, entry);
            }
        }

        @SuppressWarnings("removal")
        protected void finalize() {
            System.out.print("");
        }
    }
}
