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

package compiler.valhalla.inlinetypes;

/*
 * @test
 * @bug 8367623
 * @summary When an InlineType has a larval oop and its <init> method is
 *          not inlined, it prevents the Allocate node of the larval oop
 *          to be eliminated. This ends up causing the report of a missed
 *          optimization (InlineTypeNode::Ideal) after macro expansion.
 *          That optimization should not be carried out, which is what this
 *          test ensures.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,*TestMissingOptUseBaseOop::test*
 *      -XX:VerifyIterativeGVN=1110
 *      compiler.valhalla.inlinetypes.TestMissingOptUseBaseOop
 * @run main compiler.valhalla.inlinetypes.TestMissingOptUseBaseOop
 */

public class TestMissingOptUseBaseOop {
    static class MyClass {}

    static value class MyValue {
        MyClass obj;

        public MyValue(MyClass obj) {
            this.obj = obj;
        }
    }

    static MyValue field;

    public static void test() {
        field = new MyValue(null);
    }

    public static void main(String[] args) {
        // We want to force inlining failure for MyValue::<init>,
        // so we need 'new MyValue(...)' but not 'new MyClass()'
        new MyValue(null);
        test();
    }
}

