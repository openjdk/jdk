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

package compiler.gcbarriers;

import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Setup;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Utils;

import java.util.Random;

/*
 * @test
 * @bug 8329797
 * @key randomness
 * @summary Test that MinL/MaxL nodes are removed when GC barriers in loop
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestMinMaxLongLoopBarrier {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    public class Dummy {
        long l;
        public Dummy(long l) { this.l = l; }
    }

    @Setup
    Object[] setupDummyArray() {
        Dummy[] arr = new Dummy[512];
        for (int i = 0; i < 512; i++) {
            arr[i] = new Dummy(RANDOM.nextLong());
        }
        return new Object[] { arr };
    }

    @Test
    @Arguments(setup = "setupDummyArray")
    @IR(failOn = { IRNode.MAX_L })
    public long testMaxLAndBarrierInLoop(Dummy[] arr) {
        long result = 0;
        for (int i = 0; i < arr.length; ++i) {
            result += Math.max(arr[i].l, 1);
        }
        return result;
    }

    @Test
    @Arguments(setup = "setupDummyArray")
    @IR(failOn = { IRNode.MIN_L })
    public long testMinLAndBarrierInLoop(Dummy[] arr) {
        long result = 0;
        for (int i = 0; i < arr.length; ++i) {
            result += Math.min(arr[i].l, 1);
        }
        return result;
    }
}
