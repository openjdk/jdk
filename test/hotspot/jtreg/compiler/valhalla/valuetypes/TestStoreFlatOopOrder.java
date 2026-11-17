/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8394771
 * @summary Do not assume the order of fields in StoreFlat expansion
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,${test.main.class}::main
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import jdk.internal.vm.annotation.NullRestricted;

public class TestStoreFlatOopOrder {
    value record A(Object o) {}
    value record B(Object o, @NullRestricted A a) {}

    static B[] array = new B[1];

    public static void main(String[] args) {
        array[0] = array[0];
    }
}

