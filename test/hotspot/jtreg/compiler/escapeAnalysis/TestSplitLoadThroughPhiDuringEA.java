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
package compiler.escapeAnalysis;

import java.util.Objects;

/*
 * @test
 * @bug 8374435
 * @summary assert during escape analysis when splitting a Load through a Phi because the input of
 *          the result Phi is a Load not from an AddP
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-UseCompressedOops ${test.main.class}
 */
public class TestSplitLoadThroughPhiDuringEA {
    static class Holder {
        Object o;
    }

    public static void main(String[] args) {
        Object o = new Object();
        Holder h = new Holder();
        for (int i = 0; i < 20000; i++) {
            test(true, h, o);
            test(false, h, o);
        }
    }

    private static Object test(boolean b, Holder h, Object o) {
        h = Objects.requireNonNull(h);
        if (b) {
            h = new Holder();
            // This access has the pattern LoadP -> LoadP, which upsets the compiler because the
            // pointer input of a LoadP is not an AddP
            h.o = o.getClass();
        }
        return h.o;
    }
}
