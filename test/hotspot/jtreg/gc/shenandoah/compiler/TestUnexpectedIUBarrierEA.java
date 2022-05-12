/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * bug 8280885
 * @summary Shenandoah: Some tests failed with "EA: missing allocation reference path"
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:-BackgroundCompilation -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:ShenandoahGCMode=iu
 *                   -XX:CompileCommand=dontinline,TestUnexpectedIUBarrierEA::notInlined TestUnexpectedIUBarrierEA
 */

public class TestUnexpectedIUBarrierEA {

    private static Object field;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(false);
        }
    }

    private static void test(boolean flag) {
        A a = new A();
        B b = new B();
        b.field = a;
        notInlined();
        Object o = b.field;
        if (!(o instanceof A)) {

        }
        C c = new C();
        c.field = o;
        if (flag) {
            field = c.field;
        }
    }

    private static void notInlined() {

    }

    private static class A {
    }

    private static class B {
        public Object field;
    }

    private static class C {
        public Object field;
    }
}
