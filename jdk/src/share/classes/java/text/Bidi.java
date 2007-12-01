/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * (C) Copyright IBM Corp. 1999-2003 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 */

package java.text;

import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.awt.font.NumericShaper;
import sun.text.CodePointIterator;

/**
 * This class implements the Unicode Bidirectional Algorithm.
 * <p>
 * A Bidi object provides information on the bidirectional reordering of the text
 * used to create it.  This is required, for example, to properly display Arabic
 * or Hebrew text.  These languages are inherently mixed directional, as they order
 * numbers from left-to-right while ordering most other text from right-to-left.
 * <p>
 * Once created, a Bidi object can be queried to see if the text it represents is
 * all left-to-right or all right-to-left.  Such objects are very lightweight and
 * this text is relatively easy to process.
 * <p>
 * If there are multiple runs of text, information about the runs can be accessed
 * by indexing to get the start, limit, and level of a run.  The level represents
 * both the direction and the 'nesting level' of a directional run.  Odd levels
 * are right-to-left, while even levels are left-to-right.  So for example level
 * 0 represents left-to-right text, while level 1 represents right-to-left text, and
 * level 2 represents left-to-right text embedded in a right-to-left run.
 *
 * @since 1.4
 */
public final class Bidi {
    byte dir;
    byte baselevel;
    int length;
    int[] runs;
    int[] cws;

    static {
         sun.font.FontManagerNativeLibrary.load();
    }

    /** Constant indicating base direction is left-to-right. */
    public static final int DIRECTION_LEFT_TO_RIGHT = 0;

    /** Constant indicating base direction is right-to-left. */
    public static final int DIRECTION_RIGHT_TO_LEFT = 1;

    /**
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode
     * Bidirectional Algorithm.  If no strong directional character is present,
     * the base direction is left-to-right.
     */
    public static final int DIRECTION_DEFAULT_LEFT_TO_RIGHT = -2;

    /**
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode
     * Bidirectional Algorithm.  If no strong directional character is present,
     * the base direction is right-to-left.
     */
    public static final int DIRECTION_DEFAULT_RIGHT_TO_LEFT = -1;

    private static final int DIR_MIXED = 2;

    /**
     * Create Bidi from the given paragraph of text and base direction.
     * @param paragraph a paragraph of text
     * @param flags a collection of flags that control the algorithm.  The
     * algorithm understands the flags DIRECTION_LEFT_TO_RIGHT, DIRECTION_RIGHT_TO_LEFT,
     * DIRECTION_DEFAULT_LEFT_TO_RIGHT, and DIRECTION_DEFAULT_RIGHT_TO_LEFT.
     * Other values are reserved.
     */
    public Bidi(String paragraph, int flags) {
        if (paragraph == null) {
            throw new IllegalArgumentException("paragraph is null");
        }

        nativeBidiChars(this, paragraph.toCharArray(), 0, null, 0, paragraph.length(), flags);
    }

