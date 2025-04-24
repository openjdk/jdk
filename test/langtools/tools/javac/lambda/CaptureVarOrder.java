/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8336492
 * @summary Regression in lambda serialization
 */

public class CaptureVarOrder {
    static Object m(String s, int i, Object o) {
        return new Object() {
            final byte B = 0;
            void g() { System.out.println(s + i + B + o); }
        };
    }

    static Runnable r(String s, int i, Object o) {
        final byte B = 0;
        return () -> System.out.println(s + i + B + o);
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        CaptureVarOrder.class.getDeclaredMethod("lambda$r$0", String.class, int.class, Object.class);
        m("", 1, null).getClass().getDeclaredConstructor(String.class, int.class, Object.class);
    }
}
