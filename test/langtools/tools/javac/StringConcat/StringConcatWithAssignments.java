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

/**
 * @test
 * @compile StringConcatWithAssignments.java
 * @run main StringConcatWithAssignments
 * @compile -XDstringConcat=inline StringConcatWithAssignments.java
 * @run main StringConcatWithAssignments
 * @compile -XDstringConcat=indy StringConcatWithAssignments.java
 * @run main StringConcatWithAssignments
 * @compile -XDstringConcat=indyWithConstants StringConcatWithAssignments.java
 * @run main StringConcatWithAssignments
 */

import java.util.Objects;
import java.util.function.Supplier;

public class StringConcatWithAssignments {
    public static void main(String[] args) {
        StringConcatWithAssignments instance = new StringConcatWithAssignments();
        assertEquals("nulltrue", instance.assignment());
        assertEquals("nulltrue", instance.invocation());
    }
    private String assignment() {
        boolean b;
        return ((((b = true) ? null : null) + "") + b);
    }
    private String invocation() {
        StringBuilder sideEffect = new StringBuilder();
        Supplier<Boolean> provider = () -> {
            sideEffect.append(true);
            return true;
        };
        return (((provider.get() ? null : null) + "") + sideEffect.toString());
    }
    private static void assertEquals(Object o1, Object o2) {
        if (!Objects.equals(o1, o2)) {
            throw new AssertionError("Expected that '" + o1 + "' and " +
                                     "'" + o2 + "' are equal.");
        }
    }
}
