/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6723444
 *
 * @summary javac fails to substitute type variables into a constructor's throws clause
 * @author Mark Mahieu
 * @compile/fail/ref=T6723444.out -XDstdout -XDrawDiagnostics T6723444.java
 *
 */
public class T6723444 {

    static class Foo<X extends Throwable> {
        Foo() throws X {}
    }

    <X extends Throwable> T6723444()
        throws X {}

    <X extends Throwable> T6723444(Foo<X> foo)
        throws X {}

    <X1 extends Throwable, X2 extends Throwable> T6723444(Foo<X1> foo, int i)
        throws X1, X2 {}

    public static void main(String[] args) throws Exception {

        // the following 8 statements should compile without error

        Foo<Exception> exFoo = new Foo<Exception>();
        exFoo = new Foo<Exception>() {};

        new<Exception> T6723444();
        new<Exception> T6723444() {};
        new T6723444(exFoo);
        new T6723444(exFoo) {};
        new<Exception, Exception> T6723444(exFoo, 1);
        new<Exception, Exception> T6723444(exFoo, 1) {};

        // the remaining statements should all raise an
        // unreported exception error

        new T6723444(exFoo, 1);
        new T6723444(exFoo, 1) {};

        Foo<Throwable> thFoo = new Foo<Throwable>();
        thFoo = new Foo<Throwable>() {};

        new<Throwable> T6723444();
        new<Throwable> T6723444() {};
        new T6723444(thFoo);
        new T6723444(thFoo) {};
        new T6723444(thFoo, 1);
        new T6723444(thFoo, 1) {};
        new<Throwable, Throwable> T6723444(thFoo, 1);
        new<Throwable, Throwable> T6723444(thFoo, 1) {};
    }
}
