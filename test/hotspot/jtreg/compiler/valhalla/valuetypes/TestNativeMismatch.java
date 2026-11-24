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

package compiler.valhalla.valuetypes;
/*
 * @test
 * @bug 8393507
 * @summary wrong calling convention used at SharedRuntime::handle_wrong_method()
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,${test.main.class}*::doIt ${test.main.class}
 */

public class TestNativeMismatch {
    static value class MyValue {
        int x;

        public MyValue(int x) {
            this.x = x;
        }
    }

    interface MyInterface {
        // Scalarized
        MyInterface doIt(MyValue v1, MyValue v2);
    }

    static class ClassWithNative implements MyInterface {
        // Non-scalarized because method is native
        public native MyInterface doIt(MyValue v1, MyValue v2);
    }

    static class MismatchingClass extends ClassWithNative {
        // Has scalarized and non-scalarized supers
        public MyInterface doIt(MyValue v1, MyValue v2) {
            return this;
        }
    }

    static class OtherClass implements MyInterface {
        // Covariant return of OtherClass will trigger creation of a bridge method
        // public MyInterface doIt(MyValue v1, MyValue v2) {
        //   return doIt(v1, v2);
        // }
        public OtherClass doIt(MyValue v1, MyValue v2) {
            return this;
        }
    }

    static void test(MyInterface intf, MyValue MyValue) {
        intf.doIt(MyValue, MyValue);
    }

    public static void main(String[] args) {
        MyValue val = new MyValue(0);
        test(new MismatchingClass(), val);
        OtherClass other = new OtherClass();
        for (int i = 0; i < 50_000; i++) {
            test(other, val);
        }
    }
}
