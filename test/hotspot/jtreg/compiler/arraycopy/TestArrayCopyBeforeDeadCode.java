/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8395950
 * @requires vm.compiler2.enabled
 * @summary C2: generate_slow_arraycopy segfault when clone
 *          CallProjections.fallthrough_memproj is null
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.arraycopy.TestArrayCopyBeforeDeadCode::test
 *                   compiler.arraycopy.TestArrayCopyBeforeDeadCode
 */

package compiler.arraycopy;

import java.util.ArrayList;
import java.util.Arrays;

public class TestArrayCopyBeforeDeadCode {
    ArrayList al = new ArrayList(Arrays.asList());

    void f() {}

    void helper(Integer unusedButNeeded) {
        if (763 > Short.valueOf((short)5) + Character.lowSurrogate(1073741813)) {
            f();
        }
        f(); f(); f();
    }

    public static void main(String[] args) {
        // The bug only reproduces if the Integer.valueOf() call for the boxing
        // in test() is inlined. With -Xcomp, C2 does not inline a call whose
        // constant pool entry is unresolved. main() runs in the interpreter and
        // shares that entry with test(), so resolve it here before test() is
        // compiled.
        Integer.valueOf(0);

        for (int i = 0; i < 20_000; i++) {
            new TestArrayCopyBeforeDeadCode().test();
        }
    }
    void test() {
        float fi = 0.0F;
        // Delay constant folding of fi until after loop opts.
        for (; fi < 2; fi++) { }

        for (int i = 0; i < 4; i++) {
            new TestArrayCopyBeforeDeadCode().helper(fi < 2 ? 264 : -376);
        }
    }
}
