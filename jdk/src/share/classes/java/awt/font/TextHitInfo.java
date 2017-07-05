/*
 * Copyright (c) 1997, 1998, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * (C) Copyright Taligent, Inc. 1996 - 1997, All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998, All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by Taligent, Inc., a wholly-owned subsidiary
 * of IBM. These materials are provided under terms of a License
 * Agreement between Taligent and Sun. This technology is protected
 * by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.awt.font;
import java.lang.String;

/**
 * The <code>TextHitInfo</code> class represents a character position in a
 * text model, and a <b>bias</b>, or "side," of the character.  Biases are
 * either <EM>leading</EM> (the left edge, for a left-to-right character)
 * or <EM>trailing</EM> (the right edge, for a left-to-right character).
 * Instances of <code>TextHitInfo</code> are used to specify caret and
 * insertion positions within text.
 * <p>
 * For example, consider the text "abc".  TextHitInfo.trailing(1)
 * corresponds to the right side of the 'b' in the text.
 * <p>
 * <code>TextHitInfo</code> is used primarily by {@link TextLayout} and
 * clients of <code>TextLayout</code>.  Clients of <code>TextLayout</code>
 * query <code>TextHitInfo</code> instances for an insertion offset, where
 * new text is inserted into the text model.  The insertion offset is equal
 * to the character position in the <code>TextHitInfo</code> if the bias
 * is leading, and one character after if the bias is trailing.  The
 * insertion offset for TextHitInfo.trailing(1) is 2.
 * <p>
 * Sometimes it is convenient to construct a <code>TextHitInfo</code> with
 * the same insertion offset as an existing one, but on the opposite
 * character.  The <code>getOtherHit</code> method constructs a new
 * <code>TextHitInfo</code> with the same insertion offset as an existing
 * one, with a hit on the character on the other side of the insertion offset.
 * Calling <code>getOtherHit</code> on trailing(1) would return leading(2).
 * In general, <code>getOtherHit</code> for trailing(n) returns
 * leading(n+1) and <code>getOtherHit</code> for leading(n)
 * returns trailing(n-1).
 * <p>
 * <strong>Example</strong>:<p>
 * Converting a graphical point to an insertion point within a text
 * model
 * <blockquote><pre>
 * TextLayout layout = ...;
 * Point2D.Float hitPoint = ...;
 * TextHitInfo hitInfo = layout.hitTestChar(hitPoint.x, hitPoint.y);
 * int insPoint = hitInfo.getInsertionIndex();
 * // insPoint is relative to layout;  may need to adjust for use
 * // in a text model
 * </pre></blockquote>
 *
 * @see TextLayout
 */

public final class TextHitInfo {
    private int charIndex;
    private boolean isLeadingEdge;

    /**
     * Constructs a new <code>TextHitInfo</code>.
     * @param charIndex the index of the character hit
     * @param isLeadingEdge <code>true</code> if the leading edge of the
     * character was hit
     */
    private TextHitInfo(int charIndex, boolean isLeadingEdge) {
        this.charIndex = charIndex;
        this.isLeadingEdge = isLeadingEdge;
    }

    /**
     * Returns the index of the character hit.
     * @return the index of the character hit.
     */
    public int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns <code>true</code> if the leading edge of the character was
     * hit.
     * @return <code>true</code> if the leading edge of the character was
     * hit; <code>false</code> otherwise.
     */
    public boolean isLeadingEdge() {
        return isLeadingEdge;
    }

    /**
     * Returns the insertion index.  This is the character index if
     * the leading edge of the character was hit, and one greater
     * than the character index if the trailing edge was hit.
     * @return the insertion index.
     */
    public int getInsertionIndex() {
        return isLeadingEdge ? charIndex : charIndex + 1;
    }

    /**
     * Returns the hash code.
     * @return the hash code of this <code>TextHitInfo</code>, which is
     * also the <code>charIndex</code> of this <code>TextHitInfo</code>.
     */
    public int hashCode() {
        return charIndex;
    }

