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

/*
 * @test id=c1
 * @bug 8391181
 * @summary Tests that memory state is killed appropriately for dynamic constants.
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @compile TestMemoryEffectsCondy.jasm
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}Generated::test
 *                   ${test.main.class}
 */

/*
 * @test id=c2
 * @bug 8391181
 * @summary Tests that memory state is killed appropriately for dynamic constants.
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @compile TestMemoryEffectsCondy.jasm
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}Generated::test
 *                   ${test.main.class}
 */

package compiler.c1;

import java.lang.invoke.MethodHandles;

import jdk.test.lib.Asserts;

import compiler.c1.TestMemoryEffectsCondyGenerated;

public class TestMemoryEffectsCondy {
    public static class Victim {
        public int field;
    }
    public static final Victim VICTIM = new Victim();

    public static Object bootstrap(MethodHandles.Lookup lookup, String name, Class<?> type) {
        VICTIM.field = 42; // side-effect
        return Integer.valueOf(0xcafe);
    }

    public static final void main(String[] args) throws ReflectiveOperationException {
        int result = TestMemoryEffectsCondyGenerated.test();
        Asserts.assertEQ(42, result);
    }
}
