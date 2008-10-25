/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6711619
 *
 * @summary javac doesn't allow access to protected members in intersection types
 * @author Maurizio Cimadamore
 *
 * @compile/fail/ref=T6711619a.out -XDrawDiagnostics T6711619a.java
 */
class T6711619a {

    static class A {
        private void a() {}
        private A a;
    }
    static class B extends A {
        private B b() {}
        private B b;
    }
    static interface I{
        void i();
    }
    static interface I1{
        void i1();
    }
    static class E extends B implements I, I1{
        public void i() {}
        public void i1() {}
    }
    static class C<W extends B & I1, T extends W>{
        T t;
        W w;
        C(W w, T t) {
            this.w = w;
            this.t = t;
        }
    }

    static void testMemberMethods(C<? extends A, ? extends I> arg) {
        arg.t.a();
        arg.t.b();
    }

    static void testMemberFields(C<? extends A, ? extends I> arg) {
        A ta; B tb;
        ta = arg.t.a;
        tb = arg.t.b;
        ta = arg.w.a;
        tb = arg.w.b;
    }
}