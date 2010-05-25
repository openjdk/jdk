/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6182630
 * @summary Method with parameter bound to raw type avoids unchecked warning
 * @author Peter von der Ah\u00e9
 * @compile -Xlint:unchecked T6182630.java
 * @compile/fail -Werror -Xlint:unchecked T6182630.java
 * @compile/fail -Werror -Xlint:unchecked T6182630a.java
 * @compile/fail -Werror -Xlint:unchecked T6182630b.java
 * @compile/fail -Werror -Xlint:unchecked T6182630c.java
 * @compile/fail -Werror -Xlint:unchecked T6182630d.java
 * @compile/fail -Werror -Xlint:unchecked T6182630e.java
 * @compile/fail -Werror -Xlint:unchecked T6182630f.java
 */

public class T6182630 {
    static class Foo<X> {
        public X x;
        public void m(X x) { }
    }
    interface Bar {}
    <T extends Foo, S extends Foo & Bar> void test1(T t, S s) {
        t.x = "BAD";
        t.m("BAD");
        t.m(t.x);
        s.x = "BAD";
        s.m("BAD");
        s.m(s.x);
    }
}
