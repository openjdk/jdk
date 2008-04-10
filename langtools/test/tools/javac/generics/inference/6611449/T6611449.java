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

/**
 * @test
 * @bug 6611449
 * @summary Internal Error thrown during generic method/constructor invocation
 * @compile/fail/ref=T6611449.out -XDstdout -XDrawDiagnostics T6611449.java
 */
public class T6611449<S> {

    T6611449() {this(1);}

    <T extends S> T6611449(T t1) {this(t1, 1);}

    <T extends S> T6611449(T t1, T t2) {}

    <T extends S> void m(T t1) {}

    <T extends S> void m(T t1, T t2) {}

    void test() {
        m1(1);
        m2(1, 1);
    }
}
