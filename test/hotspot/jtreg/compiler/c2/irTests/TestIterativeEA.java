/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8276455
 * @summary Test C2 iterative Escape Analysis to remove all allocations in test
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIterativeEA
 */
public class TestIterativeEA {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static class MyClass {
        int val;
        public MyClass(int val) {
            this.val = val;
        }
    }

    static class AbstractClass {
        final int unused;
        public AbstractClass() {
            unused = 42;
        }
    }

    static class HolderWithSuper extends AbstractClass {
        final MyClass obj;
        public HolderWithSuper(MyClass obj) {
            this.obj = obj;
        }
    }

    static class Holder {
        final MyClass obj;
        public Holder(MyClass obj) {
            this.obj = obj;
        }
    }

    static class GenericHolder {
        final Object obj;
        public GenericHolder(Object obj) {
            this.obj = obj;
        }
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    public static int testSlow(int val) {
        MyClass obj = new MyClass(val);
        HolderWithSuper h1 = new HolderWithSuper(obj);
        GenericHolder h2 = new GenericHolder(h1);
        return ((HolderWithSuper)h2.obj).obj.val;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    public static int testFast(int val) {
        MyClass obj = new MyClass(val);
        Holder h1 = new Holder(obj);
        GenericHolder h2 = new GenericHolder(h1);
        return ((Holder)h2.obj).obj.val;
    }

    static class A {
        int i;
        public A(int i) {
            this.i = i;
        }
    }

    static class B {
        A a;
        public B(A a) {
            this.a = a;
        }
    }

    static class C {
        B b;
        public C(B b) {
            this.b = b;
        }
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    static int testNested(int i) {
        C c = new C(new B(new A(i)));
        return c.b.a.i;
    }
}
