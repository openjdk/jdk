/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8300543 8309336 8311825
 * @summary Check switches work correctly with qualified enum constants
 * @compile/fail/ref=EnumSwitchQualifiedErrors.out -XDrawDiagnostics EnumSwitchQualifiedErrors.java
*/

public class EnumSwitchQualifiedErrors {

    int testPatternMatchingSwitch1(I i) {
        return switch(i) {
            case E1.A -> 1;
            case E2.A -> 2;
        };
    }

    int testPatternMatchingSwitch2(E1 e) {
        return switch(e) {
            case E1.A -> 1;
            case E2.A -> 4;
        };
    }

    int testPatternMatchingSwitch3(Number n) {
        return switch(n) {
            case E1.A -> 1;
            case E2.A -> 2;
        };
    }

    int testPatternMatchingSwitch4(E1 e) {
        return switch(e) {
            case E1A -> 1;
            case (E1) null -> 1;
            case E1 -> 1;
            default -> {}
        };
    }

    int testPatternMatchingSwitch5(Object e) {
        return switch(e) {
            case E1A -> 1;
            case (E1) null -> 1;
            case E1 -> 1;
            default -> {}
        };
    }

    int testQualifiedDuplicate1(Object o) {
        return switch(o) {
            case E1.A -> 1;
            case E1.A -> 2;
            default -> -1;
        };
    }

    int testQualifiedDuplicate2(E1 e) {
        return switch(e) {
            case A -> 1;
            case E1.A -> 2;
            default -> -1;
        };
    }

    int testQualifiedDuplicate3(E1 e) {
        return switch(e) {
            case E1.A -> 1;
            case A -> 2;
            default -> -1;
        };
    }

    sealed interface I {}
    enum E1 implements I { A; }
    enum E2 { A; }

    static final E1 E1A = null;
}
