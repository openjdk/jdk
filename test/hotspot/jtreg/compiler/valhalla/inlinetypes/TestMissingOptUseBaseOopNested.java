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

import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @key randomness
 * @summary For InlineType nodes, we want to set the base oop if fields are loaded from memory.
 *          This test ensures that this optimization is not missed when we have nested
 *          inline types and InlineTypeNode::is_loaded suddenly returns something for one of
 *          the nested types after another optimization.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+AlwaysIncrementalInline -XX:VerifyIterativeGVN=1110
 *                   ${test.main.class}
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+AlwaysIncrementalInline -XX:VerifyIterativeGVN=1110
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+AlwaysIncrementalInline -XX:VerifyIterativeGVN=1110
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   -XX:StressSeed=335817958
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingOptUseBaseOopNested {
    public TestMissingOptUseBaseOopNested() {
        valueField1 = testValue1;
        super();
    }

    public static void main(String[] args) {
        TestMissingOptUseBaseOopNested t = new TestMissingOptUseBaseOopNested();
        for (int i = 0; i < 10000; i++) {
            t.test();
        }
    }

    @NullRestricted
    private static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);

    MyValue1 nullField;

    @NullRestricted
    MyValue1 valueField1;

    MyValue1 getNullField1() {
        return nullField;
    }

    MyValue1 getNullField2() {
        return null;
    }

    public void test() {
        nullField = getNullField1(); // should not throw
        try {
            valueField1 = getNullField1();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
        try {
            valueField1 = getNullField2();
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException e) {
            // Expected
        }
    }

}
