/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test ir
 * @bug 8324751
 * @summary Visual example of auto vectorization: normal mapping
 * @library /test/lib /
 * @run main compiler.galery.TestNormalMapping ir
 */
// TODO: fix bug id above

/*
 * @test visual
 * @library /test/lib /
 * @run main compiler.galery.TestNormalMapping visual
 */

package compiler.galery;

import jdk.test.lib.Utils;

/**
 * TODO: desc: JTREG version, with IR tests
 * TODO: link to stand-alone
 */
public class TestNormalMapping {
    public static void main(String[] args) throws InterruptedException {
        String mode = args[0];
        System.out.println("Running JTREG test in mode: " + mode);

        switch(mode) {
            case "ir" -> testIR();
            case "visual" -> testVisual();
            default -> throw new RuntimeException("Unknown mode: " + mode);
        }
    }

    private static void testIR() {
        System.out.println("Testing with IR rules...");
    }

    private static void testVisual() throws InterruptedException {
        System.out.println("Testing with 2d Graphics (visual)...");

        // We will not do anything special here, just launch the application,
        // tell it to run for 5 seconds, and then timeout after 10 seconds.
        Thread thread = new Thread() {
            public void run() {
                NormalMapping.main(new String[] {"10"});
            }
        };
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(15000));
    }
}
