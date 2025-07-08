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
 * @bug 8361214
 * @summary An anonymous class is erroneously being classify as an abstract class
 * @compile AnonymousLabeledAsAbstractTest.java
 */

class AnonymousLabeledAsAbstractTest {
    abstract class Base<T> {}
    abstract class Derived1<T> extends Base<T> {}
    abstract class Derived2<T> extends Base<T> {
        Derived2(Derived1<T> obj){}
    }
    abstract class Derived3<T> extends Base<T> {
        Derived3(Derived2<T> obj){}
    }

    Base<String> obj = new Derived2<>(new Derived1<>(){}){};
    Base<String> obj2 = new Derived3<String>(new Derived2<>(new Derived1<>(){}){}){};
    Base<String> obj3 = new Derived3<>(new Derived2<>(new Derived1<>(){}){}){};
}
