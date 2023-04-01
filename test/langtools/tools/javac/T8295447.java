/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295447
 * @summary NullPointerException with invalid pattern matching construct in constructor call
 * @modules jdk.compiler
 * @compile/fail/ref=T8295447.out -XDrawDiagnostics --enable-preview -source ${jdk.version} T8295447.java
 */
public class T8295447 {
    class Foo {
        void m(Object o) {
            if(o instanceof Foo(int x)) {}
        }

        Foo(Object o) {
            m((o instanceof Foo(int x))? 0 : 1);
        }
        void m(int i) { }
    }

    class Base { int i; Base(int j) { i = j; } }
    class Sub extends Base {
        Sub(Object o) { super(o instanceof java.awt.Point(int x, int y)? x + y: 0); }
    }
}
