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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.swing.text.AttributeSet;
import javax.swing.text.html.StyleSheet;

/*
 * @test
 * @bug 7083187 8318113
 * @summary  Verifies if CSS.CSSValue attribute is same
 * @run main CSSAttributeEqualityBug
 */
public class CSSAttributeEqualityBug {

    /**
     * CSS declarations which should produce equal attribute sets.
     */
    private static final String[] EQUALS = {
            "font-size: 42",
            "font-size: 42px",
            "font-size: 42em",
            "font-size: medium",
            "font-size: smaller",
            "font-size: 200%",

            "font-family: sans-serif",
            "font-family: 'DejaVu Serif', serif",

            "font-weight: bold",

            "color: red",
            "color: rgb(255, 0, 0)",

            "border-style: dashed",

            "margin-top: 42",
            "margin-top: 42px",
            "margin-top: 100%",

            "text-decoration: underline",

            "background-position: top",
            "background-position: top right",
            "background-position: 25% 75%",
            "background-position: 0 0",
            "background-position: 1cm 2cm",
            "background-position: 1em 2em",

            "border-width: medium",

            "background-image: none",
            "background-image: url(image.png)",
    };

    /**
     * CSS declarations which should produce different attribute sets.
     */
    private static final String[][] NOT_EQUALS = {
            {"font-size: 42px", "font-size: 22px"},
            {"font-size: 42px", "font-size: 42pt"},
            {"font-size: 42em", "font-size: 42ex"},
            {"font-size: 100%", "font-size: 200%"},

            {"margin-top: 42px", "margin-top: 22px"},
            {"margin-top: 42px", "margin-top: 42pt"},
            {"margin-top: 100%", "margin-top: 50%"},

            {"background-image: none", "background-image: url(image.png)"},
    };

    private static final String[][] EQUALS_WITH_SPACE = {
            {"font-size: 42px", "font-size: 42 px"},
            {"font-size: 100%", "font-size: 100 %"},

            {"width: 42px", "width: 42 px"},
            {"width: 100%", "width: 100 %"},
    };

    public static void main(String[] args) {
        final List<String> failures = new ArrayList<>();

        Arrays.stream(EQUALS)
              .map(CSSAttributeEqualityBug::positiveTest)
              .filter(Objects::nonNull)
              .forEach(failures::add);
        Arrays.stream(NOT_EQUALS)
              .map(CSSAttributeEqualityBug::negativeTest)
              .filter(Objects::nonNull)
              .forEach(failures::add);
        Arrays.stream(EQUALS_WITH_SPACE)
              .map(CSSAttributeEqualityBug::positiveTest)
              .filter(Objects::nonNull)
              .forEach(failures::add);

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            throw new RuntimeException("The test failed: " + failures.size()
                                       + " failure(s) detected: "
                                       + failures.get(0));
        }
    }

    private static String positiveTest(String cssDeclaration) {
        StyleSheet ss = new StyleSheet();

        AttributeSet a = ss.getDeclaration(cssDeclaration);
        AttributeSet b = ss.getDeclaration(cssDeclaration);

        return assertEquals(a, b);
    }

    private static String positiveTest(String[] cssDeclaration) {
        StyleSheet ss = new StyleSheet();

        AttributeSet a = ss.getDeclaration(cssDeclaration[0]);
        AttributeSet b = ss.getDeclaration(cssDeclaration[1]);

        return assertEquals(a, b);
    }

    private static String negativeTest(String[] cssDeclaration) {
        StyleSheet ss = new StyleSheet();

        AttributeSet a = ss.getDeclaration(cssDeclaration[0]);
        AttributeSet b = ss.getDeclaration(cssDeclaration[1]);

        return assertNotEquals(a, b);
    }

    private static String assertEquals(AttributeSet a,
                                       AttributeSet b) {
        return !a.isEqual(b)
               ? getErrorMessage(a, b, "is not equal to")
               : null;
    }

    private static String assertNotEquals(AttributeSet a,
                                          AttributeSet b) {
        return a.isEqual(b)
               ? getErrorMessage(a, b, "is equal to")
               : null;
    }

    private static String getErrorMessage(AttributeSet a,
                                          AttributeSet b,
                                          String message) {
        return a + " " + message + " " + b;
    }

}
