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
 * @bug 7083187
 * @summary  Verifies if CSS.CSSValue attribute is same
 * @run main CSSAttributeEqualityBug
 */
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.CSS;
import javax.swing.text.html.StyleSheet;

public class CSSAttributeEqualityBug {

    public static void main(String[] args) {
        testFontSize();
        testFontFamily();
        testFontWeight();
        testColor();
        testBorderStyle();
        testCSSLengthValue();
        testBackgroundPosition();
        testCSSStringValue();
    }

    private static void testFontSize() {
        StyleSheet ss = new StyleSheet();
        String fontSize = "42";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.FONT_SIZE, fontSize);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.FONT_SIZE, fontSize);

        if (a.isEqual(b)) {
            System.out.println( "a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.FontSize a is not equal to b");
        }
    }

    private static void testFontFamily() {
        StyleSheet ss = new StyleSheet();
        String fontFamily = "Sans-Serif";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.FONT_FAMILY, "Sans-Serif");

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.FONT_FAMILY, "Sans-Serif");

        if (a.isEqual(b)) {
            System.out.println( "a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.FontFamily a is not equal to b");
        }
    }

    private static void testFontWeight() {
        StyleSheet ss = new StyleSheet();
        String fontWeight = "bold";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.FONT_WEIGHT, fontWeight);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.FONT_WEIGHT, fontWeight);

        if (a.isEqual(b)) {
            System.out.println( "a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.FontWeight a is not equal to b");
        }
    }

    private static void testColor() {
        StyleSheet ss = new StyleSheet();
        String fontColor = "red";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.COLOR, fontColor);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.COLOR, fontColor);

        if (a.isEqual(b)) {
            System.out.println( "a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.Color a is not equal to b");
        }
    }

    private static void testBorderStyle() {
        StyleSheet ss = new StyleSheet();
        String borderStyle = "DASHED";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.BORDER_STYLE, borderStyle);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.BORDER_STYLE, borderStyle);

        if (a.isEqual(b)) {
            System.out.println( "a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.BorderStyle a is not equal to b");
        }
    }

    private static void testCSSLengthValue() {
        StyleSheet ss = new StyleSheet();
        String lengthUnit = "42";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.MARGIN_TOP, lengthUnit);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.MARGIN_TOP, lengthUnit);

        if (a.isEqual(b)) {
            System.out.println("a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS LengthValue a is not equal to b");
        }
    }

    private static void testBackgroundPosition() {
        StyleSheet ss = new StyleSheet();
        String bgPosition = "top";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.BACKGROUND_POSITION, bgPosition);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.BACKGROUND_POSITION, bgPosition);

        if (a.isEqual(b)) {
            System.out.println("a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.BACKGROUND_POSITION a is not equal to b");
        }
    }

    private static void testCSSStringValue() {
        StyleSheet ss = new StyleSheet();
        String strVal = "underline";

        SimpleAttributeSet a = new SimpleAttributeSet();
        ss.addCSSAttribute(a, CSS.Attribute.TEXT_DECORATION, strVal);

        SimpleAttributeSet b = new SimpleAttributeSet();
        ss.addCSSAttribute(b, CSS.Attribute.TEXT_DECORATION, strVal);

        if (a.isEqual(b)) {
            System.out.println("a equals b");
        } else {
            System.out.println("a = " + a);
            System.out.println("b = " + b);
            throw new RuntimeException("CSS.Attribute.TEXT_DECORATION a is not equal to b");
        }
    }
}

