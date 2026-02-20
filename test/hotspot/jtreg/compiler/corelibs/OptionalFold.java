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

package compiler.corelibs;

import java.util.Optional;

import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/*
 * @test
 * @bug 8372696
 * @summary Verify constant folding for Optional, both present and absent
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class OptionalFold {

    public static void main(String[] args) {
        // Somehow fails with -XX:-TieredCompilation
        TestFramework.runWithFlags("-XX:+TieredCompilation");
    }

    // Ensure both present and empty values can fold
    static final Optional<Integer> ONE = Optional.of(5), TWO = Optional.empty();

    @Test
    @IR(failOn = {IRNode.ADD_I})
    public int testSum() {
        return ONE.orElse(7) + TWO.orElse(12);
    }

    @Check(test = "testSum")
    public void checkTestSum(int res) {
        if (res != 5 + 12) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

}
