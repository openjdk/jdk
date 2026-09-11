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

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @key randomness
 * @summary For InlineType nodes, we want to set the base oop if fields are loaded from memory.
 *          This test ensures that this optimization is not missed when we create an InlineType
 *          node in parsing with InlineTypeNode::buffer.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:+AlwaysIncrementalInline -XX:VerifyIterativeGVN=1110
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingOptUseBaseOopParsing {
    @NullRestricted
    static final MyValueEmpty empty = new MyValueEmpty();

    public static void main(String[] args) {
        // avoid unloaded
        Object[] array = (MyValueEmpty[])ValueClass.newNullRestrictedNonAtomicArray(MyValueEmpty.class, 2, empty);
        Asserts.assertEquals(array[0], empty);

        test();
    }

    public static Object helper(Object[] array) {
        array[0] = new MyValueEmpty();
        return array[1];
    }

    public static void test() {
        Object[] array = (MyValueEmpty[])ValueClass.newNullRestrictedNonAtomicArray(MyValueEmpty.class, 2, empty);
        Object res = helper(array);
        Asserts.assertEquals(array[0], empty);
    }
}
