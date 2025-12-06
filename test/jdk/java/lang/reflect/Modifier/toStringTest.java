/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4394937 8051382 8373125
 * @summary tests the toString method of reflect.Modifier
 */

import java.lang.reflect.Modifier;
import static java.lang.reflect.Modifier.*;

public class toStringTest {

    static void testString(int test, String expected) {
        String result = Modifier.toString(test);
        if(!expected.equals(result)) {
            System.err.println("For input 0x" + Integer.toHexString(test));
            System.err.println("expected:\t" + expected + "\ngot\t\t" + result);

            throw new RuntimeException();
        }
    }

    public static void main(String... argv) {
        int allMods = PUBLIC    | PROTECTED | PRIVATE |
                      ABSTRACT  | STATIC    | FINAL |
                      TRANSIENT | VOLATILE  | SYNCHRONIZED |
                      NATIVE    | STRICT    | INTERFACE;

        String allModsString = "public protected private abstract static " +
            "final transient volatile synchronized native strictfp interface";

        final int ALL_ONES = ~0;

        // zero should have an empty string
        testString(0, "");

        // test to make sure all modifiers print out in the proper order
        testString(allMods, allModsString);

        // verify no extraneous modifiers are printed
        testString(ALL_ONES, allModsString);

        ModifierKindCase[] kindModifiers = {
            new ModifierKindCase(classModifiers(),
                                 "public protected private abstract " +
                                 "static final strictfp"),

            new ModifierKindCase(constructorModifiers(),"public protected private"),

            new ModifierKindCase(fieldModifiers(),
                                 "public protected private " +
                                 "static final transient volatile"),

            new ModifierKindCase(interfaceModifiers(),
                                 "public protected private " +
                                 "abstract static strictfp"),

            new ModifierKindCase(methodModifiers(),
                                 "public protected private abstract " +
                                 "static final synchronized native strictfp"),

            new ModifierKindCase(parameterModifiers(), "final"),
        };

        for (var modKindCase : kindModifiers) {
            testString(ALL_ONES & modKindCase.mask(), modKindCase.expected());
        }
    }

    private record ModifierKindCase(int mask, String expected){}
}
