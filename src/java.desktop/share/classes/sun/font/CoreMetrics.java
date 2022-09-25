/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 2003, All Rights Reserved
 *
 */

package sun.font;

import java.awt.font.LineMetrics;
import java.awt.font.GraphicAttribute;

public final class CoreMetrics {

    public CoreMetrics(float ascent,
                       float descent,
                       float leading,
                       float height,
                       int baselineIndex,
                       float[] baselineOffsets,
                       float strikethroughOffset,
                       float strikethroughThickness,
                       float underlineOffset,
                       float underlineThickness,
                       float ssOffset,
                       float italicAngle) {
        this.ascent = ascent;
        this.descent = descent;
        this.leading = leading;
        this.height = height;
        this.baselineIndex = baselineIndex;
        this.baselineOffsets = baselineOffsets;
        this.strikethroughOffset = strikethroughOffset;
        this.strikethroughThickness = strikethroughThickness;
        this.underlineOffset = underlineOffset;
        this.underlineThickness = underlineThickness;
        this.ssOffset = ssOffset;
        this.italicAngle = italicAngle;
    }

    public static CoreMetrics get(LineMetrics lm) {
        return ((FontLineMetrics)lm).cm;
    }

    public int hashCode() {
        return Float.floatToIntBits(ascent + ssOffset);
    }

    public boolean equals(Object o) {
        return this == o || o instanceof CoreMetrics o1
                && ascent == o1.ascent
                && descent == o1.descent
                && leading == o1.leading
                && baselineIndex == o1.baselineIndex
                && baselineOffsets[0] == o1.baselineOffsets[0]
                && baselineOffsets[1] == o1.baselineOffsets[1]
                && baselineOffsets[2] == o1.baselineOffsets[2]
                && strikethroughOffset == o1.strikethroughOffset
                && strikethroughThickness == o1.strikethroughThickness
                && underlineOffset == o1.underlineOffset
                && underlineThickness == o1.underlineThickness
                && ssOffset == o1.ssOffset
                && italicAngle == o1.italicAngle;
    }

    // fullOffsets is an array of 5 baseline offsets,
    // roman, center, hanging, bottom, and top in that order
    // this does NOT add the ssOffset
    public float effectiveBaselineOffset(float[] fullOffsets) {
        switch (baselineIndex) {
        case GraphicAttribute.TOP_ALIGNMENT:
            return fullOffsets[4] + ascent;
        case GraphicAttribute.BOTTOM_ALIGNMENT:
            return fullOffsets[3] - descent;
        default:
            return fullOffsets[baselineIndex];
        }
    }

    public final float   ascent;
    public final float   descent;
    public final float   leading;
    public final float   height;
    public final int     baselineIndex;
    public final float[] baselineOffsets; // !! this is a hole, don't expose this class
    public final float   strikethroughOffset;
    public final float   strikethroughThickness;
    public final float   underlineOffset;
    public final float   underlineThickness;
    public final float   ssOffset;
    public final float   italicAngle;
}
