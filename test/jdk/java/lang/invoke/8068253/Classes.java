/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;

abstract class A {
    int x1;
    static int x2;
    static final int x3 = 0;
    static void m1() {}
    void m3() {}
    abstract void m4();
    private void m7() {}
    void m8() {}
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
abstract class B extends A {
    int x1;
    static int x2;
    static final int x3 = 0;
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
    void m8() {}
}
class C extends B {
    void m4() {}
    private void m7() {}
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}

interface I {
    int x4 = 0;
    static void m2() {}
    void m5();
    default void m6() {}
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
interface J {
    int x4 = 0;
    void m5();
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
interface K extends I {
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}

abstract class G implements I, J {
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
class H implements J, I {
    public void m5() {}
    static MethodHandles.Lookup lookup() { return MethodHandles.lookup(); }
}
