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
 * @bug 8394804
 * @summary Test case where IGVN deep revisit is required for ValueType
 * @library /test/lib
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

public class TestValueTypeDeepRevisit {
    value record MyValue(Double d, int i) { }

    static MyValue source = new MyValue(1.0, 1);

    static void test(int limit) {
        // ValueTypeNode::Ideal tries to see if there is already a heap representation
        // for the new source via is_loaded. For this it needs to traverse deeply,
        // through nested ValueType, object references, casts, DecodeN, to eventually
        // find if the field load are at the desired offset and all from the same object.
        source = new MyValue(source.d(), source.i());

        // Empty loop, seems to make the missing optimization recognizable.
        while (limit-- > 0) { }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(2);
        }
    }
}
