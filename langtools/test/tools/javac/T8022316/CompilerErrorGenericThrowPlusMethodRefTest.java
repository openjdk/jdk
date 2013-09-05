/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8022316
 * @summary Generic throws, overriding and method reference
 * @compile/fail/ref=CompilerErrorGenericThrowPlusMethodRefTest.out -XDrawDiagnostics CompilerErrorGenericThrowPlusMethodRefTest.java
 */

@SuppressWarnings("unchecked")
public class CompilerErrorGenericThrowPlusMethodRefTest {
    interface SAM11 {
        public <E extends Throwable> void foo() throws E ;
    }

    interface SAM12 extends SAM11{
        @Override
        public void foo() throws Throwable;
    }

    public void boo() throws RuntimeException {}

    static void test1() {
        try {
            SAM12 s2 = new CompilerErrorGenericThrowPlusMethodRefTest()::boo;
            s2.foo();
        } catch(Throwable ex) {}
    }

    static void test2() {
        SAM11 s1 = null;
        s1.<Exception>foo();
        s1.<RuntimeException>foo();
    }

    interface SAM21 {
        <E extends Exception> void m(E arg) throws E;
    }

    interface SAM22 {
        <F extends Exception> void m(F arg) throws F;
    }

    interface SAM23 extends SAM21, SAM22 {}

    public <E extends Exception> void bar(E e) throws E {}

    static <E extends Exception> void test3(E e) {
        try {
            SAM23 s2 = new CompilerErrorGenericThrowPlusMethodRefTest()::bar;
            s2.m(e);
        } catch(Exception ex) {}
    }

}
