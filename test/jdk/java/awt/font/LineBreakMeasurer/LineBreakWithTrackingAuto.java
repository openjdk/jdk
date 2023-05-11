/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 8165943
  @summary LineBreakMeasurer does not measure correctly if TextAttribute.TRACKING is set
  @run main/othervm LineBreakWithTrackingAuto
*/

import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;

public class LineBreakWithTrackingAuto {

  private static final String WORD = "word";
  private static final String SPACE = " ";
  private static final int NUM_WORDS = 12;
  private static final float FONT_SIZE = 24.0f;
  private static final float TEXT_TRACKING[] = { -0.1f, 0f, 0.1f, 0.2f, 0.3f };
  private static final float EPSILON = 0.005f;


  public static void main(String[] args) {
    new LineBreakWithTrackingAuto().test();
  }

  public void test() {

    final FontRenderContext frc = new FontRenderContext(null, false, false);

    // construct a paragraph as follows: [SPACE + WORD] + ...
    StringBuffer text = new StringBuffer();
    for (int i = 0; i < NUM_WORDS; i++) {
      text.append(SPACE);
      text.append(WORD);
    }
    AttributedString attrString = new AttributedString(text.toString());
    attrString.addAttribute(TextAttribute.SIZE, Float.valueOf(FONT_SIZE));

    // test different tracking values: -0.1f, 0f, 0.1f, 0.2f, 0.3f
    for (float textTracking : TEXT_TRACKING) {

      final float trackingAdvance = FONT_SIZE * textTracking;
      attrString.addAttribute(TextAttribute.TRACKING, textTracking);

      LineBreakMeasurer measurer = new LineBreakMeasurer(attrString.getIterator(), frc);

      final int sequenceLength = WORD.length() + SPACE.length();
      final float sequenceAdvance = getSequenceAdvance(measurer, text.length(), sequenceLength);
      final float textAdvance = NUM_WORDS * sequenceAdvance;

      // test different wrapping width starting from the WORD+SPACE to TEXT width
      for (float wrappingWidth = sequenceAdvance; wrappingWidth < textAdvance; wrappingWidth += sequenceAdvance / sequenceLength) {

        measurer.setPosition(0);

        // break a paragraph into lines that fit the given wrapping width
        do {
          TextLayout layout = measurer.nextLayout(wrappingWidth);
          float visAdvance = layout.getVisibleAdvance();

          int currPos = measurer.getPosition();
          if ((trackingAdvance <= 0 && visAdvance - wrappingWidth > EPSILON)
                  || (trackingAdvance > 0 && visAdvance - wrappingWidth > trackingAdvance + EPSILON)) {
            throw new Error("text line is too long for given wrapping width");
          }

          if (currPos < text.length() && visAdvance <= wrappingWidth - sequenceAdvance) {
            throw new Error("text line is too short for given wrapping width");
          }
        } while (measurer.getPosition() != text.length());

      }
    }
  }

  private float getSequenceAdvance(LineBreakMeasurer measurer, int textLength, int sequenceLength) {

    measurer.setPosition(textLength - sequenceLength);

    TextLayout layout = measurer.nextLayout(10000.0f);
    if (layout.getCharacterCount() != sequenceLength) {
      throw new Error("layout length is incorrect");
    }

    return layout.getVisibleAdvance();
  }

}
