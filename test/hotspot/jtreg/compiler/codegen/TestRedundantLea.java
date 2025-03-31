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

/*
 * @test
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.codegen.TestRedundantLea::test*
 *      -XX:CompileCommand=PrintIdealPhase,compiler.codegen.TestRedundantLea::test*,FINAL_CODE
 *      -XX:CompileCommand=print,compiler.codegen.TestRedundantLea::test*
 *      compiler.codegen.TestRedundantLea
 */

package compiler.codegen;

import java.util.concurrent.atomic.*;

// TODO: convert to IR test
public class TestRedundantLea {
    public static void main(String[] args) {
        TestRedundantLea t = new TestRedundantLea();
        for (int i = 0; i < 100000; i++) {
            t.test1();
            t.test2("test");
        }
    }

    private static final Object CURRENT = new Object();

    private final AtomicReference<Object> obj = new AtomicReference<Object>();

    private final Foo foo = new Foo("bar");

    public void test1() {
        obj.getAndSet(CURRENT);
    }

    public boolean test2(String baz) {
        return foo.bar(baz);
    }
}

class Foo {
    private String bar;

    public Foo(String bar) {
        this.bar = bar;
    }

    public boolean bar(String baz) {
        return this.bar.equals(baz);
    }
}
