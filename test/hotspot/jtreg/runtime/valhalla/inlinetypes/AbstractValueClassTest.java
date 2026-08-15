/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293134
 * @summary Test that a super abstract value class is allowed.
 * @enablePreview
 * @run main runtime.valhalla.inlinetypes.AbstractValueClassTest
 */

package runtime.valhalla.inlinetypes;

public class AbstractValueClassTest {

    public static void main(String[] args) {
        A a = new A();
        a.doit(a);
    }
    public static abstract value class T {
        public abstract void doit(T t);
    }

    public static final value class A extends T {
        public void doit(T t) {
            System.out.println(t);
        }
    }
    public static final value class B extends T {
        public void doit(T t) {
            System.out.println(t);
        }
    }
}