    /**
     * Create Bidi from the given paragraph of text.
     * <p>
     * The RUN_DIRECTION attribute in the text, if present, determines the base
     * direction (left-to-right or right-to-left).  If not present, the base
     * direction is computes using the Unicode Bidirectional Algorithm, defaulting to left-to-right
     * if there are no strong directional characters in the text.  This attribute, if
     * present, must be applied to all the text in the paragraph.
     * <p>
     * The BIDI_EMBEDDING attribute in the text, if present, represents embedding level
     * information.  Negative values from -1 to -62 indicate overrides at the absolute value
     * of the level.  Positive values from 1 to 62 indicate embeddings.  Where values are
     * zero or not defined, the base embedding level as determined by the base direction
     * is assumed.
     * <p>
     * The NUMERIC_SHAPING attribute in the text, if present, converts European digits to
     * other decimal digits before running the bidi algorithm.  This attribute, if present,
     * must be applied to all the text in the paragraph.
     *
     * @param paragraph a paragraph of text with optional character and paragraph attribute information
     *
     * @see TextAttribute#BIDI_EMBEDDING
     * @see TextAttribute#NUMERIC_SHAPING
     * @see TextAttribute#RUN_DIRECTION
     */
    public Bidi(AttributedCharacterIterator paragraph) {
        if (paragraph == null) {
            throw new IllegalArgumentException("paragraph is null");
        }

        int flags = DIRECTION_DEFAULT_LEFT_TO_RIGHT;
        byte[] embeddings = null;

        int start = paragraph.getBeginIndex();
        int limit = paragraph.getEndIndex();
        int length = limit - start;
        int n = 0;
        char[] text = new char[length];
        for (char c = paragraph.first(); c != paragraph.DONE; c = paragraph.next()) {
            text[n++] = c;
        }

        paragraph.first();
        try {
            Boolean runDirection = (Boolean)paragraph.getAttribute(TextAttribute.RUN_DIRECTION);
            if (runDirection != null) {
                if (TextAttribute.RUN_DIRECTION_LTR.equals(runDirection)) {
                    flags = DIRECTION_LEFT_TO_RIGHT; // clears default setting
                } else {
                    flags = DIRECTION_RIGHT_TO_LEFT;
                }
            }
        }
        catch (ClassCastException e) {
        }

        try {
            NumericShaper shaper = (NumericShaper)paragraph.getAttribute(TextAttribute.NUMERIC_SHAPING);
            if (shaper != null) {
                shaper.shape(text, 0, text.length);
            }
        }
        catch (ClassCastException e) {
        }

        int pos = start;
        do {
            paragraph.setIndex(pos);
            Object embeddingLevel = paragraph.getAttribute(TextAttribute.BIDI_EMBEDDING);
            int newpos = paragraph.getRunLimit(TextAttribute.BIDI_EMBEDDING);

            if (embeddingLevel != null) {
                try {
                    int intLevel = ((Integer)embeddingLevel).intValue();
                    if (intLevel >= -61 && intLevel < 61) {
                        byte level = (byte)(intLevel < 0 ? (-intLevel | 0x80) : intLevel);
                        if (embeddings == null) {
                            embeddings = new byte[length];
                        }
                        for (int i = pos - start; i < newpos - start; ++i) {
                            embeddings[i] = level;
                        }
                    }
                }
                catch (ClassCastException e) {
                }
            }
            pos = newpos;
        } while (pos < limit);

        nativeBidiChars(this, text, 0, embeddings, 0, text.length, flags);
    }

    /**
     * Create Bidi from the given text, embedding, and direction information.
     * The embeddings array may be null.  If present, the values represent embedding level
     * information.  Negative values from -1 to -61 indicate overrides at the absolute value
     * of the level.  Positive values from 1 to 61 indicate embeddings.  Where values are
     * zero, the base embedding level as determined by the base direction is assumed.
     * @param text an array containing the paragraph of text to process.
     * @param textStart the index into the text array of the start of the paragraph.
     * @param embeddings an array containing embedding values for each character in the paragraph.
     * This can be null, in which case it is assumed that there is no external embedding information.
     * @param embStart the index into the embedding array of the start of the paragraph.
     * @param paragraphLength the length of the paragraph in the text and embeddings arrays.
     * @param flags a collection of flags that control the algorithm.  The
     * algorithm understands the flags DIRECTION_LEFT_TO_RIGHT, DIRECTION_RIGHT_TO_LEFT,
     * DIRECTION_DEFAULT_LEFT_TO_RIGHT, and DIRECTION_DEFAULT_RIGHT_TO_LEFT.
     * Other values are reserved.
     */
    public Bidi(char[] text, int textStart, byte[] embeddings, int embStart, int paragraphLength, int flags) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        if (paragraphLength < 0) {
            throw new IllegalArgumentException("bad length: " + paragraphLength);
        }
        if (textStart < 0 || paragraphLength > text.length - textStart) {
            throw new IllegalArgumentException("bad range: " + textStart +
                                               " length: " + paragraphLength +
                                               " for text of length: " + text.length);
        }
        if (embeddings != null && (embStart < 0 || paragraphLength > embeddings.length - embStart)) {
            throw new IllegalArgumentException("bad range: " + embStart +
                                               " length: " + paragraphLength +
                                               " for embeddings of length: " + text.length);
        }

