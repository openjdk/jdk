/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 0
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 1
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 2
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 3
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 4
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 5
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 6
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 7
 */

/*
 * @test
 * @bug 8315003
 * @summary Test that removing allocation merges of non-value and value object at EA is working properly.
 * @library /test/lib /
 * @enablePreview
 * @run main compiler.valhalla.inlinetypes.TestAllocationMergeAndFolding 8
 */
package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;

import java.util.Random;

public class TestAllocationMergeAndFolding {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework framework = InlineTypes.getFramework();
        int index = Integer.parseInt(args[0]);
        if (index < 7) {
            framework.addScenarios(InlineTypes.DEFAULT_SCENARIOS[index]);
        } else if (index == 7) {
            framework.addScenarios(new Scenario(7, "--enable-preview", "-XX:-UseCompressedOops"));
        } else {
            framework.addScenarios(new Scenario(8, "--enable-preview", "-XX:+UseCompressedOops"));
        }
        framework.start();
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    static int test(boolean flag) {
        Object o;
        if (flag) {
            o = new V(34);
        } else {
            o = new Object();
        }
        dontInline(); // Not inlined and thus we have a safepoint where keep phi(o) = [V, Object].

        // 'o' escapes as store to 'f'. However, 'f' does not escape and can be removed. As a result, we can also remove
        // the allocations in both branches with EA after JDK-8287061. Since V has an inline type field v2, we put it
        // on a list to scalarize it as well. The improved allocation merge was disabled in Valhalla but is now enabled
        // and fixed with JDK-8315003.
        Foo f = new Foo(o);
        return f.i;
    }

    @DontInline
    static void dontInline() {
    }

    @Run(test = "test")
    static void run() {
        test(RANDOM.nextBoolean());
    }

    static class Foo {
        Object o;
        int i;

        Foo(Object o) {
            this.o = o;
        }
    }

    static value class V {
        int i;
        V2 v2;

        V(int i) {
            this.i = i;
            this.v2 = new V2();
        }
    }

    static value class V2 {
    }

    // TODO 8376254: C1 bailouts if the type of the nullable flat field is uninitialized
    static final V2 LOAD_V2 = new V2();
}
