/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262891 8310133
 * @summary Check errors reported for guarded patterns.
 * @compile/fail/ref=GuardsErrors.out -XDrawDiagnostics GuardsErrors.java
 */

//TODO: tests and error recovery for misplaced guards
public class GuardsErrors {

    void typeTestPatternSwitchTest(Object o, int check) {
        switch (o) {
            case Integer i when i == check -> System.err.println(); //error: check is not effectivelly final
            default -> {}
        }
        check = 0;

    }

    void variablesInGuards(Object o) {
        final int i1;
              int i2 = 0;
        switch (o) {
            case Integer v when (i1 = 0) == 0 -> {}
            case Integer v when i2++ == 0 -> {}
            case Integer v when ++i2 == 0 -> {}
            case Integer v when new Predicate() {
                public boolean test() {
                    final int i;
                    i = 2;
                    return i == 2;
                }
            }.test() -> {}
            case Integer v when v != null -> {
                v = null;
            }
            case Number v1 when v1 instanceof Integer v2 && (v2 = 0) == 0 -> {}
            default -> {}
        }
    }

    GuardsErrors(Object o) {
        switch (o) {
            case Integer v when (f = 0) == 0 -> {}
            default -> throw new RuntimeException();
        }
    }

    final int f;

    interface Predicate {
        public boolean test();
    }
}