    /**
     * Returns <code>true</code> if the specified <code>Object</code> is a
     * <code>TextHitInfo</code> and equals this <code>TextHitInfo</code>.
     * @param obj the <code>Object</code> to test for equality
     * @return <code>true</code> if the specified <code>Object</code>
     * equals this <code>TextHitInfo</code>; <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        return (obj instanceof TextHitInfo) && equals((TextHitInfo)obj);
    }

    /**
     * Returns <code>true</code> if the specified <code>TextHitInfo</code>
     * has the same <code>charIndex</code> and <code>isLeadingEdge</code>
     * as this <code>TextHitInfo</code>.  This is not the same as having
     * the same insertion offset.
     * @param hitInfo a specified <code>TextHitInfo</code>
     * @return <code>true</code> if the specified <code>TextHitInfo</code>
     * has the same <code>charIndex</code> and <code>isLeadingEdge</code>
     * as this <code>TextHitInfo</code>.
     */
    public boolean equals(TextHitInfo hitInfo) {
        return hitInfo != null && charIndex == hitInfo.charIndex &&
            isLeadingEdge == hitInfo.isLeadingEdge;
    }

    /**
     * Returns a <code>String</code> representing the hit for debugging
     * use only.
     * @return a <code>String</code> representing this
     * <code>TextHitInfo</code>.
     */
    public String toString() {
        return "TextHitInfo[" + charIndex + (isLeadingEdge ? "L" : "T")+"]";
    }

    /**
     * Creates a <code>TextHitInfo</code> on the leading edge of the
     * character at the specified <code>charIndex</code>.
     * @param charIndex the index of the character hit
     * @return a <code>TextHitInfo</code> on the leading edge of the
     * character at the specified <code>charIndex</code>.
     */
    public static TextHitInfo leading(int charIndex) {
        return new TextHitInfo(charIndex, true);
    }

    /**
     * Creates a hit on the trailing edge of the character at
     * the specified <code>charIndex</code>.
     * @param charIndex the index of the character hit
     * @return a <code>TextHitInfo</code> on the trailing edge of the
     * character at the specified <code>charIndex</code>.
     */
    public static TextHitInfo trailing(int charIndex) {
        return new TextHitInfo(charIndex, false);
    }

    /**
     * Creates a <code>TextHitInfo</code> at the specified offset,
     * associated with the character before the offset.
     * @param offset an offset associated with the character before
     * the offset
     * @return a <code>TextHitInfo</code> at the specified offset.
     */
    public static TextHitInfo beforeOffset(int offset) {
        return new TextHitInfo(offset-1, false);
    }

    /**
     * Creates a <code>TextHitInfo</code> at the specified offset,
     * associated with the character after the offset.
     * @param offset an offset associated with the character after
     * the offset
     * @return a <code>TextHitInfo</code> at the specified offset.
     */
    public static TextHitInfo afterOffset(int offset) {
        return new TextHitInfo(offset, true);
    }

    /**
     * Creates a <code>TextHitInfo</code> on the other side of the
     * insertion point.  This <code>TextHitInfo</code> remains unchanged.
     * @return a <code>TextHitInfo</code> on the other side of the
     * insertion point.
     */
    public TextHitInfo getOtherHit() {
        if (isLeadingEdge) {
            return trailing(charIndex - 1);
        } else {
            return leading(charIndex + 1);
        }
    }

    /**
     * Creates a <code>TextHitInfo</code> whose character index is offset
     * by <code>delta</code> from the <code>charIndex</code> of this
     * <code>TextHitInfo</code>. This <code>TextHitInfo</code> remains
     * unchanged.
     * @param delta the value to offset this <code>charIndex</code>
     * @return a <code>TextHitInfo</code> whose <code>charIndex</code> is
     * offset by <code>delta</code> from the <code>charIndex</code> of
     * this <code>TextHitInfo</code>.
     */
    public TextHitInfo getOffsetHit(int delta) {
        return new TextHitInfo(charIndex + delta, isLeadingEdge);
    }
}
