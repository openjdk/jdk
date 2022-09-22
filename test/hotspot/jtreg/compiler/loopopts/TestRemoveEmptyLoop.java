/*
 * Copyright (c) 2019, Huawei Technologies Co., Ltd. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8231988 8293996
 * @summary Unexpected test result caused by C2 IdealLoopTree::do_remove_empty_loop
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation
 *      compiler.loopopts.TestRemoveEmptyLoop
 */

package compiler.loopopts;

public class TestRemoveEmptyLoop {

    public void test_cmp_helper() {
        int i = 34;
        // The empty loop that collapses
        for (; i > 0; i -= 11);
        // If uses same Cmp node as the loop condition
        if (i < 0) {
            // do nothing
        } else {
            throw new RuntimeException("Test failed.");
        }
    }

    public void test_cmp() {
        // Loop is OSR compiled, and test_cmp_helper inlined
        for (int i = 0; i < 50000; i++) {
            test_cmp_helper();
        }
    }

    void test_collapse_helper() {
        int o = 11;
        int e = 43542;
        for (int i = 524; i < 19325; i += 1) {
            // The empty loop that is supposed to collapse
            for (int j = 0; j < 32767; j++) {
                o++;
            }
            for (int k = 0; k < o; k++) {
                e++;
            }
        }
    }

    public void test_collapse() {
        // Loop is OSR compiled, and test_collapse_helper inlined
        for (int i = 0; i < 50000; i++) {
            test_collapse_helper();
        }
    }

    public static void main(String[] args) {
        TestRemoveEmptyLoop _instance = new TestRemoveEmptyLoop();
        _instance.test_cmp();
        _instance.test_collapse();
        System.out.println("Test passed.");
    }
}
