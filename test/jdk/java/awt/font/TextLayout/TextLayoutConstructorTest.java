/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4138921
 * @summary Confirm constructor behavior for various edge cases.
 */

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class TextLayoutConstructorTest {

    public static void main(String[] args) throws Exception {
        testFontConstructor();
        testMapConstructor();
        testIteratorConstructor();
    }

    private static void testFontConstructor() {

        // new TextLayout(String, Font, FontRenderContext)

        Font font = new Font(Font.DIALOG, Font.PLAIN, 20);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        assertThrows(() -> new TextLayout(null, font, frc),
            IllegalArgumentException.class,
            "Null string passed to TextLayout constructor.");

        assertThrows(() -> new TextLayout("test", (Font) null, frc),
            IllegalArgumentException.class,
            "Null font passed to TextLayout constructor.");

        assertThrows(() -> new TextLayout("test", font, null),
            IllegalArgumentException.class,
            "Null font render context passed to TextLayout constructor.");

        Function< String, TextLayout > creator = (s) -> new TextLayout(s, font, frc);
        assertEmptyTextLayoutBehavior(creator);
    }

    private static void testMapConstructor() {

        // new TextLayout(String, Map, FontRenderContext)

        Map< TextAttribute, Object > attributes = Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        assertThrows(() -> new TextLayout(null, attributes, frc),
            IllegalArgumentException.class,
            "Null string passed to TextLayout constructor.");

        assertThrows(() -> new TextLayout("test", (Map) null, frc),
            IllegalArgumentException.class,
            "Null map passed to TextLayout constructor.");

        assertThrows(() -> new TextLayout("test", attributes, null),
            IllegalArgumentException.class,
            "Null font render context passed to TextLayout constructor.");

        Function< String, TextLayout > creator = (s) -> new TextLayout(s, attributes, frc);
        assertEmptyTextLayoutBehavior(creator);
    }

    private static void testIteratorConstructor() {

        // new TextLayout(AttributedCharacterIterator, FontRenderContext)

        Map< TextAttribute, Object > attributes = Map.of();
        FontRenderContext frc = new FontRenderContext(null, true, true);

        assertThrows(() -> new TextLayout(null, frc),
            IllegalArgumentException.class,
            "Null iterator passed to TextLayout constructor.");

        AttributedCharacterIterator it1 = new AttributedString("test", attributes).getIterator();
        assertThrows(() -> new TextLayout(it1, null),
            IllegalArgumentException.class,
            "Null font render context passed to TextLayout constructor.");

        Function< String, TextLayout > creator = (s) -> {
            AttributedCharacterIterator it2 = new AttributedString(s, attributes).getIterator();
            return new TextLayout(it2, frc);
        };
        assertEmptyTextLayoutBehavior(creator);
    }

    private static void assertEmptyTextLayoutBehavior(Function< String, TextLayout > creator) {

        TextLayout tl = creator.apply("");
        TextLayout ref = creator.apply(" "); // space
        FontRenderContext frc = new FontRenderContext(null, true, true);
        Rectangle zero = new Rectangle(0, 0, 0, 0);
        Rectangle2D.Float zero2D = new Rectangle2D.Float(0, 0, 0, 0);
        Rectangle2D.Float oneTwo = new Rectangle2D.Float(1, 2, 0, 0);
        Rectangle2D.Float kilo = new Rectangle2D.Float(0, 0, 1000, 1000);
        AffineTransform identity = new AffineTransform();
        TextLayout.CaretPolicy policy = new TextLayout.CaretPolicy();
        TextHitInfo start = TextHitInfo.trailing(-1);
        TextHitInfo end = TextHitInfo.leading(0);

        assertEqual(0, tl.getJustifiedLayout(100).getAdvance(), "justified advance");
        assertEqual(0, tl.getBaseline(), "baseline");

        float[] offsets = tl.getBaselineOffsets();
        float[] refOffsets = ref.getBaselineOffsets();
        assertEqual(3, offsets.length, "baseline offsets");
        assertEqual(refOffsets[0], offsets[0], "baseline offset 1");
        assertEqual(refOffsets[1], offsets[1], "baseline offset 2");
        assertEqual(refOffsets[2], offsets[2], "baseline offset 3");

        assertEqual(0, tl.getAdvance(), "advance");
        assertEqual(0, tl.getVisibleAdvance(), "visible advance");
        assertEqual(ref.getAscent(), tl.getAscent(), "ascent");
        assertEqual(ref.getDescent(), tl.getDescent(), "descent");
        assertEqual(ref.getLeading(), tl.getLeading(), "leading");
        assertEqual(zero2D, tl.getBounds(), "bounds");
        assertEqual(zero2D, tl.getPixelBounds(frc, 0, 0), "pixel bounds 1");
        assertEqual(oneTwo, tl.getPixelBounds(frc, 1, 2), "pixel bounds 2");
        assertEqual(true, tl.isLeftToRight(), "left to right");
        assertEqual(false, tl.isVertical(), "is vertical");
        assertEqual(0, tl.getCharacterCount(), "character count");

        float[] caretInfo = tl.getCaretInfo(start, kilo);
        float[] refCaretInfo = ref.getCaretInfo(start, kilo);
        assertEqual(6, caretInfo.length, "caret info length 1");
        assertEqual(refCaretInfo[0], caretInfo[0], "first caret info 1");
        assertEqual(refCaretInfo[1], caretInfo[1], "second caret info 1");
        assertEqual(refCaretInfo[2], caretInfo[2], "third caret info 1");
        assertEqual(refCaretInfo[3], caretInfo[3], "fourth caret info 1");
        assertEqual(refCaretInfo[4], caretInfo[4], "fifth caret info 1");
        assertEqual(refCaretInfo[5], caretInfo[5], "sixth caret info 1");

        float[] caretInfo2 = tl.getCaretInfo(start);
        float[] refCaretInfo2 = ref.getCaretInfo(start);
        assertEqual(6, caretInfo2.length, "caret info length 2");
        assertEqual(refCaretInfo2[0], caretInfo2[0], "first caret info 2");
        assertEqual(refCaretInfo2[1], caretInfo2[1], "second caret info 2");
        assertEqual(refCaretInfo2[2], caretInfo2[2], "third caret info 2");
        assertEqual(refCaretInfo2[3], caretInfo2[3], "fourth caret info 2");
        assertEqual(refCaretInfo2[4], caretInfo2[4], "fifth caret info 2");
        assertEqual(refCaretInfo2[5], caretInfo2[5], "sixth caret info 2");

        assertEqual(null, tl.getNextRightHit(start), "next right hit 1");
        assertEqual(null, tl.getNextRightHit(end), "next right hit 2");
        assertEqual(null, tl.getNextRightHit(0, policy), "next right hit 3");
        assertEqual(null, tl.getNextRightHit(0), "next right hit 4");
        assertEqual(null, tl.getNextLeftHit(start), "next left hit 1");
        assertEqual(null, tl.getNextLeftHit(end), "next left hit 2");
        assertEqual(null, tl.getNextLeftHit(0, policy), "next left hit 3");
        assertEqual(null, tl.getNextLeftHit(0), "next left hit 4");
        assertEqual(end, tl.getVisualOtherHit(start), "visual other hit");

        Shape caretShape = tl.getCaretShape(start, kilo);
        Shape refCaretShape = ref.getCaretShape(start, kilo);
        assertEqual(refCaretShape.getBounds(), caretShape.getBounds(), "caret shape 1");

        Shape caretShape2 = tl.getCaretShape(start);
        Shape refCaretShape2 = ref.getCaretShape(start);
        assertEqual(refCaretShape2.getBounds(), caretShape2.getBounds(), "caret shape 2");

        assertEqual(0, tl.getCharacterLevel(0), "character level");

        Shape[] caretShapes = tl.getCaretShapes(0, kilo, policy);
        Shape[] refCaretShapes = ref.getCaretShapes(0, kilo, policy);
        assertEqual(2, caretShapes.length, "caret shapes length 1");
        assertEqual(refCaretShapes[0].getBounds(), caretShapes[0].getBounds(), "caret shapes strong 1");
        assertEqual(refCaretShapes[1], caretShapes[1], "caret shapes weak 1");
        assertEqual(null, caretShapes[1], "caret shapes weak 1");

        Shape[] caretShapes2 = tl.getCaretShapes(0, kilo);
        Shape[] refCaretShapes2 = ref.getCaretShapes(0, kilo);
        assertEqual(2, caretShapes2.length, "caret shapes length 2");
        assertEqual(refCaretShapes2[0].getBounds(), caretShapes2[0].getBounds(), "caret shapes strong 2");
        assertEqual(refCaretShapes2[1], caretShapes2[1], "caret shapes weak 2");
        assertEqual(null, caretShapes2[1], "caret shapes weak 2");

        Shape[] caretShapes3 = tl.getCaretShapes(0);
        Shape[] refCaretShapes3 = ref.getCaretShapes(0);
        assertEqual(2, caretShapes3.length, "caret shapes length 3");
        assertEqual(refCaretShapes3[0].getBounds(), caretShapes3[0].getBounds(), "caret shapes strong 3");
        assertEqual(refCaretShapes3[1], caretShapes3[1], "caret shapes weak 3");
        assertEqual(null, caretShapes3[1], "caret shapes weak 3");

        assertEqual(0, tl.getLogicalRangesForVisualSelection(start, start).length, "logical ranges for visual selection");
        assertEqual(zero2D, tl.getVisualHighlightShape(start, start, kilo).getBounds(), "visual highlight shape 1");
        assertEqual(zero2D, tl.getVisualHighlightShape(start, start).getBounds(), "visual highlight shape 2");
        assertEqual(zero, tl.getLogicalHighlightShape(0, 0, kilo).getBounds(), "logical highlight shape 1");
        assertEqual(zero, tl.getLogicalHighlightShape(0, 0).getBounds(), "logical highlight shape 2");
        assertEqual(zero, tl.getBlackBoxBounds(0, 0).getBounds(), "black box bounds");

        TextHitInfo hit = tl.hitTestChar(0, 0);
        assertEqual(-1, hit.getCharIndex(), "hit test char index 1");
        assertEqual(false, hit.isLeadingEdge(), "hit test leading edge 1");

        TextHitInfo hit2 = tl.hitTestChar(0, 0, kilo);
        assertEqual(-1, hit2.getCharIndex(), "hit test char index 2");
        assertEqual(false, hit2.isLeadingEdge(), "hit test leading edge 2");

        assertEqual(false, tl.equals(creator.apply("")), "equals");
        assertEqual(false, tl.toString().isEmpty(), "to string");
        assertDoesNotDraw(tl);
        assertEqual(zero2D, tl.getOutline(identity).getBounds(), "outline");
        assertEqual(null, tl.getLayoutPath(), "layout path");

        Point2D.Float point = new Point2D.Float(7, 7);
        tl.hitToPoint(start, point);
        assertEqual(0, point.x, "hit to point x");
        assertEqual(0, point.y, "hit to point y");
    }

    private static void assertEqual(int expected, int actual, String name) {
        if (expected != actual) {
            throw new RuntimeException("Expected " + name + " = " + expected + ", but got " + actual);
        }
    }

    private static void assertEqual(float expected, float actual, String name) {
        if (expected != actual) {
            throw new RuntimeException("Expected " + name + " = " + expected + ", but got " + actual);
        }
    }

    private static void assertEqual(boolean expected, boolean actual, String name) {
        if (expected != actual) {
            throw new RuntimeException("Expected " + name + " = " + expected + ", but got " + actual);
        }
    }

    private static void assertEqual(Object expected, Object actual, String name) {
        if (!Objects.equals(expected, actual)) {
            throw new RuntimeException("Expected " + name + " = " + expected + ", but got " + actual);
        }
    }

    private static void assertThrows(Runnable r, Class< ? > type, String message) {
        Class< ? > actualType;
        String actualMessage;
        Exception actualException;
        try {
            r.run();
            actualType = null;
            actualMessage = null;
            actualException = null;
        } catch (Exception e) {
            actualType = e.getClass();
            actualMessage = e.getMessage();
            actualException = e;
        }
        if (!Objects.equals(type, actualType)) {
            throw new RuntimeException(type + " != " + actualType, actualException);
        }
        if (!Objects.equals(message, actualMessage)) {
            throw new RuntimeException(message + " != " + actualMessage, actualException);
        }
    }

    private static void assertDoesNotDraw(TextLayout layout) {

        int w = 200;
        int h = 200;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = image.createGraphics();
        int expected = image.getRGB(0, 0);

        layout.draw(g2d, w / 2f, h / 2f); // should not actually draw anything

        int[] rowPixels = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, rowPixels, 0, w);
            for (int x = 0; x < w; x++) {
                if (rowPixels[x] != expected) {
                    throw new RuntimeException(
                        "pixel (" + x + ", " + y +"): " + expected + " != " + rowPixels[x]);
                }
            }
        }
    }
}
