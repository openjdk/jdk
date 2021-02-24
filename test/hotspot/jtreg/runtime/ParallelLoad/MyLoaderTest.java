/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test MyLoaderTest
 * @bug 8262046
 * @summary Call handle_parallel_super_load, loading parallel threads that throw CCE
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 * @compile -XDignore.symbol.file AsmClasses.java
 * @compile classfiles/ClassInLoader.java classfiles/A.java classfiles/B.java classfiles/C.java
 * @run main/othervm MyLoaderTest
 * @run main/othervm MyLoaderTest -waitForSuper
 * @run main/othervm MyLoaderTest -parallelCapable
 * @run main/othervm MyLoaderTest -waitForSuper -parallelCapable
 * @run main/othervm MyLoaderTest -okSuper
 * @run main/othervm MyLoaderTest -waitForSuper -okSuper
 * @run main/othervm MyLoaderTest -waitForSuper -parallelCapable -okSuper
 * @run main/othervm MyLoaderTest -concurrent
 * @run main/othervm MyLoaderTest -concurrent -parallelCapable
 */

public class MyLoaderTest {
    public static void main(java.lang.String[] args) throws Exception {
        boolean concurrent = false;
        boolean waitForSuper = false;
        boolean okSuper = false;
        boolean parallelCapable = false;
        boolean success = true;
        for (int i = 0; i < args.length; i++) {
            try {
                // Don't print debug info
                if (args[i].equals("-concurrent")) {
                    concurrent = true;
                } else if (args[i].equals("-okSuper")) {
                    okSuper = true;
                } else if (args[i].equals("-parallelCapable")) {
                    parallelCapable = true;
                } else if (args[i].equals("-waitForSuper")) {
                    waitForSuper = true;
                } else {
                    System.out.println("Unrecognized " + args[i]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid parameter: " + args[i - 1] + " " + args[i]);
            }
        }
        if (parallelCapable) {
            MyLoader ldr = new MyLoader(concurrent, waitForSuper, okSuper);
            ldr.startLoading();
            success = ldr.report_success();
        } else {
            MyNonParallelLoader ldr = new MyNonParallelLoader(concurrent, waitForSuper, okSuper);
            ldr.startLoading();
            success = ldr.report_success();
        }
        if (success) {
            System.out.println("PASSED");
        } else {
            throw new RuntimeException("FAILED");
        }
    }
}
