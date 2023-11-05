/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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
 * @bug     8273914
 * @summary Indy string concat changes order of operations
 *
 * @clean *
 * @compile -XDstringConcat=indy              StringAppendEvaluatesInOrder.java
 * @run main StringAppendEvaluatesInOrder
 *
 * @clean *
 * @compile -XDstringConcat=indyWithConstants StringAppendEvaluatesInOrder.java
 * @run main StringAppendEvaluatesInOrder
 *
 * @clean *
 * @compile -XDstringConcat=inline            StringAppendEvaluatesInOrder.java
 * @run main StringAppendEvaluatesInOrder
 */

public class StringAppendEvaluatesInOrder {
    static String test() {
        StringBuilder builder = new StringBuilder("foo");
        int i = 15;
        return "Test: " + i + " " + (++i) + builder + builder.append("bar");
    }

    static String compoundAssignment() {
        StringBuilder builder2 = new StringBuilder("foo");
        Object oo = builder2;
        oo += "" + builder2.append("bar");
        return oo.toString();
    }

    public static void main(String[] args) throws Exception {
        assertEquals(test(), "Test: 15 16foofoobar");
        assertEquals(compoundAssignment(), "foofoobar");
    }

    private static void assertEquals(String actual, String expected) {
      if (!actual.equals(expected)) {
        throw new AssertionError("expected: " + expected + ", actual: " + actual);
      }
    }
}
