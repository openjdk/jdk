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

/*
 * @test
 * @bug 0000000
 * @summary Exercise runtime handing of templated strings.
 * @enablePreview true
 */

import java.lang.StringTemplate.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static java.lang.StringTemplate.RAW;

public class Basic {
    public static void main(String... arg) {
        equalsHashCode();
        concatenationTests();
        componentTests();
        limitsTests();
        processorTests();
        stringTemplateCoverage();
        simpleProcessorCoverage();
    }

    static void ASSERT(String a, String b) {
        if (!Objects.equals(a, b)) {
            System.out.println(a);
            System.out.println(b);
            throw new RuntimeException("Test failed");
        }
    }

    static void ASSERT(Object a, Object b) {
        if (!Objects.deepEquals(a, b)) {
            System.out.println(a);
            System.out.println(b);
            throw new RuntimeException("Test failed");
        }
    }

    /*
     * equals and hashCode tests.
     */
    static void equalsHashCode() {
        int x = 10;
        int y = 20;
        int a = 10;
        int b = 20;

        StringTemplate st0 = RAW."\{x} + \{y} = \{x + y}";
        StringTemplate st1 = RAW."\{a} + \{b} = \{a + b}";
        StringTemplate st2 = RAW."\{x} + \{y} = \{x + y}!";
        x++;
        StringTemplate st3 = RAW."\{x} + \{y} = \{x + y}";

        if (!st0.equals(st1)) throw new RuntimeException("st0 != st1");
        if (st0.equals(st2)) throw new RuntimeException("st0 == st2");
        if (st0.equals(st3)) throw new RuntimeException("st0 == st3");

        if (st0.hashCode() != st1.hashCode()) throw new RuntimeException("st0.hashCode() != st1.hashCode()");
    }

    /*
     * Concatenation tests.
     */
    static void concatenationTests() {
        int x = 10;
        int y = 20;

        ASSERT(STR."\{x} \{y}", x + " " + y);
        ASSERT(STR."\{x + y}", "" + (x + y));
        ASSERT(STR.process(RAW."\{x} \{y}"), x + " " + y);
        ASSERT(STR.process(RAW."\{x + y}"), "" + (x + y));
        ASSERT((RAW."\{x} \{y}").process(STR), x + " " + y);
        ASSERT((RAW."\{x + y}").process(STR), "" + (x + y));
    }

    /*
     * Component tests.
     */
    static void componentTests() {
        int x = 10;
        int y = 20;

        StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
        ASSERT(st.values(), List.of(x, y, x + y));
        ASSERT(st.fragments(), List.of("", " + ", " = ", ""));
        ASSERT(st.interpolate(), x + " + " + y + " = " + (x + y));
    }

    /*
     * Limits tests.
     */
    static void limitsTests() {
        int x = 9;

        StringTemplate ts250 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             """;
        ASSERT(ts250.values().size(), 250);
        ASSERT(ts250.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999
               """);

