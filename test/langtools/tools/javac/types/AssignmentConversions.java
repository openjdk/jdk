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
 * @bug 8385070
 * @summary javac should use subtype checks for language rules specified in terms of subtyping
 * @compile AssignmentConversions.java
 * @run main AssignmentConversions
 */

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AssignmentConversions {
    sealed interface I permits A {}
    static final class A implements I {}

    public static void main(String... args) {
        orderIndependentConditional(true);
        orderIndependentConditional(false);
        orderIndependentSwitch(0);
        orderIndependentSwitch(1);
        pureAssignmentContexts(true, 0);
        pureAssignmentContexts(false, 1);
        throwExpressionContext();
    }

    static void orderIndependentConditional(boolean flag) {
        I i = new A();

        var v1 = flag ? new A() : i;
        var v2 = flag ? i : new A();

        String s1 = overloadShouldPickI(v1);
        String s2 = overloadShouldPickI(v2);
    }

    static void orderIndependentSwitch(int selector) {
        I i = new A();

        var v1 = switch (selector) {
            case 0 -> new A();
            default -> i;
        };
        var v2 = switch (selector) {
            case 0 -> i;
            default -> new A();
        };

        String s1 = overloadShouldPickI(v1);
        String s2 = overloadShouldPickI(v2);
    }

    static void pureAssignmentContexts(boolean flag, int selector) {
        // asserting the pure assignment context (a raw type ArrayList is not a
        // clean subtype of ArrayList<String> but it is accepted by assignment)
        ArrayList raw = new ArrayList();

        ArrayList<String> declarationInitializer = raw;

        ArrayList<String> simple;
        simple = raw;

        ArrayList<String>[] array = new ArrayList[1];
        array[0] = raw;

        ArrayList<String>[] arrayInitializer = new ArrayList[] { raw };

        ArrayList<String> returned = methodReturn(raw);

        Supplier<ArrayList<String>> lambda = () -> raw;

        Supplier<ArrayList<String>> methodRef = new Box()::val;

        Iterable<ArrayList> rawIterable = List.of(raw);
        for (ArrayList<String> item : rawIterable) { }

        ArrayList<String> conditional = flag ? raw : new ArrayList<String>();

        ArrayList<String> switchExpression = switch (selector) {
            case 0 -> raw;
            default -> new ArrayList<String>();
        };
    }

    static String overloadShouldPickI(I i) {
        return "";
    }
    static Integer overloadShouldPickI(A a) {
        return 0;
    }

    static ArrayList<String> methodReturn(ArrayList list) {
        return list;
    }

    static void throwExpressionContext() {
        try {
            throwRuntime(new RuntimeException());
        } catch (RuntimeException ex) {
        }
    }

    static void throwRuntime(RuntimeException ex) {
        throw ex;
    }

    static class Box {
        ArrayList val() {
            return new ArrayList();
        }
    }
}
