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

package compiler.vectorapi;

import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8375010
 * @summary C2 crashes when expanding VectorBox with Proj input from vector math call
 * @modules jdk.incubator.vector
 * @library /test/lib
 *
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.vectorapi.VectorBoxExpandProj::test compiler.vectorapi.VectorBoxExpandProj
 */
public class VectorBoxExpandProj {
    static boolean b;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            b = !b;
            test();
        }
        System.out.println("PASS");
    }

    // TAN returns Proj (not VectorNode). Phi merging creates VectorBox(Phi, Proj).
    // expand_vbox_node_helper must handle Proj inputs with vector type.
    static Object test() {
        var t = DoubleVector.broadcast(DoubleVector.SPECIES_128, 1.0).lanewise(VectorOperators.TAN);
        return b ? t.convertShape(VectorOperators.Conversion.ofCast(double.class, double.class), DoubleVector.SPECIES_128, 0) : t;
    }
}