        if (embeddings != null) {
            // native uses high bit to indicate override, not negative value, sigh

            for (int i = embStart, embLimit = embStart + paragraphLength; i < embLimit; ++i) {
                if (embeddings[i] < 0) {
                    byte[] temp = new byte[paragraphLength];
                    System.arraycopy(embeddings, embStart, temp, 0, paragraphLength);

                    for (i -= embStart; i < paragraphLength; ++i) {
                        if (temp[i] < 0) {
                            temp[i] = (byte)(-temp[i] | 0x80);
                        }
                    }

                    embeddings = temp;
                    embStart = 0;
                    break;
                }
            }
        }

        nativeBidiChars(this, text, textStart, embeddings, embStart, paragraphLength, flags);
    }

    /**
     * Private constructor used by line bidi.
     */
    private Bidi(int dir, int baseLevel, int length, int[] data, int[] cws) {
        reset(dir, baseLevel, length, data, cws);
    }

    /**
     * Private mutator used by native code.
     */
    private void reset(int dir, int baselevel, int length, int[] data, int[] cws) {
        this.dir = (byte)dir;
        this.baselevel = (byte)baselevel;
        this.length = length;
        this.runs = data;
        this.cws = cws;
    }

    /**
     * Create a Bidi object representing the bidi information on a line of text within
     * the paragraph represented by the current Bidi.  This call is not required if the
     * entire paragraph fits on one line.
     * @param lineStart the offset from the start of the paragraph to the start of the line.
     * @param lineLimit the offset from the start of the paragraph to the limit of the line.
     */
    public Bidi createLineBidi(int lineStart, int lineLimit) {
        if (lineStart == 0 && lineLimit == length) {
            return this;
        }

        int lineLength = lineLimit - lineStart;
        if (lineStart < 0 ||
            lineLimit < lineStart ||
            lineLimit > length) {
            throw new IllegalArgumentException("range " + lineStart +
                                               " to " + lineLimit +
                                               " is invalid for paragraph of length " + length);
        }

        if (runs == null) {
            return new Bidi(dir, baselevel, lineLength, null, null);
        } else {
            int cwspos = -1;
            int[] ncws = null;
            if (cws != null) {
                int cwss = 0;
                int cwsl = cws.length;
                while (cwss < cwsl) {
                    if (cws[cwss] >= lineStart) {
                        cwsl = cwss;
                        while (cwsl < cws.length && cws[cwsl] < lineLimit) {
                            cwsl++;
                        }
                        int ll = lineLimit-1;
                        while (cwsl > cwss && cws[cwsl-1] == ll) {
                            cwspos = ll; // record start of counter-directional whitespace
                            --cwsl;
                            --ll;
                        }

                        if (cwspos == lineStart) { // entire line is cws, so ignore
                            return new Bidi(dir, baselevel, lineLength, null, null);
                        }

                        int ncwslen = cwsl - cwss;
                        if (ncwslen > 0) {
                            ncws = new int[ncwslen];
                            for (int i = 0; i < ncwslen; ++i) {
                                ncws[i] = cws[cwss+i] - lineStart;
                            }
                        }
                        break;
                    }
                    ++cwss;
                }
            }

            int[] nruns = null;
            int nlevel = baselevel;
            int limit = cwspos == -1 ? lineLimit : cwspos;
            int rs = 0;
            int rl = runs.length;
            int ndir = dir;
            for (; rs < runs.length; rs += 2) {
                if (runs[rs] > lineStart) {
                    rl = rs;
                    while (rl < runs.length && runs[rl] < limit) {
                        rl += 2;
                    }
                    if ((rl > rs) || (runs[rs+1] != baselevel)) {
                        rl += 2;

                        if (cwspos != -1 && rl > rs && runs[rl-1] != baselevel) { // add level for cws
                            nruns = new int[rl - rs + 2];
                            nruns[rl - rs] = lineLength;
                            nruns[rl - rs + 1] = baselevel;
                        } else {
                            limit = lineLimit;
                            nruns = new int[rl - rs];
                        }

                        int n = 0;
                        for (int i = rs; i < rl; i += 2) {
                            nruns[n++] = runs[i] - lineStart;
                            nruns[n++] = runs[i+1];
                        }
                        nruns[n-2] = limit - lineStart;
                    } else {
                        ndir = (runs[rs+1] & 0x1) == 0 ? DIRECTION_LEFT_TO_RIGHT : DIRECTION_RIGHT_TO_LEFT;
                    }
                    break;
                }
            }

            return new Bidi(ndir, baselevel, lineLength, nruns, ncws);
        }
    }

    /**
     * Return true if the line is not left-to-right or right-to-left.  This means it either has mixed runs of left-to-right
     * and right-to-left text, or the base direction differs from the direction of the only run of text.
     * @return true if the line is not left-to-right or right-to-left.
     */
    public boolean isMixed() {
        return dir == DIR_MIXED;
    }

    /**
     * Return true if the line is all left-to-right text and the base direction is left-to-right.
     * @return true if the line is all left-to-right text and the base direction is left-to-right
     */
    public boolean isLeftToRight() {
        return dir == DIRECTION_LEFT_TO_RIGHT;
    }

    /**
     * Return true if the line is all right-to-left text, and the base direction is right-to-left.
     * @return true if the line is all right-to-left text, and the base direction is right-to-left
     */
    public boolean isRightToLeft() {
        return dir == DIRECTION_RIGHT_TO_LEFT;
    }

    /**
     * Return the length of text in the line.
     * @return the length of text in the line
     */
    public int getLength() {
        return length;
    }

    /**
     * Return true if the base direction is left-to-right.
     * @return true if the base direction is left-to-right
     */
    public boolean baseIsLeftToRight() {
        return (baselevel & 0x1) == 0;
    }

    /**
     * Return the base level (0 if left-to-right, 1 if right-to-left).
     * @return the base level
     */
    public int getBaseLevel() {
      return baselevel;
    }

    /**
     * Return the resolved level of the character at offset.  If offset is <0 or >=
     * the length of the line, return the base direction level.
     * @param offset the index of the character for which to return the level
     * @return the resolved level of the character at offset
     */
    public int getLevelAt(int offset) {
        if (runs == null || offset < 0 || offset >= length) {
            return baselevel;
        } else {
            int i = 0;
            do {
                if (offset < runs[i]) {
                    return runs[i+1];
                }
                i += 2;
            } while (true);
        }
    }

    /**
     * Return the number of level runs.
     * @return the number of level runs
     */
    public int getRunCount() {
        return runs == null ? 1 : runs.length / 2;
    }

    /**
     * Return the level of the nth logical run in this line.
     * @param run the index of the run, between 0 and <code>getRunCount()</code>
     * @return the level of the run
     */
    public int getRunLevel(int run) {
        return runs == null ? baselevel : runs[run * 2 + 1];
    }

    /**
     * Return the index of the character at the start of the nth logical run in this line, as
     * an offset from the start of the line.
     * @param run the index of the run, between 0 and <code>getRunCount()</code>
     * @return the start of the run
     */
    public int getRunStart(int run) {
        return (runs == null || run == 0) ? 0 : runs[run * 2 - 2];
    }

    /**
     * Return the index of the character past the end of the nth logical run in this line, as
     * an offset from the start of the line.  For example, this will return the length
     * of the line for the last run on the line.
     * @param run the index of the run, between 0 and <code>getRunCount()</code>
     * @return limit the limit of the run
     */
    public int getRunLimit(int run) {
        return runs == null ? length : runs[run * 2];
    }

    /**
     * Return true if the specified text requires bidi analysis.  If this returns false,
     * the text will display left-to-right.  Clients can then avoid constructing a Bidi object.
     * Text in the Arabic Presentation Forms area of Unicode is presumed to already be shaped
     * and ordered for display, and so will not cause this function to return true.
     *
     * @param text the text containing the characters to test
     * @param start the start of the range of characters to test
     * @param limit the limit of the range of characters to test
     * @return true if the range of characters requires bidi analysis
     */
    public static boolean requiresBidi(char[] text, int start, int limit) {
        CodePointIterator cpi = CodePointIterator.create(text, start, limit);
        for (int cp = cpi.next(); cp != CodePointIterator.DONE; cp = cpi.next()) {
            if (cp > 0x0590) {
                int dc = nativeGetDirectionCode(cp);
                if ((RMASK & (1 << dc)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reorder the objects in the array into visual order based on their levels.
     * This is a utility function to use when you have a collection of objects
     * representing runs of text in logical order, each run containing text
     * at a single level.  The elements at <code>index</code> from
     * <code>objectStart</code> up to <code>objectStart + count</code>
     * in the objects array will be reordered into visual order assuming
     * each run of text has the level indicated by the corresponding element
     * in the levels array (at <code>index - objectStart + levelStart</code>).
     *
     * @param levels an array representing the bidi level of each object
     * @param levelStart the start position in the levels array
     * @param objects the array of objects to be reordered into visual order
     * @param objectStart the start position in the objects array
     * @param count the number of objects to reorder
     */
    public static void reorderVisually(byte[] levels, int levelStart, Object[] objects, int objectStart, int count) {

        if (count < 0) {
            throw new IllegalArgumentException("count " + count + " must be >= 0");
        }
        if (levelStart < 0 || levelStart + count > levels.length) {
            throw new IllegalArgumentException("levelStart " + levelStart + " and count " + count +
                                               " out of range [0, " + levels.length + "]");
        }
        if (objectStart < 0 || objectStart + count > objects.length) {
            throw new IllegalArgumentException("objectStart " + objectStart + " and count " + count +
                                               " out of range [0, " + objects.length + "]");
        }

        byte lowestOddLevel = (byte)(NUMLEVELS + 1);
        byte highestLevel = 0;

        // initialize mapping and levels

        int levelLimit = levelStart + count;
        for (int i = levelStart; i < levelLimit; i++) {
            byte level = levels[i];
            if (level > highestLevel) {
                highestLevel = level;
            }

            if ((level & 0x01) != 0 && level < lowestOddLevel) {
                lowestOddLevel = level;
            }
        }

        int delta = objectStart - levelStart;

        while (highestLevel >= lowestOddLevel) {
            int i = levelStart;

            for (;;) {
                while (i < levelLimit && levels[i] < highestLevel) {
                    i++;
                }
                int begin = i++;

                if (begin == levelLimit) {
                    break; // no more runs at this level
                }

                while (i < levelLimit && levels[i] >= highestLevel) {
                    i++;
                }
                int end = i - 1;

                begin += delta;
                end += delta;
                while (begin < end) {
                    Object temp = objects[begin];
                    objects[begin] = objects[end];
                    objects[end] = temp;
                    ++begin;
                    --end;
                }
            }

            --highestLevel;
        }
    }

    private static final char NUMLEVELS = 62;

    private static final int RMASK =
        (1 << 1 /* U_RIGHT_TO_LEFT */) |
        (1 << 5 /* U_ARABIC_NUMBER */) |
        (1 << 13 /* U_RIGHT_TO_LEFT_ARABIC */) |
        (1 << 14 /* U_RIGHT_TO_LEFT_EMBEDDING */) |
        (1 << 15 /* U_RIGHT_TO_LEFT_OVERRIDE */);

    /** Access native bidi implementation. */
    private static native int nativeGetDirectionCode(int cp);

    /** Access native bidi implementation. */
    private static synchronized native void nativeBidiChars(Bidi bidi, char[] text, int textStart,
                                                            byte[] embeddings, int embeddingStart,
                                                            int length, int flags);

    /**
     * Display the bidi internal state, used in debugging.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer(super.toString());
        buf.append("[dir: " + dir);
        buf.append(" baselevel: " + baselevel);
        buf.append(" length: " + length);
        if (runs == null) {
            buf.append(" runs: null");
        } else {
            buf.append(" runs: [");
            for (int i = 0; i < runs.length; i += 2) {
                if (i != 0) {
                    buf.append(' ');
                }
                buf.append(runs[i]); // limit
                buf.append('/');
                buf.append(runs[i+1]); // level
            }
            buf.append(']');
        }
        if (cws == null) {
            buf.append(" cws: null");
        } else {
            buf.append(" cws: [");
            for (int i = 0; i < cws.length; ++i) {
                if (i != 0) {
                    buf.append(' ');
                }
                buf.append(Integer.toHexString(cws[i]));
            }
            buf.append(']');
        }
        buf.append(']');

        return buf.toString();
    }
}