        StringTemplate ts251 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}
             """;
        ASSERT(ts251.values().size(), 251);
        ASSERT(ts251.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9
               """);

        StringTemplate ts252 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}
             """;
        ASSERT(ts252.values().size(), 252);
        ASSERT(ts252.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 99
               """);

        StringTemplate ts253 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}
             """;
        ASSERT(ts253.values().size(), 253);
        ASSERT(ts253.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 999
               """);

        StringTemplate ts254 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}
             """;
        ASSERT(ts254.values().size(), 254);
        ASSERT(ts254.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999
               """);

        StringTemplate ts255 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}
             """;
        ASSERT(ts255.values().size(), 255);
        ASSERT(ts255.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 99999
               """);

        StringTemplate ts256 = RAW."""
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}

             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}
             \{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x}\{x} \{x}\{x}\{x}\{x}\{x}\{x}
             """;
        ASSERT(ts256.values().size(), 256);
        ASSERT(ts256.interpolate(), """
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999
               9999999999 9999999999

               9999999999 9999999999
               9999999999 9999999999
               9999999999 999999
               """);

    }

    /*
     * Processor tests.
     */
    public static final Processor<StringTemplate, RuntimeException> STRINGIFY = st -> {
        List<Object> values = st.values()
                .stream()
                .map(v -> (Object)String.valueOf(v))
                .toList();

        return StringTemplate.of(st.fragments(), values);
    };

    public static final Processor<StringTemplate, RuntimeException> UPPER = st -> {
        List<String> fragments = st.fragments()
                .stream()
                .map(String::toUpperCase)
                .toList();

        return StringTemplate.of(fragments, st.values());
    };

    public static final Processor<String, RuntimeException> CHAIN = st -> {
        st = STRINGIFY.process(st);
        st = UPPER.process(st);
        return STR.process(st);
    };

    static void processorTests() {
        String name = "Joan";
        int age = 25;
        ASSERT(CHAIN."\{name} is \{age} years old", "Joan IS 25 YEARS OLD");
    }

    /*
     *  StringTemplate coverage
     */
    static void stringTemplateCoverage() {
        StringTemplate tsNoValues = StringTemplate.of("No Values");

        ASSERT(tsNoValues.values(), List.of());
        ASSERT(tsNoValues.fragments(), List.of("No Values"));
        ASSERT(tsNoValues.interpolate(), STR."No Values");

        int x = 10, y = 20;
        StringTemplate src = RAW."\{x} + \{y} = \{x + y}";
        StringTemplate tsValues = StringTemplate.of(src.fragments(), src.values());
        ASSERT(tsValues.fragments(), List.of("", " + ", " = ", ""));
        ASSERT(tsValues.values(), List.of(x, y, x + y));
        ASSERT(tsValues.interpolate(), x + " + " + y + " = " + (x + y));
        ASSERT(StringTemplate.combine(src, src).interpolate(),
                RAW."\{x} + \{y} = \{x + y}\{x} + \{y} = \{x + y}".interpolate());
        ASSERT(StringTemplate.combine(src), src);
        ASSERT(StringTemplate.combine().interpolate(), "");
        ASSERT(StringTemplate.combine(List.of(src, src)).interpolate(),
                RAW."\{x} + \{y} = \{x + y}\{x} + \{y} = \{x + y}".interpolate());
    }

    /*
     * SimpleProcessor coverage.
     */

    static class Processor0 implements Processor<String, IllegalArgumentException> {
        @Override
        public String process(StringTemplate stringTemplate) throws IllegalArgumentException {
            StringBuilder sb = new StringBuilder();
            Iterator<String> fragmentsIter = stringTemplate.fragments().iterator();

            for (Object value : stringTemplate.values()) {
                sb.append(fragmentsIter.next());

                if (value instanceof Boolean) {
                    throw new IllegalArgumentException("I don't like Booleans");
                }

                sb.append(value);
            }

            sb.append(fragmentsIter.next());

            return sb.toString();
        }
    }

    static Processor0 processor0 = new Processor0();

    static Processor<String, RuntimeException> processor1 =
        st -> st.interpolate();

    static Processor<String, RuntimeException> processor2 = st -> st.interpolate();

    static Processor<String, RuntimeException> processor3 = st -> st.interpolate();

    static Processor<String, RuntimeException> processor4 = st ->
        StringTemplate.interpolate(st.fragments(), st.values());


    static void simpleProcessorCoverage() {
        try {
            int x = 10;
            int y = 20;
            ASSERT(processor0."\{x} + \{y} = \{x + y}", "10 + 20 = 30");
            ASSERT(processor1."\{x} + \{y} = \{x + y}", "10 + 20 = 30");
            ASSERT(processor2."\{x} + \{y} = \{x + y}", "10 + 20 = 30");
            ASSERT(processor3."\{x} + \{y} = \{x + y}", "10 + 20 = 30");
            ASSERT(processor4."\{x} + \{y} = \{x + y}", "10 + 20 = 30");
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("processor fail");
        }
    }

    static String justify(String string, int width) {
        boolean leftJustify = width < 0;
        int length = string.length();
        width = Math.abs(width);
        int diff = width - length;

        if (diff < 0) {
            string = "*".repeat(width);
        } else if (0 < diff) {
            if (leftJustify) {
                string += " ".repeat(diff);
            } else {
                string = " ".repeat(diff) + string;
            }
        }

        return string;
    }

}
