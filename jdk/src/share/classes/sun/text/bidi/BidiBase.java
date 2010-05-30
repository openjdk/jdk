/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************
 * (C) Copyright IBM Corp. and others, 1996-2009 - All Rights Reserved         *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

/* FOOD FOR THOUGHT: currently the reordering modes are a mixture of
 * algorithm for direct BiDi, algorithm for inverse Bidi and the bizarre
 * concept of RUNS_ONLY which is a double operation.
 * It could be advantageous to divide this into 3 concepts:
 * a) Operation: direct / inverse / RUNS_ONLY
 * b) Direct algorithm: default / NUMBERS_SPECIAL / GROUP_NUMBERS_WITH_L
 * c) Inverse algorithm: default / INVERSE_LIKE_DIRECT / NUMBERS_SPECIAL
 * This would allow combinations not possible today like RUNS_ONLY with
 * NUMBERS_SPECIAL.
 * Also allow to set INSERT_MARKS for the direct step of RUNS_ONLY and
 * REMOVE_CONTROLS for the inverse step.
 * Not all combinations would be supported, and probably not all do make sense.
 * This would need to document which ones are supported and what are the
 * fallbacks for unsupported combinations.
 */

package sun.text.bidi;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.text.AttributedCharacterIterator;
import java.text.Bidi;
import java.util.Arrays;
import java.util.MissingResourceException;
import sun.text.normalizer.UBiDiProps;
import sun.text.normalizer.UCharacter;
import sun.text.normalizer.UTF16;

/**
 *
 * <h2>Bidi algorithm for ICU</h2>
 *
 * This is an implementation of the Unicode Bidirectional algorithm. The
 * algorithm is defined in the <a
 * href="http://www.unicode.org/unicode/reports/tr9/">Unicode Standard Annex #9</a>,
 * version 13, also described in The Unicode Standard, Version 4.0 .
 * <p>
 *
 * Note: Libraries that perform a bidirectional algorithm and reorder strings
 * accordingly are sometimes called "Storage Layout Engines". ICU's Bidi and
 * shaping (ArabicShaping) classes can be used at the core of such "Storage
 * Layout Engines".
 *
 * <h3>General remarks about the API:</h3>
 *
 * The &quot;limit&quot; of a sequence of characters is the position just after
 * their last character, i.e., one more than that position.
 * <p>
 *
 * Some of the API methods provide access to &quot;runs&quot;. Such a
 * &quot;run&quot; is defined as a sequence of characters that are at the same
 * embedding level after performing the Bidi algorithm.
 * <p>
 *
 * <h3>Basic concept: paragraph</h3>
 * A piece of text can be divided into several paragraphs by characters
 * with the Bidi class <code>Block Separator</code>. For handling of
 * paragraphs, see:
 * <ul>
 * <li>{@link #countParagraphs}
 * <li>{@link #getParaLevel}
 * <li>{@link #getParagraph}
 * <li>{@link #getParagraphByIndex}
 * </ul>
 *
 * <h3>Basic concept: text direction</h3>
 * The direction of a piece of text may be:
 * <ul>
 * <li>{@link #LTR}
 * <li>{@link #RTL}
 * <li>{@link #MIXED}
 * </ul>
 *
 * <h3>Basic concept: levels</h3>
 *
 * Levels in this API represent embedding levels according to the Unicode
 * Bidirectional Algorithm.
 * Their low-order bit (even/odd value) indicates the visual direction.<p>
 *
 * Levels can be abstract values when used for the
 * <code>paraLevel</code> and <code>embeddingLevels</code>
 * arguments of <code>setPara()</code>; there:
 * <ul>
 * <li>the high-order bit of an <code>embeddingLevels[]</code>
 * value indicates whether the using application is
 * specifying the level of a character to <i>override</i> whatever the
 * Bidi implementation would resolve it to.</li>
 * <li><code>paraLevel</code> can be set to the
 * pseudo-level values <code>LEVEL_DEFAULT_LTR</code>
 * and <code>LEVEL_DEFAULT_RTL</code>.</li>
 * </ul>
 *
 * <p>The related constants are not real, valid level values.
 * <code>DEFAULT_XXX</code> can be used to specify
 * a default for the paragraph level for
 * when the <code>setPara()</code> method
 * shall determine it but there is no
 * strongly typed character in the input.<p>
 *
 * Note that the value for <code>LEVEL_DEFAULT_LTR</code> is even
 * and the one for <code>LEVEL_DEFAULT_RTL</code> is odd,
 * just like with normal LTR and RTL level values -
 * these special values are designed that way. Also, the implementation
 * assumes that MAX_EXPLICIT_LEVEL is odd.
 *
 * <ul><b>See Also:</b>
 * <li>{@link #LEVEL_DEFAULT_LTR}
 * <li>{@link #LEVEL_DEFAULT_RTL}
 * <li>{@link #LEVEL_OVERRIDE}
 * <li>{@link #MAX_EXPLICIT_LEVEL}
 * <li>{@link #setPara}
 * </ul>
 *
 * <h3>Basic concept: Reordering Mode</h3>
 * Reordering mode values indicate which variant of the Bidi algorithm to
 * use.
 *
 * <ul><b>See Also:</b>
 * <li>{@link #setReorderingMode}
 * <li>{@link #REORDER_DEFAULT}
 * <li>{@link #REORDER_NUMBERS_SPECIAL}
 * <li>{@link #REORDER_GROUP_NUMBERS_WITH_R}
 * <li>{@link #REORDER_RUNS_ONLY}
 * <li>{@link #REORDER_INVERSE_NUMBERS_AS_L}
 * <li>{@link #REORDER_INVERSE_LIKE_DIRECT}
 * <li>{@link #REORDER_INVERSE_FOR_NUMBERS_SPECIAL}
 * </ul>
 *
 * <h3>Basic concept: Reordering Options</h3>
 * Reordering options can be applied during Bidi text transformations.
 * <ul><b>See Also:</b>
 * <li>{@link #setReorderingOptions}
 * <li>{@link #OPTION_DEFAULT}
 * <li>{@link #OPTION_INSERT_MARKS}
 * <li>{@link #OPTION_REMOVE_CONTROLS}
 * <li>{@link #OPTION_STREAMING}
 * </ul>
 *
 *
 * @author Simon Montagu, Matitiahu Allouche (ported from C code written by Markus W. Scherer)
 * @stable ICU 3.8
 *
 *
 * <h4> Sample code for the ICU Bidi API </h4>
 *
 * <h5>Rendering a paragraph with the ICU Bidi API</h5>
 *
 * This is (hypothetical) sample code that illustrates how the ICU Bidi API
 * could be used to render a paragraph of text. Rendering code depends highly on
 * the graphics system, therefore this sample code must make a lot of
 * assumptions, which may or may not match any existing graphics system's
 * properties.
 *
 * <p>
 * The basic assumptions are:
 * </p>
 * <ul>
 * <li>Rendering is done from left to right on a horizontal line.</li>
 * <li>A run of single-style, unidirectional text can be rendered at once.
 * </li>
 * <li>Such a run of text is passed to the graphics system with characters
 * (code units) in logical order.</li>
 * <li>The line-breaking algorithm is very complicated and Locale-dependent -
 * and therefore its implementation omitted from this sample code.</li>
 * </ul>
 *
 * <pre>
 *
 *  package com.ibm.icu.dev.test.bidi;
 *
 *  import com.ibm.icu.text.Bidi;
 *  import com.ibm.icu.text.BidiRun;
 *
 *  public class Sample {
 *
 *      static final int styleNormal = 0;
 *      static final int styleSelected = 1;
 *      static final int styleBold = 2;
 *      static final int styleItalics = 4;
 *      static final int styleSuper=8;
 *      static final int styleSub = 16;
 *
 *      static class StyleRun {
 *          int limit;
 *          int style;
 *
 *          public StyleRun(int limit, int style) {
 *              this.limit = limit;
 *              this.style = style;
 *          }
 *      }
 *
 *      static class Bounds {
 *          int start;
 *          int limit;
 *
 *          public Bounds(int start, int limit) {
 *              this.start = start;
 *              this.limit = limit;
 *          }
 *      }
 *
 *      static int getTextWidth(String text, int start, int limit,
 *                              StyleRun[] styleRuns, int styleRunCount) {
 *          // simplistic way to compute the width
 *          return limit - start;
 *      }
 *
 *      // set limit and StyleRun limit for a line
 *      // from text[start] and from styleRuns[styleRunStart]
 *      // using Bidi.getLogicalRun(...)
 *      // returns line width
 *      static int getLineBreak(String text, Bounds line, Bidi para,
 *                              StyleRun styleRuns[], Bounds styleRun) {
 *          // dummy return
 *          return 0;
 *      }
 *
 *      // render runs on a line sequentially, always from left to right
 *
 *      // prepare rendering a new line
 *      static void startLine(byte textDirection, int lineWidth) {
 *          System.out.println();
 *      }
 *
 *      // render a run of text and advance to the right by the run width
 *      // the text[start..limit-1] is always in logical order
 *      static void renderRun(String text, int start, int limit,
 *                            byte textDirection, int style) {
 *      }
 *
 *      // We could compute a cross-product
 *      // from the style runs with the directional runs
 *      // and then reorder it.
 *      // Instead, here we iterate over each run type
 *      // and render the intersections -
 *      // with shortcuts in simple (and common) cases.
 *      // renderParagraph() is the main function.
 *
 *      // render a directional run with
 *      // (possibly) multiple style runs intersecting with it
 *      static void renderDirectionalRun(String text, int start, int limit,
 *                                       byte direction, StyleRun styleRuns[],
 *                                       int styleRunCount) {
 *          int i;
 *
 *          // iterate over style runs
 *          if (direction == Bidi.LTR) {
 *              int styleLimit;
 *              for (i = 0; i < styleRunCount; ++i) {
 *                  styleLimit = styleRuns[i].limit;
 *                  if (start < styleLimit) {
 *                      if (styleLimit > limit) {
 *                          styleLimit = limit;
 *                      }
 *                      renderRun(text, start, styleLimit,
 *                                direction, styleRuns[i].style);
 *                      if (styleLimit == limit) {
 *                          break;
 *                      }
 *                      start = styleLimit;
 *                  }
 *              }
 *          } else {
 *              int styleStart;
 *
 *              for (i = styleRunCount-1; i >= 0; --i) {
 *                  if (i > 0) {
 *                      styleStart = styleRuns[i-1].limit;
 *                  } else {
 *                      styleStart = 0;
 *                  }
 *                  if (limit >= styleStart) {
 *                      if (styleStart < start) {
 *                          styleStart = start;
 *                      }
 *                      renderRun(text, styleStart, limit, direction,
 *                                styleRuns[i].style);
 *                      if (styleStart == start) {
 *                          break;
 *                      }
 *                      limit = styleStart;
 *                  }
 *              }
 *          }
 *      }
 *
 *      // the line object represents text[start..limit-1]
 *      static void renderLine(Bidi line, String text, int start, int limit,
 *                             StyleRun styleRuns[], int styleRunCount) {
 *          byte direction = line.getDirection();
 *          if (direction != Bidi.MIXED) {
 *              // unidirectional
 *              if (styleRunCount <= 1) {
 *                  renderRun(text, start, limit, direction, styleRuns[0].style);
 *              } else {
 *                  renderDirectionalRun(text, start, limit, direction,
 *                                       styleRuns, styleRunCount);
 *              }
 *          } else {
 *              // mixed-directional
 *              int count, i;
 *              BidiRun run;
 *
 *              try {
 *                  count = line.countRuns();
 *              } catch (IllegalStateException e) {
 *                  e.printStackTrace();
 *                  return;
 *              }
 *              if (styleRunCount <= 1) {
 *                  int style = styleRuns[0].style;
 *
 *                  // iterate over directional runs
 *                  for (i = 0; i < count; ++i) {
 *                      run = line.getVisualRun(i);
 *                      renderRun(text, run.getStart(), run.getLimit(),
 *                                run.getDirection(), style);
 *                  }
 *              } else {
 *                  // iterate over both directional and style runs
 *                  for (i = 0; i < count; ++i) {
 *                      run = line.getVisualRun(i);
 *                      renderDirectionalRun(text, run.getStart(),
 *                                           run.getLimit(), run.getDirection(),
 *                                           styleRuns, styleRunCount);
 *                  }
 *              }
 *          }
 *      }
 *
 *      static void renderParagraph(String text, byte textDirection,
 *                                  StyleRun styleRuns[], int styleRunCount,
 *                                  int lineWidth) {
 *          int length = text.length();
 *          Bidi para = new Bidi();
 *          try {
 *              para.setPara(text,
 *                           textDirection != 0 ? Bidi.LEVEL_DEFAULT_RTL
 *                                              : Bidi.LEVEL_DEFAULT_LTR,
 *                           null);
 *          } catch (Exception e) {
 *              e.printStackTrace();
 *              return;
 *          }
 *          byte paraLevel = (byte)(1 & para.getParaLevel());
 *          StyleRun styleRun = new StyleRun(length, styleNormal);
 *
 *          if (styleRuns == null || styleRunCount <= 0) {
 *              styleRuns = new StyleRun[1];
 *              styleRunCount = 1;
 *              styleRuns[0] = styleRun;
 *          }
 *          // assume styleRuns[styleRunCount-1].limit>=length
 *
 *          int width = getTextWidth(text, 0, length, styleRuns, styleRunCount);
 *          if (width <= lineWidth) {
 *              // everything fits onto one line
 *
 *              // prepare rendering a new line from either left or right
 *              startLine(paraLevel, width);
 *
 *              renderLine(para, text, 0, length, styleRuns, styleRunCount);
 *          } else {
 *              // we need to render several lines
 *              Bidi line = new Bidi(length, 0);
 *              int start = 0, limit;
 *              int styleRunStart = 0, styleRunLimit;
 *
 *              for (;;) {
 *                  limit = length;
 *                  styleRunLimit = styleRunCount;
 *                  width = getLineBreak(text, new Bounds(start, limit),
 *                                       para, styleRuns,
 *                                       new Bounds(styleRunStart, styleRunLimit));
 *                  try {
 *                      line = para.setLine(start, limit);
 *                  } catch (Exception e) {
 *                      e.printStackTrace();
 *                      return;
 *                  }
 *                  // prepare rendering a new line
 *                  // from either left or right
 *                  startLine(paraLevel, width);
 *
 *                  if (styleRunStart > 0) {
 *                      int newRunCount = styleRuns.length - styleRunStart;
 *                      StyleRun[] newRuns = new StyleRun[newRunCount];
 *                      System.arraycopy(styleRuns, styleRunStart, newRuns, 0,
 *                                       newRunCount);
 *                      renderLine(line, text, start, limit, newRuns,
 *                                 styleRunLimit - styleRunStart);
 *                  } else {
 *                      renderLine(line, text, start, limit, styleRuns,
 *                                 styleRunLimit - styleRunStart);
 *                  }
 *                  if (limit == length) {
 *                      break;
 *                  }
 *                  start = limit;
 *                  styleRunStart = styleRunLimit - 1;
 *                  if (start >= styleRuns[styleRunStart].limit) {
 *                      ++styleRunStart;
 *                  }
 *              }
 *          }
 *      }
 *
 *      public static void main(String[] args)
 *      {
 *          renderParagraph("Some Latin text...", Bidi.LTR, null, 0, 80);
 *          renderParagraph("Some Hebrew text...", Bidi.RTL, null, 0, 60);
 *      }
 *  }
 *
 * </pre>
 */

public class BidiBase {

    class Point {
        int pos;    /* position in text */
        int flag;   /* flag for LRM/RLM, before/after */
    }

    class InsertPoints {
        int size;
        int confirmed;
        Point[] points = new Point[0];
    }

    /** Paragraph level setting<p>
     *
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode Bidirectional
     * Algorithm. If no strong directional character is present,
     * then set the paragraph level to 0 (left-to-right).<p>
     *
     * If this value is used in conjunction with reordering modes
     * <code>REORDER_INVERSE_LIKE_DIRECT</code> or
     * <code>REORDER_INVERSE_FOR_NUMBERS_SPECIAL</code>, the text to reorder
     * is assumed to be visual LTR, and the text after reordering is required
     * to be the corresponding logical string with appropriate contextual
     * direction. The direction of the result string will be RTL if either
     * the righmost or leftmost strong character of the source text is RTL
     * or Arabic Letter, the direction will be LTR otherwise.<p>
     *
     * If reordering option <code>OPTION_INSERT_MARKS</code> is set, an RLM may
     * be added at the beginning of the result string to ensure round trip
     * (that the result string, when reordered back to visual, will produce
     * the original source text).
     * @see #REORDER_INVERSE_LIKE_DIRECT
     * @see #REORDER_INVERSE_FOR_NUMBERS_SPECIAL
     * @stable ICU 3.8
     */
    public static final byte INTERNAL_LEVEL_DEFAULT_LTR = (byte)0x7e;

    /** Paragraph level setting<p>
     *
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode Bidirectional
     * Algorithm. If no strong directional character is present,
     * then set the paragraph level to 1 (right-to-left).<p>
     *
     * If this value is used in conjunction with reordering modes
     * <code>REORDER_INVERSE_LIKE_DIRECT</code> or
     * <code>REORDER_INVERSE_FOR_NUMBERS_SPECIAL</code>, the text to reorder
     * is assumed to be visual LTR, and the text after reordering is required
     * to be the corresponding logical string with appropriate contextual
     * direction. The direction of the result string will be RTL if either
     * the righmost or leftmost strong character of the source text is RTL
     * or Arabic Letter, or if the text contains no strong character;
     * the direction will be LTR otherwise.<p>
     *
     * If reordering option <code>OPTION_INSERT_MARKS</code> is set, an RLM may
     * be added at the beginning of the result string to ensure round trip
     * (that the result string, when reordered back to visual, will produce
     * the original source text).
     * @see #REORDER_INVERSE_LIKE_DIRECT
     * @see #REORDER_INVERSE_FOR_NUMBERS_SPECIAL
     * @stable ICU 3.8
     */
    public static final byte INTERNAL_LEVEL_DEFAULT_RTL = (byte)0x7f;

    /**
     * Maximum explicit embedding level.
     * (The maximum resolved level can be up to <code>MAX_EXPLICIT_LEVEL+1</code>).
     * @stable ICU 3.8
     */
    public static final byte MAX_EXPLICIT_LEVEL = 61;

    /**
     * Bit flag for level input.
     * Overrides directional properties.
     * @stable ICU 3.8
     */
    public static final byte INTERNAL_LEVEL_OVERRIDE = (byte)0x80;

    /**
     * Special value which can be returned by the mapping methods when a
     * logical index has no corresponding visual index or vice-versa. This may
     * happen for the logical-to-visual mapping of a Bidi control when option
     * <code>OPTION_REMOVE_CONTROLS</code> is
     * specified. This can also happen for the visual-to-logical mapping of a
     * Bidi mark (LRM or RLM) inserted by option
     * <code>OPTION_INSERT_MARKS</code>.
     * @see #getVisualIndex
     * @see #getVisualMap
     * @see #getLogicalIndex
     * @see #getLogicalMap
     * @see #OPTION_INSERT_MARKS
     * @see #OPTION_REMOVE_CONTROLS
     * @stable ICU 3.8
     */
    public static final int MAP_NOWHERE = -1;

    /**
     * Mixed-directional text.
     * @stable ICU 3.8
     */
    public static final byte MIXED = 2;

    /**
     * option bit for writeReordered():
     * replace characters with the "mirrored" property in RTL runs
     * by their mirror-image mappings
     *
     * @see #writeReordered
     * @stable ICU 3.8
     */
    public static final short DO_MIRRORING = 2;

    /** Reordering mode: Regular Logical to Visual Bidi algorithm according to Unicode.
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_DEFAULT = 0;

    /** Reordering mode: Logical to Visual algorithm which handles numbers in
     * a way which mimicks the behavior of Windows XP.
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_NUMBERS_SPECIAL = 1;

    /** Reordering mode: Logical to Visual algorithm grouping numbers with
     * adjacent R characters (reversible algorithm).
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_GROUP_NUMBERS_WITH_R = 2;

    /** Reordering mode: Reorder runs only to transform a Logical LTR string
     * to the logical RTL string with the same display, or vice-versa.<br>
     * If this mode is set together with option
     * <code>OPTION_INSERT_MARKS</code>, some Bidi controls in the source
     * text may be removed and other controls may be added to produce the
     * minimum combination which has the required display.
     * @see #OPTION_INSERT_MARKS
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_RUNS_ONLY = 3;

    /** Reordering mode: Visual to Logical algorithm which handles numbers
     * like L (same algorithm as selected by <code>setInverse(true)</code>.
     * @see #setInverse
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_INVERSE_NUMBERS_AS_L = 4;

    /** Reordering mode: Visual to Logical algorithm equivalent to the regular
     * Logical to Visual algorithm.
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_INVERSE_LIKE_DIRECT = 5;

    /** Reordering mode: Inverse Bidi (Visual to Logical) algorithm for the
     * <code>REORDER_NUMBERS_SPECIAL</code> Bidi algorithm.
     * @see #setReorderingMode
     * @stable ICU 3.8
     */
    private static final short REORDER_INVERSE_FOR_NUMBERS_SPECIAL = 6;

    /* Reordering mode values must be ordered so that all the regular logical to
     * visual modes come first, and all inverse Bidi modes come last.
     */
    private static final short REORDER_LAST_LOGICAL_TO_VISUAL =
            REORDER_NUMBERS_SPECIAL;

    /**
     * Option bit for <code>setReorderingOptions</code>:
     * insert Bidi marks (LRM or RLM) when needed to ensure correct result of
     * a reordering to a Logical order
     *
     * <p>This option must be set or reset before calling
     * <code>setPara</code>.</p>
     *
     * <p>This option is significant only with reordering modes which generate
     * a result with Logical order, specifically.</p>
     * <ul>
     *   <li><code>REORDER_RUNS_ONLY</code></li>
     *   <li><code>REORDER_INVERSE_NUMBERS_AS_L</code></li>
     *   <li><code>REORDER_INVERSE_LIKE_DIRECT</code></li>
     *   <li><code>REORDER_INVERSE_FOR_NUMBERS_SPECIAL</code></li>
     * </ul>
     *
     * <p>If this option is set in conjunction with reordering mode
     * <code>REORDER_INVERSE_NUMBERS_AS_L</code> or with calling
     * <code>setInverse(true)</code>, it implies option
     * <code>INSERT_LRM_FOR_NUMERIC</code> in calls to method
     * <code>writeReordered()</code>.</p>
     *
     * <p>For other reordering modes, a minimum number of LRM or RLM characters
     * will be added to the source text after reordering it so as to ensure
     * round trip, i.e. when applying the inverse reordering mode on the
     * resulting logical text with removal of Bidi marks
     * (option <code>OPTION_REMOVE_CONTROLS</code> set before calling
     * <code>setPara()</code> or option
     * <code>REMOVE_BIDI_CONTROLS</code> in
     * <code>writeReordered</code>), the result will be identical to the
     * source text in the first transformation.
     *
     * <p>This option will be ignored if specified together with option
     * <code>OPTION_REMOVE_CONTROLS</code>. It inhibits option
     * <code>REMOVE_BIDI_CONTROLS</code> in calls to method
     * <code>writeReordered()</code> and it implies option
     * <code>INSERT_LRM_FOR_NUMERIC</code> in calls to method
     * <code>writeReordered()</code> if the reordering mode is
     * <code>REORDER_INVERSE_NUMBERS_AS_L</code>.</p>
     *
     * @see #setReorderingMode
     * @see #setReorderingOptions
     * @see #INSERT_LRM_FOR_NUMERIC
     * @see #REMOVE_BIDI_CONTROLS
     * @see #OPTION_REMOVE_CONTROLS
     * @see #REORDER_RUNS_ONLY
     * @see #REORDER_INVERSE_NUMBERS_AS_L
     * @see #REORDER_INVERSE_LIKE_DIRECT
     * @see #REORDER_INVERSE_FOR_NUMBERS_SPECIAL
     * @stable ICU 3.8
     */
    private static final int OPTION_INSERT_MARKS = 1;

    /**
     * Option bit for <code>setReorderingOptions</code>:
     * remove Bidi control characters
     *
     * <p>This option must be set or reset before calling
     * <code>setPara</code>.</p>
     *
     * <p>This option nullifies option
     * <code>OPTION_INSERT_MARKS</code>. It inhibits option
     * <code>INSERT_LRM_FOR_NUMERIC</code> in calls to method
     * <code>writeReordered()</code> and it implies option
     * <code>REMOVE_BIDI_CONTROLS</code> in calls to that method.</p>
     *
     * @see #setReorderingMode
     * @see #setReorderingOptions
     * @see #OPTION_INSERT_MARKS
     * @see #INSERT_LRM_FOR_NUMERIC
     * @see #REMOVE_BIDI_CONTROLS
     * @stable ICU 3.8
     */
    private static final int OPTION_REMOVE_CONTROLS = 2;

    /**
     * Option bit for <code>setReorderingOptions</code>:
     * process the output as part of a stream to be continued
     *
     * <p>This option must be set or reset before calling
     * <code>setPara</code>.</p>
     *
     * <p>This option specifies that the caller is interested in processing
     * large text object in parts. The results of the successive calls are
     * expected to be concatenated by the caller. Only the call for the last
     * part will have this option bit off.</p>
     *
     * <p>When this option bit is on, <code>setPara()</code> may process
     * less than the full source text in order to truncate the text at a
     * meaningful boundary. The caller should call
     * <code>getProcessedLength()</code> immediately after calling
     * <code>setPara()</code> in order to determine how much of the source
     * text has been processed. Source text beyond that length should be
     * resubmitted in following calls to <code>setPara</code>. The
     * processed length may be less than the length of the source text if a
     * character preceding the last character of the source text constitutes a
     * reasonable boundary (like a block separator) for text to be continued.<br>
     * If the last character of the source text constitutes a reasonable
     * boundary, the whole text will be processed at once.<br>
     * If nowhere in the source text there exists
     * such a reasonable boundary, the processed length will be zero.<br>
     * The caller should check for such an occurrence and do one of the following:
     * <ul><li>submit a larger amount of text with a better chance to include
     *         a reasonable boundary.</li>
     *     <li>resubmit the same text after turning off option
     *         <code>OPTION_STREAMING</code>.</li></ul>
     * In all cases, this option should be turned off before processing the last
     * part of the text.</p>
     *
     * <p>When the <code>OPTION_STREAMING</code> option is used, it is
     * recommended to call <code>orderParagraphsLTR()</code> with argument
     * <code>orderParagraphsLTR</code> set to <code>true</code> before calling
     * <code>setPara()</code> so that later paragraphs may be concatenated to
     * previous paragraphs on the right.
     * </p>
     *
     * @see #setReorderingMode
     * @see #setReorderingOptions
     * @see #getProcessedLength
     * @see #orderParagraphsLTR
     * @stable ICU 3.8
     */
    private static final int OPTION_STREAMING = 4;

    /*
     *   Comparing the description of the Bidi algorithm with this implementation
     *   is easier with the same names for the Bidi types in the code as there.
     *   See UCharacterDirection
     */
    private static final byte L   = 0;
    private static final byte R   = 1;
    private static final byte EN  = 2;
    private static final byte ES  = 3;
    private static final byte ET  = 4;
    private static final byte AN  = 5;
    private static final byte CS  = 6;
    static final byte B   = 7;
    private static final byte S   = 8;
    private static final byte WS  = 9;
    private static final byte ON  = 10;
    private static final byte LRE = 11;
    private static final byte LRO = 12;
    private static final byte AL  = 13;
    private static final byte RLE = 14;
    private static final byte RLO = 15;
    private static final byte PDF = 16;
    private static final byte NSM = 17;
    private static final byte BN  = 18;

    private static final int MASK_R_AL = (1 << R | 1 << AL);

    private static final char CR = '\r';
    private static final char LF = '\n';

    static final int LRM_BEFORE = 1;
    static final int LRM_AFTER = 2;
    static final int RLM_BEFORE = 4;
    static final int RLM_AFTER = 8;

    /*
     * reference to parent paragraph object (reference to self if this object is
     * a paragraph object); set to null in a newly opened object; set to a
     * real value after a successful execution of setPara or setLine
     */
    BidiBase                paraBidi;

    final UBiDiProps    bdp;

    /* character array representing the current text */
    char[]              text;

    /* length of the current text */
    int                 originalLength;

    /* if the option OPTION_STREAMING is set, this is the length of
     * text actually processed by <code>setPara</code>, which may be shorter
     * than the original length. Otherwise, it is identical to the original
     * length.
     */
    public int                 length;

    /* if option OPTION_REMOVE_CONTROLS is set, and/or Bidi
     * marks are allowed to be inserted in one of the reordering modes, the
     * length of the result string may be different from the processed length.
     */
    int                 resultLength;

    /* indicators for whether memory may be allocated after construction */
    boolean             mayAllocateText;
    boolean             mayAllocateRuns;

    /* arrays with one value per text-character */
    byte[]              dirPropsMemory = new byte[1];
    byte[]              levelsMemory = new byte[1];
    byte[]              dirProps;
    byte[]              levels;

    /* must block separators receive level 0? */
    boolean             orderParagraphsLTR;

    /* the paragraph level */
    byte                paraLevel;

    /* original paraLevel when contextual */
    /* must be one of DEFAULT_xxx or 0 if not contextual */
    byte                defaultParaLevel;

    /* the following is set in setPara, used in processPropertySeq */

    ImpTabPair          impTabPair;  /* reference to levels state table pair */

    /* the overall paragraph or line directionality*/
    byte                direction;

    /* flags is a bit set for which directional properties are in the text */
    int                 flags;

    /* lastArabicPos is index to the last AL in the text, -1 if none */
    int                 lastArabicPos;

    /* characters after trailingWSStart are WS and are */
    /* implicitly at the paraLevel (rule (L1)) - levels may not reflect that */
    int                 trailingWSStart;

    /* fields for paragraph handling */
    int                 paraCount;       /* set in getDirProps() */
    int[]               parasMemory = new int[1];
    int[]               paras;           /* limits of paragraphs, filled in
                                          ResolveExplicitLevels() or CheckExplicitLevels() */

    /* for single paragraph text, we only need a tiny array of paras (no allocation) */
    int[]               simpleParas = {0};

    /* fields for line reordering */
    int                 runCount;     /* ==-1: runs not set up yet */
    BidiRun[]           runsMemory = new BidiRun[0];
    BidiRun[]           runs;

    /* for non-mixed text, we only need a tiny array of runs (no allocation) */
    BidiRun[]           simpleRuns = {new BidiRun()};

    /* mapping of runs in logical order to visual order */
    int[]               logicalToVisualRunsMap;

    /* flag to indicate that the map has been updated */
    boolean             isGoodLogicalToVisualRunsMap;

    /* for inverse Bidi with insertion of directional marks */
    InsertPoints        insertPoints = new InsertPoints();

    /* for option OPTION_REMOVE_CONTROLS */
    int                 controlCount;

    /*
     * Sometimes, bit values are more appropriate
     * to deal with directionality properties.
     * Abbreviations in these method names refer to names
     * used in the Bidi algorithm.
     */
    static int DirPropFlag(byte dir) {
        return (1 << dir);
    }

    /*
     * The following bit is ORed to the property of characters in paragraphs
     * with contextual RTL direction when paraLevel is contextual.
     */
    static final byte CONTEXT_RTL_SHIFT = 6;
    static final byte CONTEXT_RTL = (byte)(1<<CONTEXT_RTL_SHIFT);   // 0x40
    static byte NoContextRTL(byte dir)
    {
        return (byte)(dir & ~CONTEXT_RTL);
    }

    /*
     * The following is a variant of DirProp.DirPropFlag() which ignores the
     * CONTEXT_RTL bit.
     */
    static int DirPropFlagNC(byte dir) {
        return (1<<(dir & ~CONTEXT_RTL));
    }

    static final int DirPropFlagMultiRuns = DirPropFlag((byte)31);

    /* to avoid some conditional statements, use tiny constant arrays */
    static final int DirPropFlagLR[] = { DirPropFlag(L), DirPropFlag(R) };
    static final int DirPropFlagE[] = { DirPropFlag(LRE), DirPropFlag(RLE) };
    static final int DirPropFlagO[] = { DirPropFlag(LRO), DirPropFlag(RLO) };

    static final int DirPropFlagLR(byte level) { return DirPropFlagLR[level & 1]; }
    static final int DirPropFlagE(byte level)  { return DirPropFlagE[level & 1]; }
    static final int DirPropFlagO(byte level)  { return DirPropFlagO[level & 1]; }

    /*
     *  are there any characters that are LTR?
     */
    static final int MASK_LTR =
        DirPropFlag(L)|DirPropFlag(EN)|DirPropFlag(AN)|DirPropFlag(LRE)|DirPropFlag(LRO);

    /*
     *  are there any characters that are RTL?
     */
    static final int MASK_RTL = DirPropFlag(R)|DirPropFlag(AL)|DirPropFlag(RLE)|DirPropFlag(RLO);

    /* explicit embedding codes */
    private static final int MASK_LRX = DirPropFlag(LRE)|DirPropFlag(LRO);
    private static final int MASK_RLX = DirPropFlag(RLE)|DirPropFlag(RLO);
    private static final int MASK_EXPLICIT = MASK_LRX|MASK_RLX|DirPropFlag(PDF);
    private static final int MASK_BN_EXPLICIT = DirPropFlag(BN)|MASK_EXPLICIT;

    /* paragraph and segment separators */
    private static final int MASK_B_S = DirPropFlag(B)|DirPropFlag(S);

    /* all types that are counted as White Space or Neutral in some steps */
    static final int MASK_WS = MASK_B_S|DirPropFlag(WS)|MASK_BN_EXPLICIT;
    private static final int MASK_N = DirPropFlag(ON)|MASK_WS;

    /* types that are neutrals or could becomes neutrals in (Wn) */
    private static final int MASK_POSSIBLE_N = DirPropFlag(CS)|DirPropFlag(ES)|DirPropFlag(ET)|MASK_N;

    /*
     * These types may be changed to "e",
     * the embedding type (L or R) of the run,
     * in the Bidi algorithm (N2)
     */
    static final int MASK_EMBEDDING = DirPropFlag(NSM)|MASK_POSSIBLE_N;

    /*
     *  the dirProp's L and R are defined to 0 and 1 values in UCharacterDirection.java
     */
    private static byte GetLRFromLevel(byte level)
    {
        return (byte)(level & 1);
    }

    private static boolean IsDefaultLevel(byte level)
    {
        return ((level & INTERNAL_LEVEL_DEFAULT_LTR) == INTERNAL_LEVEL_DEFAULT_LTR);
    }

    byte GetParaLevelAt(int index)
    {
        return (defaultParaLevel != 0) ?
                (byte)(dirProps[index]>>CONTEXT_RTL_SHIFT) : paraLevel;
    }

    static boolean IsBidiControlChar(int c)
    {
        /* check for range 0x200c to 0x200f (ZWNJ, ZWJ, LRM, RLM) or
                           0x202a to 0x202e (LRE, RLE, PDF, LRO, RLO) */
        return (((c & 0xfffffffc) == 0x200c) || ((c >= 0x202a) && (c <= 0x202e)));
    }

    public void verifyValidPara()
    {
        if (this != this.paraBidi) {
            throw new IllegalStateException("");
        }
    }

    public void verifyValidParaOrLine()
    {
        BidiBase para = this.paraBidi;
        /* verify Para */
        if (this == para) {
            return;
        }
        /* verify Line */
        if ((para == null) || (para != para.paraBidi)) {
            throw new IllegalStateException();
        }
    }

    public void verifyRange(int index, int start, int limit)
    {
        if (index < start || index >= limit) {
            throw new IllegalArgumentException("Value " + index +
                      " is out of range " + start + " to " + limit);
        }
    }

    public void verifyIndex(int index, int start, int limit)
    {
        if (index < start || index >= limit) {
            throw new ArrayIndexOutOfBoundsException("Index " + index +
                      " is out of range " + start + " to " + limit);
        }
    }

    /**
     * Allocate a <code>Bidi</code> object with preallocated memory
     * for internal structures.
     * This method provides a <code>Bidi</code> object like the default constructor
     * but it also preallocates memory for internal structures
     * according to the sizings supplied by the caller.<p>
     * The preallocation can be limited to some of the internal memory
     * by setting some values to 0 here. That means that if, e.g.,
     * <code>maxRunCount</code> cannot be reasonably predetermined and should not
     * be set to <code>maxLength</code> (the only failproof value) to avoid
     * wasting  memory, then <code>maxRunCount</code> could be set to 0 here
     * and the internal structures that are associated with it will be allocated
     * on demand, just like with the default constructor.
     *
     * @param maxLength is the maximum text or line length that internal memory
     *        will be preallocated for. An attempt to associate this object with a
     *        longer text will fail, unless this value is 0, which leaves the allocation
     *        up to the implementation.
     *
     * @param maxRunCount is the maximum anticipated number of same-level runs
     *        that internal memory will be preallocated for. An attempt to access
     *        visual runs on an object that was not preallocated for as many runs
     *        as the text was actually resolved to will fail,
     *        unless this value is 0, which leaves the allocation up to the implementation.<br><br>
     *        The number of runs depends on the actual text and maybe anywhere between
     *        1 and <code>maxLength</code>. It is typically small.
     *
     * @throws IllegalArgumentException if maxLength or maxRunCount is less than 0
     * @stable ICU 3.8
     */
    public BidiBase(int maxLength, int maxRunCount)
     {
        /* check the argument values */
        if (maxLength < 0 || maxRunCount < 0) {
            throw new IllegalArgumentException();
        }

        /* reset the object, all reference variables null, all flags false,
           all sizes 0.
           In fact, we don't need to do anything, since class members are
           initialized as zero when an instance is created.
         */
        /*
        mayAllocateText = false;
        mayAllocateRuns = false;
        orderParagraphsLTR = false;
        paraCount = 0;
        runCount = 0;
        trailingWSStart = 0;
        flags = 0;
        paraLevel = 0;
        defaultParaLevel = 0;
        direction = 0;
        */
        /* get Bidi properties */
        try {
            bdp = UBiDiProps.getSingleton();
        }
        catch (IOException e) {
            throw new MissingResourceException(e.getMessage(), "(BidiProps)", "");
        }

        /* allocate memory for arrays as requested */
        if (maxLength > 0) {
            getInitialDirPropsMemory(maxLength);
            getInitialLevelsMemory(maxLength);
        } else {
            mayAllocateText = true;
        }

        if (maxRunCount > 0) {
            // if maxRunCount == 1, use simpleRuns[]
            if (maxRunCount > 1) {
                getInitialRunsMemory(maxRunCount);
            }
        } else {
            mayAllocateRuns = true;
        }
    }

    /*
     * We are allowed to allocate memory if object==null or
     * mayAllocate==true for each array that we need.
     *
     * Assume sizeNeeded>0.
     * If object != null, then assume size > 0.
     */
    private Object getMemory(String label, Object array, Class arrayClass,
            boolean mayAllocate, int sizeNeeded)
    {
        int len = Array.getLength(array);

        /* we have at least enough memory and must not allocate */
        if (sizeNeeded == len) {
            return array;
        }
        if (!mayAllocate) {
            /* we must not allocate */
            if (sizeNeeded <= len) {
                return array;
            }
            throw new OutOfMemoryError("Failed to allocate memory for "
                                       + label);
        }
        /* we may try to grow or shrink */
        /* FOOD FOR THOUGHT: when shrinking it should be possible to avoid
           the allocation altogether and rely on this.length */
        try {
            return Array.newInstance(arrayClass, sizeNeeded);
        } catch (Exception e) {
            throw new OutOfMemoryError("Failed to allocate memory for "
                                       + label);
        }
    }

    /* helper methods for each allocated array */
    private void getDirPropsMemory(boolean mayAllocate, int len)
    {
        Object array = getMemory("DirProps", dirPropsMemory, Byte.TYPE, mayAllocate, len);
        dirPropsMemory = (byte[]) array;
    }

    void getDirPropsMemory(int len)
    {
        getDirPropsMemory(mayAllocateText, len);
    }

    private void getLevelsMemory(boolean mayAllocate, int len)
    {
        Object array = getMemory("Levels", levelsMemory, Byte.TYPE, mayAllocate, len);
        levelsMemory = (byte[]) array;
    }

    void getLevelsMemory(int len)
    {
        getLevelsMemory(mayAllocateText, len);
    }

    private void getRunsMemory(boolean mayAllocate, int len)
    {
        Object array = getMemory("Runs", runsMemory, BidiRun.class, mayAllocate, len);
        runsMemory = (BidiRun[]) array;
    }

    void getRunsMemory(int len)
    {
        getRunsMemory(mayAllocateRuns, len);
    }

    /* additional methods used by constructor - always allow allocation */
    private void getInitialDirPropsMemory(int len)
    {
        getDirPropsMemory(true, len);
    }

    private void getInitialLevelsMemory(int len)
    {
        getLevelsMemory(true, len);
    }

    private void getInitialParasMemory(int len)
    {
        Object array = getMemory("Paras", parasMemory, Integer.TYPE, true, len);
        parasMemory = (int[]) array;
    }

    private void getInitialRunsMemory(int len)
    {
        getRunsMemory(true, len);
    }

/* perform (P2)..(P3) ------------------------------------------------------- */

    private void getDirProps()
    {
        int i = 0, i0, i1;
        flags = 0;          /* collect all directionalities in the text */
        int uchar;
        byte dirProp;
        byte paraDirDefault = 0;   /* initialize to avoid compiler warnings */
        boolean isDefaultLevel = IsDefaultLevel(paraLevel);
        /* for inverse Bidi, the default para level is set to RTL if there is a
           strong R or AL character at either end of the text                */
        lastArabicPos = -1;
        controlCount = 0;

        final int NOT_CONTEXTUAL = 0;         /* 0: not contextual paraLevel */
        final int LOOKING_FOR_STRONG = 1;     /* 1: looking for first strong char */
        final int FOUND_STRONG_CHAR = 2;      /* 2: found first strong char       */

        int state;
        int paraStart = 0;                    /* index of first char in paragraph */
        byte paraDir;                         /* == CONTEXT_RTL within paragraphs
                                                 starting with strong R char      */
        byte lastStrongDir=0;                 /* for default level & inverse Bidi */
        int lastStrongLTR=0;                  /* for STREAMING option             */

        if (isDefaultLevel) {
            paraDirDefault = ((paraLevel & 1) != 0) ? CONTEXT_RTL : 0;
            paraDir = paraDirDefault;
            lastStrongDir = paraDirDefault;
            state = LOOKING_FOR_STRONG;
        } else {
            state = NOT_CONTEXTUAL;
            paraDir = 0;
        }
        /* count paragraphs and determine the paragraph level (P2..P3) */
        /*
         * see comment on constant fields:
         * the LEVEL_DEFAULT_XXX values are designed so that
         * their low-order bit alone yields the intended default
         */

        for (i = 0; i < originalLength; /* i is incremented in the loop */) {
            i0 = i;                     /* index of first code unit */
            uchar = UTF16.charAt(text, 0, originalLength, i);
            i += Character.charCount(uchar);
            i1 = i - 1; /* index of last code unit, gets the directional property */

            dirProp = (byte)bdp.getClass(uchar);

            flags |= DirPropFlag(dirProp);
            dirProps[i1] = (byte)(dirProp | paraDir);
            if (i1 > i0) {     /* set previous code units' properties to BN */
                flags |= DirPropFlag(BN);
                do {
                    dirProps[--i1] = (byte)(BN | paraDir);
                } while (i1 > i0);
            }
            if (state == LOOKING_FOR_STRONG) {
                if (dirProp == L) {
                    state = FOUND_STRONG_CHAR;
                    if (paraDir != 0) {
                        paraDir = 0;
                        for (i1 = paraStart; i1 < i; i1++) {
                            dirProps[i1] &= ~CONTEXT_RTL;
                        }
                    }
                    continue;
                }
                if (dirProp == R || dirProp == AL) {
                    state = FOUND_STRONG_CHAR;
                    if (paraDir == 0) {
                        paraDir = CONTEXT_RTL;
                        for (i1 = paraStart; i1 < i; i1++) {
                            dirProps[i1] |= CONTEXT_RTL;
                        }
                    }
                    continue;
                }
            }
            if (dirProp == L) {
                lastStrongDir = 0;
                lastStrongLTR = i;      /* i is index to next character */
            }
            else if (dirProp == R) {
                lastStrongDir = CONTEXT_RTL;
            }
            else if (dirProp == AL) {
                lastStrongDir = CONTEXT_RTL;
                lastArabicPos = i-1;
            }
            else if (dirProp == B) {
                if (i < originalLength) {   /* B not last char in text */
                    if (!((uchar == (int)CR) && (text[i] == (int)LF))) {
                        paraCount++;
                    }
                    if (isDefaultLevel) {
                        state=LOOKING_FOR_STRONG;
                        paraStart = i;        /* i is index to next character */
                        paraDir = paraDirDefault;
                        lastStrongDir = paraDirDefault;
                    }
                }
            }
        }
        if (isDefaultLevel) {
            paraLevel = GetParaLevelAt(0);
        }

        /* The following line does nothing new for contextual paraLevel, but is
           needed for absolute paraLevel.                               */
        flags |= DirPropFlagLR(paraLevel);

        if (orderParagraphsLTR && (flags & DirPropFlag(B)) != 0) {
            flags |= DirPropFlag(L);
        }
    }

    /* perform (X1)..(X9) ------------------------------------------------------- */

    /* determine if the text is mixed-directional or single-directional */
    private byte directionFromFlags() {
        /* if the text contains AN and neutrals, then some neutrals may become RTL */
        if (!((flags & MASK_RTL) != 0 ||
              ((flags & DirPropFlag(AN)) != 0 &&
               (flags & MASK_POSSIBLE_N) != 0))) {
            return Bidi.DIRECTION_LEFT_TO_RIGHT;
        } else if ((flags & MASK_LTR) == 0) {
            return Bidi.DIRECTION_RIGHT_TO_LEFT;
        } else {
            return MIXED;
        }
    }

    /*
     * Resolve the explicit levels as specified by explicit embedding codes.
     * Recalculate the flags to have them reflect the real properties
     * after taking the explicit embeddings into account.
     *
     * The Bidi algorithm is designed to result in the same behavior whether embedding
     * levels are externally specified (from "styled text", supposedly the preferred
     * method) or set by explicit embedding codes (LRx, RLx, PDF) in the plain text.
     * That is why (X9) instructs to remove all explicit codes (and BN).
     * However, in a real implementation, this removal of these codes and their index
     * positions in the plain text is undesirable since it would result in
     * reallocated, reindexed text.
     * Instead, this implementation leaves the codes in there and just ignores them
     * in the subsequent processing.
     * In order to get the same reordering behavior, positions with a BN or an
     * explicit embedding code just get the same level assigned as the last "real"
     * character.
     *
     * Some implementations, not this one, then overwrite some of these
     * directionality properties at "real" same-level-run boundaries by
     * L or R codes so that the resolution of weak types can be performed on the
     * entire paragraph at once instead of having to parse it once more and
     * perform that resolution on same-level-runs.
     * This limits the scope of the implicit rules in effectively
     * the same way as the run limits.
     *
     * Instead, this implementation does not modify these codes.
     * On one hand, the paragraph has to be scanned for same-level-runs, but
     * on the other hand, this saves another loop to reset these codes,
     * or saves making and modifying a copy of dirProps[].
     *
     *
     * Note that (Pn) and (Xn) changed significantly from version 4 of the Bidi algorithm.
     *
     *
     * Handling the stack of explicit levels (Xn):
     *
     * With the Bidi stack of explicit levels,
     * as pushed with each LRE, RLE, LRO, and RLO and popped with each PDF,
     * the explicit level must never exceed MAX_EXPLICIT_LEVEL==61.
     *
     * In order to have a correct push-pop semantics even in the case of overflows,
     * there are two overflow counters:
     * - countOver60 is incremented with each LRx at level 60
     * - from level 60, one RLx increases the level to 61
     * - countOver61 is incremented with each LRx and RLx at level 61
     *
     * Popping levels with PDF must work in the opposite order so that level 61
     * is correct at the correct point. Underflows (too many PDFs) must be checked.
     *
     * This implementation assumes that MAX_EXPLICIT_LEVEL is odd.
     */
    private byte resolveExplicitLevels() {
        int i = 0;
        byte dirProp;
        byte level = GetParaLevelAt(0);

        byte dirct;
        int paraIndex = 0;

        /* determine if the text is mixed-directional or single-directional */
        dirct = directionFromFlags();

        /* we may not need to resolve any explicit levels, but for multiple
           paragraphs we want to loop on all chars to set the para boundaries */
        if ((dirct != MIXED) && (paraCount == 1)) {
            /* not mixed directionality: levels don't matter - trailingWSStart will be 0 */
        } else if ((paraCount == 1) &&
                   ((flags & MASK_EXPLICIT) == 0)) {
            /* mixed, but all characters are at the same embedding level */
            /* or we are in "inverse Bidi" */
            /* and we don't have contextual multiple paragraphs with some B char */
            /* set all levels to the paragraph level */
            for (i = 0; i < length; ++i) {
                levels[i] = level;
            }
        } else {
            /* continue to perform (Xn) */

            /* (X1) level is set for all codes, embeddingLevel keeps track of the push/pop operations */
            /* both variables may carry the LEVEL_OVERRIDE flag to indicate the override status */
            byte embeddingLevel = level;
            byte newLevel;
            byte stackTop = 0;

            byte[] stack = new byte[MAX_EXPLICIT_LEVEL];    /* we never push anything >=MAX_EXPLICIT_LEVEL */
            int countOver60 = 0;
            int countOver61 = 0;  /* count overflows of explicit levels */

            /* recalculate the flags */
            flags = 0;

            for (i = 0; i < length; ++i) {
                dirProp = NoContextRTL(dirProps[i]);
                switch(dirProp) {
                case LRE:
                case LRO:
                    /* (X3, X5) */
                    newLevel = (byte)((embeddingLevel+2) & ~(INTERNAL_LEVEL_OVERRIDE | 1)); /* least greater even level */
                    if (newLevel <= MAX_EXPLICIT_LEVEL) {
                        stack[stackTop] = embeddingLevel;
                        ++stackTop;
                        embeddingLevel = newLevel;
                        if (dirProp == LRO) {
                            embeddingLevel |= INTERNAL_LEVEL_OVERRIDE;
                        }
                        /* we don't need to set LEVEL_OVERRIDE off for LRE
                           since this has already been done for newLevel which is
                           the source for embeddingLevel.
                         */
                    } else if ((embeddingLevel & ~INTERNAL_LEVEL_OVERRIDE) == MAX_EXPLICIT_LEVEL) {
                        ++countOver61;
                    } else /* (embeddingLevel & ~INTERNAL_LEVEL_OVERRIDE) == MAX_EXPLICIT_LEVEL-1 */ {
                        ++countOver60;
                    }
                    flags |= DirPropFlag(BN);
                    break;
                case RLE:
                case RLO:
                    /* (X2, X4) */
                    newLevel=(byte)(((embeddingLevel & ~INTERNAL_LEVEL_OVERRIDE) + 1) | 1); /* least greater odd level */
                    if (newLevel<=MAX_EXPLICIT_LEVEL) {
                        stack[stackTop] = embeddingLevel;
                        ++stackTop;
                        embeddingLevel = newLevel;
                        if (dirProp == RLO) {
                            embeddingLevel |= INTERNAL_LEVEL_OVERRIDE;
                        }
                        /* we don't need to set LEVEL_OVERRIDE off for RLE
                           since this has already been done for newLevel which is
                           the source for embeddingLevel.
                         */
                    } else {
                        ++countOver61;
                    }
                    flags |= DirPropFlag(BN);
                    break;
                case PDF:
                    /* (X7) */
                    /* handle all the overflow cases first */
                    if (countOver61 > 0) {
                        --countOver61;
                    } else if (countOver60 > 0 && (embeddingLevel & ~INTERNAL_LEVEL_OVERRIDE) != MAX_EXPLICIT_LEVEL) {
                        /* handle LRx overflows from level 60 */
                        --countOver60;
                    } else if (stackTop > 0) {
                        /* this is the pop operation; it also pops level 61 while countOver60>0 */
                        --stackTop;
                        embeddingLevel = stack[stackTop];
                    /* } else { (underflow) */
                    }
                    flags |= DirPropFlag(BN);
                    break;
                case B:
                    stackTop = 0;
                    countOver60 = 0;
                    countOver61 = 0;
                    level = GetParaLevelAt(i);
                    if ((i + 1) < length) {
                        embeddingLevel = GetParaLevelAt(i+1);
                        if (!((text[i] == CR) && (text[i + 1] == LF))) {
                            paras[paraIndex++] = i+1;
                        }
                    }
                    flags |= DirPropFlag(B);
                    break;
                case BN:
                    /* BN, LRE, RLE, and PDF are supposed to be removed (X9) */
                    /* they will get their levels set correctly in adjustWSLevels() */
                    flags |= DirPropFlag(BN);
                    break;
                default:
                    /* all other types get the "real" level */
                    if (level != embeddingLevel) {
                        level = embeddingLevel;
                        if ((level & INTERNAL_LEVEL_OVERRIDE) != 0) {
                            flags |= DirPropFlagO(level) | DirPropFlagMultiRuns;
                        } else {
                            flags |= DirPropFlagE(level) | DirPropFlagMultiRuns;
                        }
                    }
                    if ((level & INTERNAL_LEVEL_OVERRIDE) == 0) {
                        flags |= DirPropFlag(dirProp);
                    }
                    break;
                }

                /*
                 * We need to set reasonable levels even on BN codes and
                 * explicit codes because we will later look at same-level runs (X10).
                 */
                levels[i] = level;
            }
            if ((flags & MASK_EMBEDDING) != 0) {
                flags |= DirPropFlagLR(paraLevel);
            }
            if (orderParagraphsLTR && (flags & DirPropFlag(B)) != 0) {
                flags |= DirPropFlag(L);
            }

            /* subsequently, ignore the explicit codes and BN (X9) */

            /* again, determine if the text is mixed-directional or single-directional */
            dirct = directionFromFlags();
        }

        return dirct;
    }

    /*
     * Use a pre-specified embedding levels array:
     *
     * Adjust the directional properties for overrides (->LEVEL_OVERRIDE),
     * ignore all explicit codes (X9),
     * and check all the preset levels.
     *
     * Recalculate the flags to have them reflect the real properties
     * after taking the explicit embeddings into account.
     */
    private byte checkExplicitLevels() {
        byte dirProp;
        int i;
        this.flags = 0;     /* collect all directionalities in the text */
        byte level;
        int paraIndex = 0;

        for (i = 0; i < length; ++i) {
            if (levels[i] == 0) {
                levels[i] = paraLevel;
            }
            if (MAX_EXPLICIT_LEVEL < (levels[i]&0x7f)) {
                if ((levels[i] & INTERNAL_LEVEL_OVERRIDE) != 0) {
                    levels[i] =  (byte)(paraLevel|INTERNAL_LEVEL_OVERRIDE);
                } else {
                    levels[i] = paraLevel;
                }
            }
            level = levels[i];
            dirProp = NoContextRTL(dirProps[i]);
            if ((level & INTERNAL_LEVEL_OVERRIDE) != 0) {
                /* keep the override flag in levels[i] but adjust the flags */
                level &= ~INTERNAL_LEVEL_OVERRIDE;     /* make the range check below simpler */
                flags |= DirPropFlagO(level);
            } else {
                /* set the flags */
                flags |= DirPropFlagE(level) | DirPropFlag(dirProp);
            }

            if ((level < GetParaLevelAt(i) &&
                    !((0 == level) && (dirProp == B))) ||
                    (MAX_EXPLICIT_LEVEL <level)) {
                /* level out of bounds */
                throw new IllegalArgumentException("level " + level +
                                                   " out of bounds at index " + i);
            }
            if ((dirProp == B) && ((i + 1) < length)) {
                if (!((text[i] == CR) && (text[i + 1] == LF))) {
                    paras[paraIndex++] = i + 1;
                }
            }
        }
        if ((flags&MASK_EMBEDDING) != 0) {
            flags |= DirPropFlagLR(paraLevel);
        }

        /* determine if the text is mixed-directional or single-directional */
        return directionFromFlags();
    }

    /*********************************************************************/
    /* The Properties state machine table                                */
    /*********************************************************************/
    /*                                                                   */
    /* All table cells are 8 bits:                                       */
    /*      bits 0..4:  next state                                       */
    /*      bits 5..7:  action to perform (if > 0)                       */
    /*                                                                   */
    /* Cells may be of format "n" where n represents the next state      */
    /* (except for the rightmost column).                                */
    /* Cells may also be of format "_(x,y)" where x represents an action */
    /* to perform and y represents the next state.                       */
    /*                                                                   */
    /*********************************************************************/
    /* Definitions and type for properties state tables                  */
    /*********************************************************************/
    private static final int IMPTABPROPS_COLUMNS = 14;
    private static final int IMPTABPROPS_RES = IMPTABPROPS_COLUMNS - 1;
    private static short GetStateProps(short cell) {
        return (short)(cell & 0x1f);
    }
    private static short GetActionProps(short cell) {
        return (short)(cell >> 5);
    }

    private static final short groupProp[] =          /* dirProp regrouped */
    {
        /*  L   R   EN  ES  ET  AN  CS  B   S   WS  ON  LRE LRO AL  RLE RLO PDF NSM BN  */
        0,  1,  2,  7,  8,  3,  9,  6,  5,  4,  4,  10, 10, 12, 10, 10, 10, 11, 10
    };
    private static final short _L  = 0;
    private static final short _R  = 1;
    private static final short _EN = 2;
    private static final short _AN = 3;
    private static final short _ON = 4;
    private static final short _S  = 5;
    private static final short _B  = 6; /* reduced dirProp */

    /*********************************************************************/
    /*                                                                   */
    /*      PROPERTIES  STATE  TABLE                                     */
    /*                                                                   */
    /* In table impTabProps,                                             */
    /*      - the ON column regroups ON and WS                           */
    /*      - the BN column regroups BN, LRE, RLE, LRO, RLO, PDF         */
    /*      - the Res column is the reduced property assigned to a run   */
    /*                                                                   */
    /* Action 1: process current run1, init new run1                     */
    /*        2: init new run2                                           */
    /*        3: process run1, process run2, init new run1               */
    /*        4: process run1, set run1=run2, init new run2              */
    /*                                                                   */
    /* Notes:                                                            */
    /*  1) This table is used in resolveImplicitLevels().                */
    /*  2) This table triggers actions when there is a change in the Bidi*/
    /*     property of incoming characters (action 1).                   */
    /*  3) Most such property sequences are processed immediately (in    */
    /*     fact, passed to processPropertySeq().                         */
    /*  4) However, numbers are assembled as one sequence. This means    */
    /*     that undefined situations (like CS following digits, until    */
    /*     it is known if the next char will be a digit) are held until  */
    /*     following chars define them.                                  */
    /*     Example: digits followed by CS, then comes another CS or ON;  */
    /*              the digits will be processed, then the CS assigned   */
    /*              as the start of an ON sequence (action 3).           */
    /*  5) There are cases where more than one sequence must be          */
    /*     processed, for instance digits followed by CS followed by L:  */
    /*     the digits must be processed as one sequence, and the CS      */
    /*     must be processed as an ON sequence, all this before starting */
    /*     assembling chars for the opening L sequence.                  */
    /*                                                                   */
    /*                                                                   */
    private static final short impTabProps[][] =
    {
/*                        L,     R,    EN,    AN,    ON,     S,     B,    ES,    ET,    CS,    BN,   NSM,    AL,  Res */
/* 0 Init        */ {     1,     2,     4,     5,     7,    15,    17,     7,     9,     7,     0,     7,     3,  _ON },
/* 1 L           */ {     1,  32+2,  32+4,  32+5,  32+7, 32+15, 32+17,  32+7,  32+9,  32+7,     1,     1,  32+3,   _L },
/* 2 R           */ {  32+1,     2,  32+4,  32+5,  32+7, 32+15, 32+17,  32+7,  32+9,  32+7,     2,     2,  32+3,   _R },
/* 3 AL          */ {  32+1,  32+2,  32+6,  32+6,  32+8, 32+16, 32+17,  32+8,  32+8,  32+8,     3,     3,     3,   _R },
/* 4 EN          */ {  32+1,  32+2,     4,  32+5,  32+7, 32+15, 32+17, 64+10,    11, 64+10,     4,     4,  32+3,  _EN },
/* 5 AN          */ {  32+1,  32+2,  32+4,     5,  32+7, 32+15, 32+17,  32+7,  32+9, 64+12,     5,     5,  32+3,  _AN },
/* 6 AL:EN/AN    */ {  32+1,  32+2,     6,     6,  32+8, 32+16, 32+17,  32+8,  32+8, 64+13,     6,     6,  32+3,  _AN },
/* 7 ON          */ {  32+1,  32+2,  32+4,  32+5,     7, 32+15, 32+17,     7, 64+14,     7,     7,     7,  32+3,  _ON },
/* 8 AL:ON       */ {  32+1,  32+2,  32+6,  32+6,     8, 32+16, 32+17,     8,     8,     8,     8,     8,  32+3,  _ON },
/* 9 ET          */ {  32+1,  32+2,     4,  32+5,     7, 32+15, 32+17,     7,     9,     7,     9,     9,  32+3,  _ON },
/*10 EN+ES/CS    */ {  96+1,  96+2,     4,  96+5, 128+7, 96+15, 96+17, 128+7,128+14, 128+7,    10, 128+7,  96+3,  _EN },
/*11 EN+ET       */ {  32+1,  32+2,     4,  32+5,  32+7, 32+15, 32+17,  32+7,    11,  32+7,    11,    11,  32+3,  _EN },
/*12 AN+CS       */ {  96+1,  96+2,  96+4,     5, 128+7, 96+15, 96+17, 128+7,128+14, 128+7,    12, 128+7,  96+3,  _AN },
/*13 AL:EN/AN+CS */ {  96+1,  96+2,     6,     6, 128+8, 96+16, 96+17, 128+8, 128+8, 128+8,    13, 128+8,  96+3,  _AN },
/*14 ON+ET       */ {  32+1,  32+2, 128+4,  32+5,     7, 32+15, 32+17,     7,    14,     7,    14,    14,  32+3,  _ON },
/*15 S           */ {  32+1,  32+2,  32+4,  32+5,  32+7,    15, 32+17,  32+7,  32+9,  32+7,    15,  32+7,  32+3,   _S },
/*16 AL:S        */ {  32+1,  32+2,  32+6,  32+6,  32+8,    16, 32+17,  32+8,  32+8,  32+8,    16,  32+8,  32+3,   _S },
/*17 B           */ {  32+1,  32+2,  32+4,  32+5,  32+7, 32+15,    17,  32+7,  32+9,  32+7,    17,  32+7,  32+3,   _B }
    };

    /*********************************************************************/
    /* The levels state machine tables                                   */
    /*********************************************************************/
    /*                                                                   */
    /* All table cells are 8 bits:                                       */
    /*      bits 0..3:  next state                                       */
    /*      bits 4..7:  action to perform (if > 0)                       */
    /*                                                                   */
    /* Cells may be of format "n" where n represents the next state      */
    /* (except for the rightmost column).                                */
    /* Cells may also be of format "_(x,y)" where x represents an action */
    /* to perform and y represents the next state.                       */
    /*                                                                   */
    /* This format limits each table to 16 states each and to 15 actions.*/
    /*                                                                   */
    /*********************************************************************/
    /* Definitions and type for levels state tables                      */
    /*********************************************************************/
    private static final int IMPTABLEVELS_COLUMNS = _B + 2;
    private static final int IMPTABLEVELS_RES = IMPTABLEVELS_COLUMNS - 1;
    private static short GetState(byte cell) { return (short)(cell & 0x0f); }
    private static short GetAction(byte cell) { return (short)(cell >> 4); }

    private static class ImpTabPair {
        byte[][][] imptab;
        short[][] impact;

        ImpTabPair(byte[][] table1, byte[][] table2,
                   short[] act1, short[] act2) {
            imptab = new byte[][][] {table1, table2};
            impact = new short[][] {act1, act2};
        }
    }

    /*********************************************************************/
    /*                                                                   */
    /*      LEVELS  STATE  TABLES                                        */
    /*                                                                   */
    /* In all levels state tables,                                       */
    /*      - state 0 is the initial state                               */
    /*      - the Res column is the increment to add to the text level   */
    /*        for this property sequence.                                */
    /*                                                                   */
    /* The impact arrays for each table of a pair map the local action   */
    /* numbers of the table to the total list of actions. For instance,  */
    /* action 2 in a given table corresponds to the action number which  */
    /* appears in entry [2] of the impact array for that table.          */
    /* The first entry of all impact arrays must be 0.                   */
    /*                                                                   */
    /* Action 1: init conditional sequence                               */
    /*        2: prepend conditional sequence to current sequence        */
    /*        3: set ON sequence to new level - 1                        */
    /*        4: init EN/AN/ON sequence                                  */
    /*        5: fix EN/AN/ON sequence followed by R                     */
    /*        6: set previous level sequence to level 2                  */
    /*                                                                   */
    /* Notes:                                                            */
    /*  1) These tables are used in processPropertySeq(). The input      */
    /*     is property sequences as determined by resolveImplicitLevels. */
    /*  2) Most such property sequences are processed immediately        */
    /*     (levels are assigned).                                        */
    /*  3) However, some sequences cannot be assigned a final level till */
    /*     one or more following sequences are received. For instance,   */
    /*     ON following an R sequence within an even-level paragraph.    */
    /*     If the following sequence is R, the ON sequence will be       */
    /*     assigned basic run level+1, and so will the R sequence.       */
    /*  4) S is generally handled like ON, since its level will be fixed */
    /*     to paragraph level in adjustWSLevels().                       */
    /*                                                                   */

    private static final byte impTabL_DEFAULT[][] = /* Even paragraph level */
        /*  In this table, conditional sequences receive the higher possible level
            until proven otherwise.
        */
    {
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     0,     1,     0,     2,     0,     0,     0,  0 },
        /* 1 : R          */ {     0,     1,     3,     3,  0x14,  0x14,     0,  1 },
        /* 2 : AN         */ {     0,     1,     0,     2,  0x15,  0x15,     0,  2 },
        /* 3 : R+EN/AN    */ {     0,     1,     3,     3,  0x14,  0x14,     0,  2 },
        /* 4 : R+ON       */ {  0x20,     1,     3,     3,     4,     4,  0x20,  1 },
        /* 5 : AN+ON      */ {  0x20,     1,  0x20,     2,     5,     5,  0x20,  1 }
    };

    private static final byte impTabR_DEFAULT[][] = /* Odd  paragraph level */
        /*  In this table, conditional sequences receive the lower possible level
            until proven otherwise.
        */
    {
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     1,     0,     2,     2,     0,     0,     0,  0 },
        /* 1 : L          */ {     1,     0,     1,     3,  0x14,  0x14,     0,  1 },
        /* 2 : EN/AN      */ {     1,     0,     2,     2,     0,     0,     0,  1 },
        /* 3 : L+AN       */ {     1,     0,     1,     3,     5,     5,     0,  1 },
        /* 4 : L+ON       */ {  0x21,     0,  0x21,     3,     4,     4,     0,  0 },
        /* 5 : L+AN+ON    */ {     1,     0,     1,     3,     5,     5,     0,  0 }
    };

    private static final short[] impAct0 = {0,1,2,3,4,5,6};

    private static final ImpTabPair impTab_DEFAULT = new ImpTabPair(
            impTabL_DEFAULT, impTabR_DEFAULT, impAct0, impAct0);

    private static final byte impTabL_NUMBERS_SPECIAL[][] = { /* Even paragraph level */
        /* In this table, conditional sequences receive the higher possible
           level until proven otherwise.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     0,     2,     1,     1,     0,     0,     0,  0 },
        /* 1 : L+EN/AN    */ {     0,     2,     1,     1,     0,     0,     0,  2 },
        /* 2 : R          */ {     0,     2,     4,     4,  0x13,     0,     0,  1 },
        /* 3 : R+ON       */ {  0x20,     2,     4,     4,     3,     3,  0x20,  1 },
        /* 4 : R+EN/AN    */ {     0,     2,     4,     4,  0x13,  0x13,     0,  2 }
    };
    private static final ImpTabPair impTab_NUMBERS_SPECIAL = new ImpTabPair(
            impTabL_NUMBERS_SPECIAL, impTabR_DEFAULT, impAct0, impAct0);

    private static final byte impTabL_GROUP_NUMBERS_WITH_R[][] = {
        /* In this table, EN/AN+ON sequences receive levels as if associated with R
           until proven that there is L or sor/eor on both sides. AN is handled like EN.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 init         */ {     0,     3,  0x11,  0x11,     0,     0,     0,  0 },
        /* 1 EN/AN        */ {  0x20,     3,     1,     1,     2,  0x20,  0x20,  2 },
        /* 2 EN/AN+ON     */ {  0x20,     3,     1,     1,     2,  0x20,  0x20,  1 },
        /* 3 R            */ {     0,     3,     5,     5,  0x14,     0,     0,  1 },
        /* 4 R+ON         */ {  0x20,     3,     5,     5,     4,  0x20,  0x20,  1 },
        /* 5 R+EN/AN      */ {     0,     3,     5,     5,  0x14,     0,     0,  2 }
    };
    private static final byte impTabR_GROUP_NUMBERS_WITH_R[][] = {
        /*  In this table, EN/AN+ON sequences receive levels as if associated with R
            until proven that there is L on both sides. AN is handled like EN.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 init         */ {     2,     0,     1,     1,     0,     0,     0,  0 },
        /* 1 EN/AN        */ {     2,     0,     1,     1,     0,     0,     0,  1 },
        /* 2 L            */ {     2,     0,  0x14,  0x14,  0x13,     0,     0,  1 },
        /* 3 L+ON         */ {  0x22,     0,     4,     4,     3,     0,     0,  0 },
        /* 4 L+EN/AN      */ {  0x22,     0,     4,     4,     3,     0,     0,  1 }
    };
    private static final ImpTabPair impTab_GROUP_NUMBERS_WITH_R = new
            ImpTabPair(impTabL_GROUP_NUMBERS_WITH_R,
                       impTabR_GROUP_NUMBERS_WITH_R, impAct0, impAct0);

    private static final byte impTabL_INVERSE_NUMBERS_AS_L[][] = {
        /* This table is identical to the Default LTR table except that EN and AN
           are handled like L.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     0,     1,     0,     0,     0,     0,     0,  0 },
        /* 1 : R          */ {     0,     1,     0,     0,  0x14,  0x14,     0,  1 },
        /* 2 : AN         */ {     0,     1,     0,     0,  0x15,  0x15,     0,  2 },
        /* 3 : R+EN/AN    */ {     0,     1,     0,     0,  0x14,  0x14,     0,  2 },
        /* 4 : R+ON       */ {  0x20,     1,  0x20,  0x20,     4,     4,  0x20,  1 },
        /* 5 : AN+ON      */ {  0x20,     1,  0x20,  0x20,     5,     5,  0x20,  1 }
    };
    private static final byte impTabR_INVERSE_NUMBERS_AS_L[][] = {
        /* This table is identical to the Default RTL table except that EN and AN
           are handled like L.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     1,     0,     1,     1,     0,     0,     0,  0 },
        /* 1 : L          */ {     1,     0,     1,     1,  0x14,  0x14,     0,  1 },
        /* 2 : EN/AN      */ {     1,     0,     1,     1,     0,     0,     0,  1 },
        /* 3 : L+AN       */ {     1,     0,     1,     1,     5,     5,     0,  1 },
        /* 4 : L+ON       */ {  0x21,     0,  0x21,  0x21,     4,     4,     0,  0 },
        /* 5 : L+AN+ON    */ {     1,     0,     1,     1,     5,     5,     0,  0 }
    };
    private static final ImpTabPair impTab_INVERSE_NUMBERS_AS_L = new ImpTabPair
            (impTabL_INVERSE_NUMBERS_AS_L, impTabR_INVERSE_NUMBERS_AS_L,
             impAct0, impAct0);

    private static final byte impTabR_INVERSE_LIKE_DIRECT[][] = {  /* Odd  paragraph level */
        /*  In this table, conditional sequences receive the lower possible level
            until proven otherwise.
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     1,     0,     2,     2,     0,     0,     0,  0 },
        /* 1 : L          */ {     1,     0,     1,     2,  0x13,  0x13,     0,  1 },
        /* 2 : EN/AN      */ {     1,     0,     2,     2,     0,     0,     0,  1 },
        /* 3 : L+ON       */ {  0x21,  0x30,     6,     4,     3,     3,  0x30,  0 },
        /* 4 : L+ON+AN    */ {  0x21,  0x30,     6,     4,     5,     5,  0x30,  3 },
        /* 5 : L+AN+ON    */ {  0x21,  0x30,     6,     4,     5,     5,  0x30,  2 },
        /* 6 : L+ON+EN    */ {  0x21,  0x30,     6,     4,     3,     3,  0x30,  1 }
    };
    private static final short[] impAct1 = {0,1,11,12};
    private static final ImpTabPair impTab_INVERSE_LIKE_DIRECT = new ImpTabPair(
            impTabL_DEFAULT, impTabR_INVERSE_LIKE_DIRECT, impAct0, impAct1);

    private static final byte impTabL_INVERSE_LIKE_DIRECT_WITH_MARKS[][] = {
        /* The case handled in this table is (visually):  R EN L
         */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     0,  0x63,     0,     1,     0,     0,     0,  0 },
        /* 1 : L+AN       */ {     0,  0x63,     0,     1,  0x12,  0x30,     0,  4 },
        /* 2 : L+AN+ON    */ {  0x20,  0x63,  0x20,     1,     2,  0x30,  0x20,  3 },
        /* 3 : R          */ {     0,  0x63,  0x55,  0x56,  0x14,  0x30,     0,  3 },
        /* 4 : R+ON       */ {  0x30,  0x43,  0x55,  0x56,     4,  0x30,  0x30,  3 },
        /* 5 : R+EN       */ {  0x30,  0x43,     5,  0x56,  0x14,  0x30,  0x30,  4 },
        /* 6 : R+AN       */ {  0x30,  0x43,  0x55,     6,  0x14,  0x30,  0x30,  4 }
    };
    private static final byte impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS[][] = {
        /* The cases handled in this table are (visually):  R EN L
                                                            R L AN L
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {  0x13,     0,     1,     1,     0,     0,     0,  0 },
        /* 1 : R+EN/AN    */ {  0x23,     0,     1,     1,     2,  0x40,     0,  1 },
        /* 2 : R+EN/AN+ON */ {  0x23,     0,     1,     1,     2,  0x40,     0,  0 },
        /* 3 : L          */ {    3 ,     0,     3,  0x36,  0x14,  0x40,     0,  1 },
        /* 4 : L+ON       */ {  0x53,  0x40,     5,  0x36,     4,  0x40,  0x40,  0 },
        /* 5 : L+ON+EN    */ {  0x53,  0x40,     5,  0x36,     4,  0x40,  0x40,  1 },
        /* 6 : L+AN       */ {  0x53,  0x40,     6,     6,     4,  0x40,  0x40,  3 }
    };
    private static final short impAct2[] = {0,1,7,8,9,10};
    private static final ImpTabPair impTab_INVERSE_LIKE_DIRECT_WITH_MARKS =
            new ImpTabPair(impTabL_INVERSE_LIKE_DIRECT_WITH_MARKS,
                           impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS, impAct0, impAct2);

    private static final ImpTabPair impTab_INVERSE_FOR_NUMBERS_SPECIAL = new ImpTabPair(
            impTabL_NUMBERS_SPECIAL, impTabR_INVERSE_LIKE_DIRECT, impAct0, impAct1);

    private static final byte impTabL_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS[][] = {
        /*  The case handled in this table is (visually):  R EN L
        */
        /*                         L,     R,    EN,    AN,    ON,     S,     B, Res */
        /* 0 : init       */ {     0,  0x62,     1,     1,     0,     0,     0,  0 },
        /* 1 : L+EN/AN    */ {     0,  0x62,     1,     1,     0,  0x30,     0,  4 },
        /* 2 : R          */ {     0,  0x62,  0x54,  0x54,  0x13,  0x30,     0,  3 },
        /* 3 : R+ON       */ {  0x30,  0x42,  0x54,  0x54,     3,  0x30,  0x30,  3 },
        /* 4 : R+EN/AN    */ {  0x30,  0x42,     4,     4,  0x13,  0x30,  0x30,  4 }
    };
    private static final ImpTabPair impTab_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS = new
            ImpTabPair(impTabL_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS,
                       impTabR_INVERSE_LIKE_DIRECT_WITH_MARKS, impAct0, impAct2);

    private class LevState {
        byte[][] impTab;                /* level table pointer          */
        short[] impAct;                 /* action map array             */
        int startON;                    /* start of ON sequence         */
        int startL2EN;                  /* start of level 2 sequence    */
        int lastStrongRTL;              /* index of last found R or AL  */
        short state;                    /* current state                */
        byte runLevel;                  /* run level before implicit solving */
    }

    /*------------------------------------------------------------------------*/

    static final int FIRSTALLOC = 10;
    /*
     *  param pos:     position where to insert
     *  param flag:    one of LRM_BEFORE, LRM_AFTER, RLM_BEFORE, RLM_AFTER
     */
    private void addPoint(int pos, int flag)
    {
        Point point = new Point();

        int len = insertPoints.points.length;
        if (len == 0) {
            insertPoints.points = new Point[FIRSTALLOC];
            len = FIRSTALLOC;
        }
        if (insertPoints.size >= len) { /* no room for new point */
            Point[] savePoints = insertPoints.points;
            insertPoints.points = new Point[len * 2];
            System.arraycopy(savePoints, 0, insertPoints.points, 0, len);
        }
        point.pos = pos;
        point.flag = flag;
        insertPoints.points[insertPoints.size] = point;
        insertPoints.size++;
    }

    /* perform rules (Wn), (Nn), and (In) on a run of the text ------------------ */

    /*
     * This implementation of the (Wn) rules applies all rules in one pass.
     * In order to do so, it needs a look-ahead of typically 1 character
     * (except for W5: sequences of ET) and keeps track of changes
     * in a rule Wp that affect a later Wq (p<q).
     *
     * The (Nn) and (In) rules are also performed in that same single loop,
     * but effectively one iteration behind for white space.
     *
     * Since all implicit rules are performed in one step, it is not necessary
     * to actually store the intermediate directional properties in dirProps[].
     */

    private void processPropertySeq(LevState levState, short _prop,
            int start, int limit) {
        byte cell;
        byte[][] impTab = levState.impTab;
        short[] impAct = levState.impAct;
        short oldStateSeq,actionSeq;
        byte level, addLevel;
        int start0, k;

        start0 = start;                 /* save original start position */
        oldStateSeq = levState.state;
        cell = impTab[oldStateSeq][_prop];
        levState.state = GetState(cell);        /* isolate the new state */
        actionSeq = impAct[GetAction(cell)];    /* isolate the action */
        addLevel = (byte)impTab[levState.state][IMPTABLEVELS_RES];

        if (actionSeq != 0) {
            switch (actionSeq) {
            case 1:                     /* init ON seq */
                levState.startON = start0;
                break;

            case 2:                     /* prepend ON seq to current seq */
                start = levState.startON;
                break;

            case 3:                     /* L or S after possible relevant EN/AN */
                /* check if we had EN after R/AL */
                if (levState.startL2EN >= 0) {
                    addPoint(levState.startL2EN, LRM_BEFORE);
                }
                levState.startL2EN = -1;  /* not within previous if since could also be -2 */
                /* check if we had any relevant EN/AN after R/AL */
                if ((insertPoints.points.length == 0) ||
                        (insertPoints.size <= insertPoints.confirmed)) {
                    /* nothing, just clean up */
                    levState.lastStrongRTL = -1;
                    /* check if we have a pending conditional segment */
                    level = (byte)impTab[oldStateSeq][IMPTABLEVELS_RES];
                    if ((level & 1) != 0 && levState.startON > 0) { /* after ON */
                        start = levState.startON;   /* reset to basic run level */
                    }
                    if (_prop == _S) {              /* add LRM before S */
                        addPoint(start0, LRM_BEFORE);
                        insertPoints.confirmed = insertPoints.size;
                    }
                    break;
                }
                /* reset previous RTL cont to level for LTR text */
                for (k = levState.lastStrongRTL + 1; k < start0; k++) {
                    /* reset odd level, leave runLevel+2 as is */
                    levels[k] = (byte)((levels[k] - 2) & ~1);
                }
                /* mark insert points as confirmed */
                insertPoints.confirmed = insertPoints.size;
                levState.lastStrongRTL = -1;
                if (_prop == _S) {           /* add LRM before S */
                    addPoint(start0, LRM_BEFORE);
                    insertPoints.confirmed = insertPoints.size;
                }
                break;

            case 4:                     /* R/AL after possible relevant EN/AN */
                /* just clean up */
                if (insertPoints.points.length > 0)
                    /* remove all non confirmed insert points */
                    insertPoints.size = insertPoints.confirmed;
                levState.startON = -1;
                levState.startL2EN = -1;
                levState.lastStrongRTL = limit - 1;
                break;

            case 5:                     /* EN/AN after R/AL + possible cont */
                /* check for real AN */
                if ((_prop == _AN) && (NoContextRTL(dirProps[start0]) == AN)) {
                    /* real AN */
                    if (levState.startL2EN == -1) { /* if no relevant EN already found */
                        /* just note the righmost digit as a strong RTL */
                        levState.lastStrongRTL = limit - 1;
                        break;
                    }
                    if (levState.startL2EN >= 0)  { /* after EN, no AN */
                        addPoint(levState.startL2EN, LRM_BEFORE);
                        levState.startL2EN = -2;
                    }
                    /* note AN */
                    addPoint(start0, LRM_BEFORE);
                    break;
                }
                /* if first EN/AN after R/AL */
                if (levState.startL2EN == -1) {
                    levState.startL2EN = start0;
                }
                break;

            case 6:                     /* note location of latest R/AL */
                levState.lastStrongRTL = limit - 1;
                levState.startON = -1;
                break;

            case 7:                     /* L after R+ON/EN/AN */
                /* include possible adjacent number on the left */
                for (k = start0-1; k >= 0 && ((levels[k] & 1) == 0); k--) {
                }
                if (k >= 0) {
                    addPoint(k, RLM_BEFORE);    /* add RLM before */
                    insertPoints.confirmed = insertPoints.size; /* confirm it */
                }
                levState.startON = start0;
                break;

            case 8:                     /* AN after L */
                /* AN numbers between L text on both sides may be trouble. */
                /* tentatively bracket with LRMs; will be confirmed if followed by L */
                addPoint(start0, LRM_BEFORE);   /* add LRM before */
                addPoint(start0, LRM_AFTER);    /* add LRM after  */
                break;

            case 9:                     /* R after L+ON/EN/AN */
                /* false alert, infirm LRMs around previous AN */
                insertPoints.size=insertPoints.confirmed;
                if (_prop == _S) {          /* add RLM before S */
                    addPoint(start0, RLM_BEFORE);
                    insertPoints.confirmed = insertPoints.size;
                }
                break;

            case 10:                    /* L after L+ON/AN */
                level = (byte)(levState.runLevel + addLevel);
                for (k=levState.startON; k < start0; k++) {
                    if (levels[k] < level) {
                        levels[k] = level;
                    }
                }
                insertPoints.confirmed = insertPoints.size;   /* confirm inserts */
                levState.startON = start0;
                break;

            case 11:                    /* L after L+ON+EN/AN/ON */
                level = (byte)levState.runLevel;
                for (k = start0-1; k >= levState.startON; k--) {
                    if (levels[k] == level+3) {
                        while (levels[k] == level+3) {
                            levels[k--] -= 2;
                        }
                        while (levels[k] == level) {
                            k--;
                        }
                    }
                    if (levels[k] == level+2) {
                        levels[k] = level;
                        continue;
                    }
                    levels[k] = (byte)(level+1);
                }
                break;

            case 12:                    /* R after L+ON+EN/AN/ON */
                level = (byte)(levState.runLevel+1);
                for (k = start0-1; k >= levState.startON; k--) {
                    if (levels[k] > level) {
                        levels[k] -= 2;
                    }
                }
                break;

            default:                        /* we should never get here */
                throw new IllegalStateException("Internal ICU error in processPropertySeq");
            }
        }
        if ((addLevel) != 0 || (start < start0)) {
            level = (byte)(levState.runLevel + addLevel);
            for (k = start; k < limit; k++) {
                levels[k] = level;
            }
        }
    }

    private void resolveImplicitLevels(int start, int limit, short sor, short eor)
    {
        LevState levState = new LevState();
        int i, start1, start2;
        short oldStateImp, stateImp, actionImp;
        short gprop, resProp, cell;
        short nextStrongProp = R;
        int nextStrongPos = -1;


        /* check for RTL inverse Bidi mode */
        /* FOOD FOR THOUGHT: in case of RTL inverse Bidi, it would make sense to
         * loop on the text characters from end to start.
         * This would need a different properties state table (at least different
         * actions) and different levels state tables (maybe very similar to the
         * LTR corresponding ones.
         */
        /* initialize for levels state table */
        levState.startL2EN = -1;        /* used for INVERSE_LIKE_DIRECT_WITH_MARKS */
        levState.lastStrongRTL = -1;    /* used for INVERSE_LIKE_DIRECT_WITH_MARKS */
        levState.state = 0;
        levState.runLevel = levels[start];
        levState.impTab = impTabPair.imptab[levState.runLevel & 1];
        levState.impAct = impTabPair.impact[levState.runLevel & 1];
        processPropertySeq(levState, (short)sor, start, start);
        /* initialize for property state table */
        if (dirProps[start] == NSM) {
            stateImp = (short)(1 + sor);
        } else {
            stateImp = 0;
        }
        start1 = start;
        start2 = 0;

        for (i = start; i <= limit; i++) {
            if (i >= limit) {
                gprop = eor;
            } else {
                short prop, prop1;
                prop = NoContextRTL(dirProps[i]);
                gprop = groupProp[prop];
            }
            oldStateImp = stateImp;
            cell = impTabProps[oldStateImp][gprop];
            stateImp = GetStateProps(cell);     /* isolate the new state */
            actionImp = GetActionProps(cell);   /* isolate the action */
            if ((i == limit) && (actionImp == 0)) {
                /* there is an unprocessed sequence if its property == eor   */
                actionImp = 1;                  /* process the last sequence */
            }
            if (actionImp != 0) {
                resProp = impTabProps[oldStateImp][IMPTABPROPS_RES];
                switch (actionImp) {
                case 1:             /* process current seq1, init new seq1 */
                    processPropertySeq(levState, resProp, start1, i);
                    start1 = i;
                    break;
                case 2:             /* init new seq2 */
                    start2 = i;
                    break;
                case 3:             /* process seq1, process seq2, init new seq1 */
                    processPropertySeq(levState, resProp, start1, start2);
                    processPropertySeq(levState, _ON, start2, i);
                    start1 = i;
                    break;
                case 4:             /* process seq1, set seq1=seq2, init new seq2 */
                    processPropertySeq(levState, resProp, start1, start2);
                    start1 = start2;
                    start2 = i;
                    break;
                default:            /* we should never get here */
                    throw new IllegalStateException("Internal ICU error in resolveImplicitLevels");
                }
            }
        }
        /* flush possible pending sequence, e.g. ON */
        processPropertySeq(levState, (short)eor, limit, limit);
    }

    /* perform (L1) and (X9) ---------------------------------------------------- */

    /*
     * Reset the embedding levels for some non-graphic characters (L1).
     * This method also sets appropriate levels for BN, and
     * explicit embedding types that are supposed to have been removed
     * from the paragraph in (X9).
     */
    private void adjustWSLevels() {
        int i;

        if ((flags & MASK_WS) != 0) {
            int flag;
            i = trailingWSStart;
            while (i > 0) {
                /* reset a sequence of WS/BN before eop and B/S to the paragraph paraLevel */
                while (i > 0 && ((flag = DirPropFlagNC(dirProps[--i])) & MASK_WS) != 0) {
                    if (orderParagraphsLTR && (flag & DirPropFlag(B)) != 0) {
                        levels[i] = 0;
                    } else {
                        levels[i] = GetParaLevelAt(i);
                    }
                }

                /* reset BN to the next character's paraLevel until B/S, which restarts above loop */
                /* here, i+1 is guaranteed to be <length */
                while (i > 0) {
                    flag = DirPropFlagNC(dirProps[--i]);
                    if ((flag & MASK_BN_EXPLICIT) != 0) {
                        levels[i] = levels[i + 1];
                    } else if (orderParagraphsLTR && (flag & DirPropFlag(B)) != 0) {
                        levels[i] = 0;
                        break;
                    } else if ((flag & MASK_B_S) != 0){
                        levels[i] = GetParaLevelAt(i);
                        break;
                    }
                }
            }
        }
    }

    private int Bidi_Min(int x, int y) {
        return x < y ? x : y;
    }

    private int Bidi_Abs(int x) {
        return x >= 0 ? x : -x;
    }

    /**
     * Perform the Unicode Bidi algorithm. It is defined in the
     * <a href="http://www.unicode.org/unicode/reports/tr9/">Unicode Standard Annex #9</a>,
     * version 13,
     * also described in The Unicode Standard, Version 4.0 .<p>
     *
     * This method takes a piece of plain text containing one or more paragraphs,
     * with or without externally specified embedding levels from <i>styled</i>
     * text and computes the left-right-directionality of each character.<p>
     *
     * If the entire text is all of the same directionality, then
     * the method may not perform all the steps described by the algorithm,
     * i.e., some levels may not be the same as if all steps were performed.
     * This is not relevant for unidirectional text.<br>
     * For example, in pure LTR text with numbers the numbers would get
     * a resolved level of 2 higher than the surrounding text according to
     * the algorithm. This implementation may set all resolved levels to
     * the same value in such a case.<p>
     *
     * The text can be composed of multiple paragraphs. Occurrence of a block
     * separator in the text terminates a paragraph, and whatever comes next starts
     * a new paragraph. The exception to this rule is when a Carriage Return (CR)
     * is followed by a Line Feed (LF). Both CR and LF are block separators, but
     * in that case, the pair of characters is considered as terminating the
     * preceding paragraph, and a new paragraph will be started by a character
     * coming after the LF.
     *
     * Although the text is passed here as a <code>String</code>, it is
     * stored internally as an array of characters. Therefore the
     * documentation will refer to indexes of the characters in the text.
     *
     * @param text contains the text that the Bidi algorithm will be performed
     *        on. This text can be retrieved with <code>getText()</code> or
     *        <code>getTextAsString</code>.<br>
     *
     * @param paraLevel specifies the default level for the text;
     *        it is typically 0 (LTR) or 1 (RTL).
     *        If the method shall determine the paragraph level from the text,
     *        then <code>paraLevel</code> can be set to
     *        either <code>LEVEL_DEFAULT_LTR</code>
     *        or <code>LEVEL_DEFAULT_RTL</code>; if the text contains multiple
     *        paragraphs, the paragraph level shall be determined separately for
     *        each paragraph; if a paragraph does not include any strongly typed
     *        character, then the desired default is used (0 for LTR or 1 for RTL).
     *        Any other value between 0 and <code>MAX_EXPLICIT_LEVEL</code>
     *        is also valid, with odd levels indicating RTL.
     *
     * @param embeddingLevels (in) may be used to preset the embedding and override levels,
     *        ignoring characters like LRE and PDF in the text.
     *        A level overrides the directional property of its corresponding
     *        (same index) character if the level has the
     *        <code>LEVEL_OVERRIDE</code> bit set.<br><br>
     *        Except for that bit, it must be
     *        <code>paraLevel<=embeddingLevels[]<=MAX_EXPLICIT_LEVEL</code>,
     *        with one exception: a level of zero may be specified for a
     *        paragraph separator even if <code>paraLevel&gt;0</code> when multiple
     *        paragraphs are submitted in the same call to <code>setPara()</code>.<br><br>
     *        <strong>Caution: </strong>A reference to this array, not a copy
     *        of the levels, will be stored in the <code>Bidi</code> object;
     *        the <code>embeddingLevels</code>
     *        should not be modified to avoid unexpected results on subsequent
     *        Bidi operations. However, the <code>setPara()</code> and
     *        <code>setLine()</code> methods may modify some or all of the
     *        levels.<br><br>
     *        <strong>Note:</strong> the <code>embeddingLevels</code> array must
     *        have one entry for each character in <code>text</code>.
     *
     * @throws IllegalArgumentException if the values in embeddingLevels are
     *         not within the allowed range
     *
     * @see #LEVEL_DEFAULT_LTR
     * @see #LEVEL_DEFAULT_RTL
     * @see #LEVEL_OVERRIDE
     * @see #MAX_EXPLICIT_LEVEL
     * @stable ICU 3.8
     */
    void setPara(String text, byte paraLevel, byte[] embeddingLevels)
    {
        if (text == null) {
            setPara(new char[0], paraLevel, embeddingLevels);
        } else {
            setPara(text.toCharArray(), paraLevel, embeddingLevels);
        }
    }

    /**
     * Perform the Unicode Bidi algorithm. It is defined in the
     * <a href="http://www.unicode.org/unicode/reports/tr9/">Unicode Standard Annex #9</a>,
     * version 13,
     * also described in The Unicode Standard, Version 4.0 .<p>
     *
     * This method takes a piece of plain text containing one or more paragraphs,
     * with or without externally specified embedding levels from <i>styled</i>
     * text and computes the left-right-directionality of each character.<p>
     *
     * If the entire text is all of the same directionality, then
     * the method may not perform all the steps described by the algorithm,
     * i.e., some levels may not be the same as if all steps were performed.
     * This is not relevant for unidirectional text.<br>
     * For example, in pure LTR text with numbers the numbers would get
     * a resolved level of 2 higher than the surrounding text according to
     * the algorithm. This implementation may set all resolved levels to
     * the same value in such a case.<p>
     *
     * The text can be composed of multiple paragraphs. Occurrence of a block
     * separator in the text terminates a paragraph, and whatever comes next starts
     * a new paragraph. The exception to this rule is when a Carriage Return (CR)
     * is followed by a Line Feed (LF). Both CR and LF are block separators, but
     * in that case, the pair of characters is considered as terminating the
     * preceding paragraph, and a new paragraph will be started by a character
     * coming after the LF.
     *
     * The text is stored internally as an array of characters. Therefore the
     * documentation will refer to indexes of the characters in the text.
     *
     * @param chars contains the text that the Bidi algorithm will be performed
     *        on. This text can be retrieved with <code>getText()</code> or
     *        <code>getTextAsString</code>.<br>
     *
     * @param paraLevel specifies the default level for the text;
     *        it is typically 0 (LTR) or 1 (RTL).
     *        If the method shall determine the paragraph level from the text,
     *        then <code>paraLevel</code> can be set to
     *        either <code>LEVEL_DEFAULT_LTR</code>
     *        or <code>LEVEL_DEFAULT_RTL</code>; if the text contains multiple
     *        paragraphs, the paragraph level shall be determined separately for
     *        each paragraph; if a paragraph does not include any strongly typed
     *        character, then the desired default is used (0 for LTR or 1 for RTL).
     *        Any other value between 0 and <code>MAX_EXPLICIT_LEVEL</code>
     *        is also valid, with odd levels indicating RTL.
     *
     * @param embeddingLevels (in) may be used to preset the embedding and
     *        override levels, ignoring characters like LRE and PDF in the text.
     *        A level overrides the directional property of its corresponding
     *        (same index) character if the level has the
     *        <code>LEVEL_OVERRIDE</code> bit set.<br><br>
     *        Except for that bit, it must be
     *        <code>paraLevel<=embeddingLevels[]<=MAX_EXPLICIT_LEVEL</code>,
     *        with one exception: a level of zero may be specified for a
     *        paragraph separator even if <code>paraLevel&gt;0</code> when multiple
     *        paragraphs are submitted in the same call to <code>setPara()</code>.<br><br>
     *        <strong>Caution: </strong>A reference to this array, not a copy
     *        of the levels, will be stored in the <code>Bidi</code> object;
     *        the <code>embeddingLevels</code>
     *        should not be modified to avoid unexpected results on subsequent
     *        Bidi operations. However, the <code>setPara()</code> and
     *        <code>setLine()</code> methods may modify some or all of the
     *        levels.<br><br>
     *        <strong>Note:</strong> the <code>embeddingLevels</code> array must
     *        have one entry for each character in <code>text</code>.
     *
     * @throws IllegalArgumentException if the values in embeddingLevels are
     *         not within the allowed range
     *
     * @see #LEVEL_DEFAULT_LTR
     * @see #LEVEL_DEFAULT_RTL
     * @see #LEVEL_OVERRIDE
     * @see #MAX_EXPLICIT_LEVEL
     * @stable ICU 3.8
     */
    public void setPara(char[] chars, byte paraLevel, byte[] embeddingLevels)
    {
        /* check the argument values */
        if (paraLevel < INTERNAL_LEVEL_DEFAULT_LTR) {
            verifyRange(paraLevel, 0, MAX_EXPLICIT_LEVEL + 1);
        }
        if (chars == null) {
            chars = new char[0];
        }

        /* initialize the Bidi object */
        this.paraBidi = null;          /* mark unfinished setPara */
        this.text = chars;
        this.length = this.originalLength = this.resultLength = text.length;
        this.paraLevel = paraLevel;
        this.direction = Bidi.DIRECTION_LEFT_TO_RIGHT;
        this.paraCount = 1;

        /* Allocate zero-length arrays instead of setting to null here; then
         * checks for null in various places can be eliminated.
         */
        dirProps = new byte[0];
        levels = new byte[0];
        runs = new BidiRun[0];
        isGoodLogicalToVisualRunsMap = false;
        insertPoints.size = 0;          /* clean up from last call */
        insertPoints.confirmed = 0;     /* clean up from last call */

        /*
         * Save the original paraLevel if contextual; otherwise, set to 0.
         */
        if (IsDefaultLevel(paraLevel)) {
            defaultParaLevel = paraLevel;
        } else {
            defaultParaLevel = 0;
        }

        if (length == 0) {
            /*
             * For an empty paragraph, create a Bidi object with the paraLevel and
             * the flags and the direction set but without allocating zero-length arrays.
             * There is nothing more to do.
             */
            if (IsDefaultLevel(paraLevel)) {
                this.paraLevel &= 1;
                defaultParaLevel = 0;
            }
            if ((this.paraLevel & 1) != 0) {
                flags = DirPropFlag(R);
                direction = Bidi.DIRECTION_RIGHT_TO_LEFT;
            } else {
                flags = DirPropFlag(L);
                direction = Bidi.DIRECTION_LEFT_TO_RIGHT;
            }

            runCount = 0;
            paraCount = 0;
            paraBidi = this;         /* mark successful setPara */
            return;
        }

        runCount = -1;

        /*
         * Get the directional properties,
         * the flags bit-set, and
         * determine the paragraph level if necessary.
         */
        getDirPropsMemory(length);
        dirProps = dirPropsMemory;
        getDirProps();

        /* the processed length may have changed if OPTION_STREAMING is set */
        trailingWSStart = length;  /* the levels[] will reflect the WS run */

        /* allocate paras memory */
        if (paraCount > 1) {
            getInitialParasMemory(paraCount);
            paras = parasMemory;
            paras[paraCount - 1] = length;
        } else {
            /* initialize paras for single paragraph */
            paras = simpleParas;
            simpleParas[0] = length;
        }

        /* are explicit levels specified? */
        if (embeddingLevels == null) {
            /* no: determine explicit levels according to the (Xn) rules */
            getLevelsMemory(length);
            levels = levelsMemory;
            direction = resolveExplicitLevels();
        } else {
            /* set BN for all explicit codes, check that all levels are 0 or paraLevel..MAX_EXPLICIT_LEVEL */
            levels = embeddingLevels;
            direction = checkExplicitLevels();
        }

        /*
         * The steps after (X9) in the Bidi algorithm are performed only if
         * the paragraph text has mixed directionality!
         */
        switch (direction) {
        case Bidi.DIRECTION_LEFT_TO_RIGHT:
            /* make sure paraLevel is even */
            paraLevel = (byte)((paraLevel + 1) & ~1);

            /* all levels are implicitly at paraLevel (important for getLevels()) */
            trailingWSStart = 0;
            break;
        case Bidi.DIRECTION_RIGHT_TO_LEFT:
            /* make sure paraLevel is odd */
            paraLevel |= 1;

            /* all levels are implicitly at paraLevel (important for getLevels()) */
            trailingWSStart = 0;
            break;
        default:
            this.impTabPair = impTab_DEFAULT;

            /*
             * If there are no external levels specified and there
             * are no significant explicit level codes in the text,
             * then we can treat the entire paragraph as one run.
             * Otherwise, we need to perform the following rules on runs of
             * the text with the same embedding levels. (X10)
             * "Significant" explicit level codes are ones that actually
             * affect non-BN characters.
             * Examples for "insignificant" ones are empty embeddings
             * LRE-PDF, LRE-RLE-PDF-PDF, etc.
             */
            if (embeddingLevels == null && paraCount <= 1 &&
                (flags & DirPropFlagMultiRuns) == 0) {
                resolveImplicitLevels(0, length,
                        GetLRFromLevel(GetParaLevelAt(0)),
                        GetLRFromLevel(GetParaLevelAt(length - 1)));
            } else {
                /* sor, eor: start and end types of same-level-run */
                int start, limit = 0;
                byte level, nextLevel;
                short sor, eor;

                /* determine the first sor and set eor to it because of the loop body (sor=eor there) */
                level = GetParaLevelAt(0);
                nextLevel = levels[0];
                if (level < nextLevel) {
                    eor = GetLRFromLevel(nextLevel);
                } else {
                    eor = GetLRFromLevel(level);
                }

                do {
                    /* determine start and limit of the run (end points just behind the run) */

                    /* the values for this run's start are the same as for the previous run's end */
                    start = limit;
                    level = nextLevel;
                    if ((start > 0) && (NoContextRTL(dirProps[start - 1]) == B)) {
                        /* except if this is a new paragraph, then set sor = para level */
                        sor = GetLRFromLevel(GetParaLevelAt(start));
                    } else {
                        sor = eor;
                    }

                    /* search for the limit of this run */
                    while (++limit < length && levels[limit] == level) {}

                    /* get the correct level of the next run */
                    if (limit < length) {
                        nextLevel = levels[limit];
                    } else {
                        nextLevel = GetParaLevelAt(length - 1);
                    }

                    /* determine eor from max(level, nextLevel); sor is last run's eor */
                    if ((level & ~INTERNAL_LEVEL_OVERRIDE) < (nextLevel & ~INTERNAL_LEVEL_OVERRIDE)) {
                        eor = GetLRFromLevel(nextLevel);
                    } else {
                        eor = GetLRFromLevel(level);
                    }

                    /* if the run consists of overridden directional types, then there
                       are no implicit types to be resolved */
                    if ((level & INTERNAL_LEVEL_OVERRIDE) == 0) {
                        resolveImplicitLevels(start, limit, sor, eor);
                    } else {
                        /* remove the LEVEL_OVERRIDE flags */
                        do {
                            levels[start++] &= ~INTERNAL_LEVEL_OVERRIDE;
                        } while (start < limit);
                    }
                } while (limit  < length);
            }

            /* reset the embedding levels for some non-graphic characters (L1), (X9) */
            adjustWSLevels();

            break;
        }

        resultLength += insertPoints.size;
        paraBidi = this;             /* mark successful setPara */
    }

    /**
     * Perform the Unicode Bidi algorithm on a given paragraph, as defined in the
     * <a href="http://www.unicode.org/unicode/reports/tr9/">Unicode Standard Annex #9</a>,
     * version 13,
     * also described in The Unicode Standard, Version 4.0 .<p>
     *
     * This method takes a paragraph of text and computes the
     * left-right-directionality of each character. The text should not
     * contain any Unicode block separators.<p>
     *
     * The RUN_DIRECTION attribute in the text, if present, determines the base
     * direction (left-to-right or right-to-left). If not present, the base
     * direction is computed using the Unicode Bidirectional Algorithm,
     * defaulting to left-to-right if there are no strong directional characters
     * in the text. This attribute, if present, must be applied to all the text
     * in the paragraph.<p>
     *
     * The BIDI_EMBEDDING attribute in the text, if present, represents
     * embedding level information. Negative values from -1 to -62 indicate
     * overrides at the absolute value of the level. Positive values from 1 to
     * 62 indicate embeddings. Where values are zero or not defined, the base
     * embedding level as determined by the base direction is assumed.<p>
     *
     * The NUMERIC_SHAPING attribute in the text, if present, converts European
     * digits to other decimal digits before running the bidi algorithm. This
     * attribute, if present, must be applied to all the text in the paragraph.
     *
     * If the entire text is all of the same directionality, then
     * the method may not perform all the steps described by the algorithm,
     * i.e., some levels may not be the same as if all steps were performed.
     * This is not relevant for unidirectional text.<br>
     * For example, in pure LTR text with numbers the numbers would get
     * a resolved level of 2 higher than the surrounding text according to
     * the algorithm. This implementation may set all resolved levels to
     * the same value in such a case.<p>
     *
     * @param paragraph a paragraph of text with optional character and
     *        paragraph attribute information
     * @stable ICU 3.8
     */
    public void setPara(AttributedCharacterIterator paragraph)
    {
        byte paraLvl;
        Boolean runDirection =
            (Boolean) paragraph.getAttribute(TextAttributeConstants.RUN_DIRECTION);
        Object shaper = paragraph.getAttribute(TextAttributeConstants.NUMERIC_SHAPING);
        if (runDirection == null) {
            paraLvl = INTERNAL_LEVEL_DEFAULT_LTR;
        } else {
            paraLvl = (runDirection.equals(TextAttributeConstants.RUN_DIRECTION_LTR)) ?
                        (byte)Bidi.DIRECTION_LEFT_TO_RIGHT : (byte)Bidi.DIRECTION_RIGHT_TO_LEFT;
        }

        byte[] lvls = null;
        int len = paragraph.getEndIndex() - paragraph.getBeginIndex();
        byte[] embeddingLevels = new byte[len];
        char[] txt = new char[len];
        int i = 0;
        char ch = paragraph.first();
        while (ch != AttributedCharacterIterator.DONE) {
            txt[i] = ch;
            Integer embedding =
                (Integer) paragraph.getAttribute(TextAttributeConstants.BIDI_EMBEDDING);
            if (embedding != null) {
                byte level = embedding.byteValue();
                if (level == 0) {
                    /* no-op */
                } else if (level < 0) {
                    lvls = embeddingLevels;
                    embeddingLevels[i] = (byte)((0 - level) | INTERNAL_LEVEL_OVERRIDE);
                } else {
                    lvls = embeddingLevels;
                    embeddingLevels[i] = level;
                }
            }
            ch = paragraph.next();
            ++i;
        }

        if (shaper != null) {
            NumericShapings.shape(shaper, txt, 0, len);
        }
        setPara(txt, paraLvl, lvls);
    }

    /**
     * Specify whether block separators must be allocated level zero,
     * so that successive paragraphs will progress from left to right.
     * This method must be called before <code>setPara()</code>.
     * Paragraph separators (B) may appear in the text.  Setting them to level zero
     * means that all paragraph separators (including one possibly appearing
     * in the last text position) are kept in the reordered text after the text
     * that they follow in the source text.
     * When this feature is not enabled, a paragraph separator at the last
     * position of the text before reordering will go to the first position
     * of the reordered text when the paragraph level is odd.
     *
     * @param ordarParaLTR specifies whether paragraph separators (B) must
     * receive level 0, so that successive paragraphs progress from left to right.
     *
     * @see #setPara
     * @stable ICU 3.8
     */
    private void orderParagraphsLTR(boolean ordarParaLTR) {
        orderParagraphsLTR = ordarParaLTR;
    }

    /**
     * Get the directionality of the text.
     *
     * @return a value of <code>LTR</code>, <code>RTL</code> or <code>MIXED</code>
     *         that indicates if the entire text
     *         represented by this object is unidirectional,
     *         and which direction, or if it is mixed-directional.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     *
     * @see #LTR
     * @see #RTL
     * @see #MIXED
     * @stable ICU 3.8
     */
    private byte getDirection()
    {
        verifyValidParaOrLine();
        return direction;
    }

    /**
     * Get the length of the text.
     *
     * @return The length of the text that the <code>Bidi</code> object was
     *         created for.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @stable ICU 3.8
     */
    public int getLength()
    {
        verifyValidParaOrLine();
        return originalLength;
    }

    /* paragraphs API methods ------------------------------------------------- */

    /**
     * Get the paragraph level of the text.
     *
     * @return The paragraph level. If there are multiple paragraphs, their
     *         level may vary if the required paraLevel is LEVEL_DEFAULT_LTR or
     *         LEVEL_DEFAULT_RTL.  In that case, the level of the first paragraph
     *         is returned.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     *
     * @see #LEVEL_DEFAULT_LTR
     * @see #LEVEL_DEFAULT_RTL
     * @see #getParagraph
     * @see #getParagraphByIndex
     * @stable ICU 3.8
     */
    public byte getParaLevel()
    {
        verifyValidParaOrLine();
        return paraLevel;
    }

    /**
     * Get the index of a paragraph, given a position within the text.<p>
     *
     * @param charIndex is the index of a character within the text, in the
     *        range <code>[0..getProcessedLength()-1]</code>.
     *
     * @return The index of the paragraph containing the specified position,
     *         starting from 0.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @throws IllegalArgumentException if charIndex is not within the legal range
     *
     * @see com.ibm.icu.text.BidiRun
     * @see #getProcessedLength
     * @stable ICU 3.8
     */
    public int getParagraphIndex(int charIndex)
    {
        verifyValidParaOrLine();
        BidiBase bidi = paraBidi;             /* get Para object if Line object */
        verifyRange(charIndex, 0, bidi.length);
        int paraIndex;
        for (paraIndex = 0; charIndex >= bidi.paras[paraIndex]; paraIndex++) {
        }
        return paraIndex;
    }

    /**
     * <code>setLine()</code> returns a <code>Bidi</code> object to
     * contain the reordering information, especially the resolved levels,
     * for all the characters in a line of text. This line of text is
     * specified by referring to a <code>Bidi</code> object representing
     * this information for a piece of text containing one or more paragraphs,
     * and by specifying a range of indexes in this text.<p>
     * In the new line object, the indexes will range from 0 to <code>limit-start-1</code>.<p>
     *
     * This is used after calling <code>setPara()</code>
     * for a piece of text, and after line-breaking on that text.
     * It is not necessary if each paragraph is treated as a single line.<p>
     *
     * After line-breaking, rules (L1) and (L2) for the treatment of
     * trailing WS and for reordering are performed on
     * a <code>Bidi</code> object that represents a line.<p>
     *
     * <strong>Important: </strong>the line <code>Bidi</code> object may
     * reference data within the global text <code>Bidi</code> object.
     * You should not alter the content of the global text object until
     * you are finished using the line object.
     *
     * @param start is the line's first index into the text.
     *
     * @param limit is just behind the line's last index into the text
     *        (its last index +1).
     *
     * @return a <code>Bidi</code> object that will now represent a line of the text.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code>
     * @throws IllegalArgumentException if start and limit are not in the range
     *         <code>0&lt;=start&lt;limit&lt;=getProcessedLength()</code>,
     *         or if the specified line crosses a paragraph boundary
     *
     * @see #setPara
     * @see #getProcessedLength
     * @stable ICU 3.8
     */
    public Bidi setLine(Bidi bidi, BidiBase bidiBase, Bidi newBidi, BidiBase newBidiBase, int start, int limit)
    {
        verifyValidPara();
        verifyRange(start, 0, limit);
        verifyRange(limit, 0, length+1);
        if (getParagraphIndex(start) != getParagraphIndex(limit - 1)) {
            /* the line crosses a paragraph boundary */
            throw new IllegalArgumentException();
        }

        return BidiLine.setLine(bidi, this, newBidi, newBidiBase, start, limit);
    }

    /**
     * Get the level for one character.
     *
     * @param charIndex the index of a character.
     *
     * @return The level for the character at <code>charIndex</code>.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @throws IllegalArgumentException if charIndex is not in the range
     *         <code>0&lt;=charIndex&lt;getProcessedLength()</code>
     *
     * @see #getProcessedLength
     * @stable ICU 3.8
     */
    public byte getLevelAt(int charIndex)
    {
        if (charIndex < 0 || charIndex >= length) {
            return (byte)getBaseLevel();
        }
        verifyValidParaOrLine();
        verifyRange(charIndex, 0, length);
        return BidiLine.getLevelAt(this, charIndex);
    }

    /**
     * Get an array of levels for each character.<p>
     *
     * Note that this method may allocate memory under some
     * circumstances, unlike <code>getLevelAt()</code>.
     *
     * @return The levels array for the text,
     *         or <code>null</code> if an error occurs.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @stable ICU 3.8
     */
    private byte[] getLevels()
    {
        verifyValidParaOrLine();
        if (length <= 0) {
            return new byte[0];
        }
        return BidiLine.getLevels(this);
    }

    /**
     * Get the number of runs.
     * This method may invoke the actual reordering on the
     * <code>Bidi</code> object, after <code>setPara()</code>
     * may have resolved only the levels of the text. Therefore,
     * <code>countRuns()</code> may have to allocate memory,
     * and may throw an exception if it fails to do so.
     *
     * @return The number of runs.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @stable ICU 3.8
     */
    public int countRuns()
    {
        verifyValidParaOrLine();
        BidiLine.getRuns(this);
        return runCount;
    }

    /**
     * Get a visual-to-logical index map (array) for the characters in the
     * <code>Bidi</code> (paragraph or line) object.
     * <p>
     * Some values in the map may be <code>MAP_NOWHERE</code> if the
     * corresponding text characters are Bidi marks inserted in the visual
     * output by the option <code>OPTION_INSERT_MARKS</code>.
     * <p>
     * When the visual output is altered by using options of
     * <code>writeReordered()</code> such as <code>INSERT_LRM_FOR_NUMERIC</code>,
     * <code>KEEP_BASE_COMBINING</code>, <code>OUTPUT_REVERSE</code>,
     * <code>REMOVE_BIDI_CONTROLS</code>, the logical positions returned may not
     * be correct. It is advised to use, when possible, reordering options
     * such as {@link #OPTION_INSERT_MARKS} and {@link #OPTION_REMOVE_CONTROLS}.
     *
     * @return an array of <code>getResultLength()</code>
     *        indexes which will reflect the reordering of the characters.<br><br>
     *        The index map will result in
     *        <code>indexMap[visualIndex]==logicalIndex</code>, where
     *        <code>indexMap</code> represents the returned array.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     *
     * @see #getLogicalMap
     * @see #getLogicalIndex
     * @see #getResultLength
     * @see #MAP_NOWHERE
     * @see #OPTION_INSERT_MARKS
     * @see #writeReordered
     * @stable ICU 3.8
     */
    private int[] getVisualMap()
    {
        /* countRuns() checks successful call to setPara/setLine */
        countRuns();
        if (resultLength <= 0) {
            return new int[0];
        }
        return BidiLine.getVisualMap(this);
    }

    /**
     * This is a convenience method that does not use a <code>Bidi</code> object.
     * It is intended to be used for when an application has determined the levels
     * of objects (character sequences) and just needs to have them reordered (L2).
     * This is equivalent to using <code>getVisualMap()</code> on a
     * <code>Bidi</code> object.
     *
     * @param levels is an array of levels that have been determined by
     *        the application.
     *
     * @return an array of <code>levels.length</code>
     *        indexes which will reflect the reordering of the characters.<p>
     *        The index map will result in
     *        <code>indexMap[visualIndex]==logicalIndex</code>, where
     *        <code>indexMap</code> represents the returned array.
     *
     * @stable ICU 3.8
     */
    private static int[] reorderVisual(byte[] levels)
    {
        return BidiLine.reorderVisual(levels);
    }

    /**
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode Bidirectional
     * Algorithm. If no strong directional character is present, the base
     * direction is left-to-right.
     * @stable ICU 3.8
     */
    private static final int INTERNAL_DIRECTION_DEFAULT_LEFT_TO_RIGHT = 0x7e;

    /**
     * Constant indicating that the base direction depends on the first strong
     * directional character in the text according to the Unicode Bidirectional
     * Algorithm. If no strong directional character is present, the base
     * direction is right-to-left.
     * @stable ICU 3.8
     */
    private static final int INTERMAL_DIRECTION_DEFAULT_RIGHT_TO_LEFT = 0x7f;

    /**
     * Create Bidi from the given text, embedding, and direction information.
     * The embeddings array may be null. If present, the values represent
     * embedding level information. Negative values from -1 to -61 indicate
     * overrides at the absolute value of the level. Positive values from 1 to
     * 61 indicate embeddings. Where values are zero, the base embedding level
     * as determined by the base direction is assumed.<p>
     *
     * Note: this constructor calls setPara() internally.
     *
     * @param text an array containing the paragraph of text to process.
     * @param textStart the index into the text array of the start of the
     *        paragraph.
     * @param embeddings an array containing embedding values for each character
     *        in the paragraph. This can be null, in which case it is assumed
     *        that there is no external embedding information.
     * @param embStart the index into the embedding array of the start of the
     *        paragraph.
     * @param paragraphLength the length of the paragraph in the text and
     *        embeddings arrays.
     * @param flags a collection of flags that control the algorithm. The
     *        algorithm understands the flags DIRECTION_LEFT_TO_RIGHT,
     *        DIRECTION_RIGHT_TO_LEFT, DIRECTION_DEFAULT_LEFT_TO_RIGHT, and
     *        DIRECTION_DEFAULT_RIGHT_TO_LEFT. Other values are reserved.
     *
     * @throws IllegalArgumentException if the values in embeddings are
     *         not within the allowed range
     *
     * @see #DIRECTION_LEFT_TO_RIGHT
     * @see #DIRECTION_RIGHT_TO_LEFT
     * @see #DIRECTION_DEFAULT_LEFT_TO_RIGHT
     * @see #DIRECTION_DEFAULT_RIGHT_TO_LEFT
     * @stable ICU 3.8
     */
    public BidiBase(char[] text,
             int textStart,
             byte[] embeddings,
             int embStart,
             int paragraphLength,
             int flags)
     {
        this(0, 0);
        byte paraLvl;
        switch (flags) {
        case Bidi.DIRECTION_LEFT_TO_RIGHT:
        default:
            paraLvl = Bidi.DIRECTION_LEFT_TO_RIGHT;
            break;
        case Bidi.DIRECTION_RIGHT_TO_LEFT:
            paraLvl = Bidi.DIRECTION_RIGHT_TO_LEFT;
            break;
        case Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT:
            paraLvl = INTERNAL_LEVEL_DEFAULT_LTR;
            break;
        case Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT:
            paraLvl = INTERNAL_LEVEL_DEFAULT_RTL;
            break;
        }
        byte[] paraEmbeddings;
        if (embeddings == null) {
            paraEmbeddings = null;
        } else {
            paraEmbeddings = new byte[paragraphLength];
            byte lev;
            for (int i = 0; i < paragraphLength; i++) {
                lev = embeddings[i + embStart];
                if (lev < 0) {
                    lev = (byte)((- lev) | INTERNAL_LEVEL_OVERRIDE);
                } else if (lev == 0) {
                    lev = paraLvl;
                    if (paraLvl > MAX_EXPLICIT_LEVEL) {
                        lev &= 1;
                    }
                }
                paraEmbeddings[i] = lev;
            }
        }
        if (textStart == 0 && embStart == 0 && paragraphLength == text.length) {
            setPara(text, paraLvl, paraEmbeddings);
        } else {
            char[] paraText = new char[paragraphLength];
            System.arraycopy(text, textStart, paraText, 0, paragraphLength);
            setPara(paraText, paraLvl, paraEmbeddings);
        }
    }

    /**
     * Return true if the line is not left-to-right or right-to-left. This means
     * it either has mixed runs of left-to-right and right-to-left text, or the
     * base direction differs from the direction of the only run of text.
     *
     * @return true if the line is not left-to-right or right-to-left.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code>
     * @stable ICU 3.8
     */
    public boolean isMixed()
    {
        return (!isLeftToRight() && !isRightToLeft());
    }

    /**
    * Return true if the line is all left-to-right text and the base direction
     * is left-to-right.
     *
     * @return true if the line is all left-to-right text and the base direction
     *         is left-to-right.
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code>
     * @stable ICU 3.8
     */
    public boolean isLeftToRight()
    {
        return (getDirection() == Bidi.DIRECTION_LEFT_TO_RIGHT && (paraLevel & 1) == 0);
    }

    /**
     * Return true if the line is all right-to-left text, and the base direction
     * is right-to-left
     *
     * @return true if the line is all right-to-left text, and the base
     *         direction is right-to-left
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code>
     * @stable ICU 3.8
     */
    public boolean isRightToLeft()
    {
        return (getDirection() == Bidi.DIRECTION_RIGHT_TO_LEFT && (paraLevel & 1) == 1);
    }

    /**
     * Return true if the base direction is left-to-right
     *
     * @return true if the base direction is left-to-right
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     *
     * @stable ICU 3.8
     */
    public boolean baseIsLeftToRight()
    {
        return (getParaLevel() == Bidi.DIRECTION_LEFT_TO_RIGHT);
    }

    /**
     * Return the base level (0 if left-to-right, 1 if right-to-left).
     *
     * @return the base level
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     *
     * @stable ICU 3.8
     */
    public int getBaseLevel()
    {
        return getParaLevel();
    }

    /**
     * Compute the logical to visual run mapping
     */
    private void getLogicalToVisualRunsMap()
    {
        if (isGoodLogicalToVisualRunsMap) {
            return;
        }
        int count = countRuns();
        if ((logicalToVisualRunsMap == null) ||
            (logicalToVisualRunsMap.length < count)) {
            logicalToVisualRunsMap = new int[count];
        }
        int i;
        long[] keys = new long[count];
        for (i = 0; i < count; i++) {
            keys[i] = ((long)(runs[i].start)<<32) + i;
        }
        Arrays.sort(keys);
        for (i = 0; i < count; i++) {
            logicalToVisualRunsMap[i] = (int)(keys[i] & 0x00000000FFFFFFFF);
        }
        keys = null;
        isGoodLogicalToVisualRunsMap = true;
    }

    /**
     * Return the level of the nth logical run in this line.
     *
     * @param run the index of the run, between 0 and <code>countRuns()-1</code>
     *
     * @return the level of the run
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @throws IllegalArgumentException if <code>run</code> is not in
     *         the range <code>0&lt;=run&lt;countRuns()</code>
     * @stable ICU 3.8
     */
    public int getRunLevel(int run)
    {
        verifyValidParaOrLine();
        BidiLine.getRuns(this);
        if (runCount == 1) {
            return getParaLevel();
        }
        verifyIndex(run, 0, runCount);
        getLogicalToVisualRunsMap();
        return runs[logicalToVisualRunsMap[run]].level;
    }

    /**
     * Return the index of the character at the start of the nth logical run in
     * this line, as an offset from the start of the line.
     *
     * @param run the index of the run, between 0 and <code>countRuns()</code>
     *
     * @return the start of the run
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @throws IllegalArgumentException if <code>run</code> is not in
     *         the range <code>0&lt;=run&lt;countRuns()</code>
     * @stable ICU 3.8
     */
    public int getRunStart(int run)
    {
        verifyValidParaOrLine();
        BidiLine.getRuns(this);
        if (runCount == 1) {
            return 0;
        } else if (run == runCount) {
            return length;
        }
        verifyIndex(run, 0, runCount);
        getLogicalToVisualRunsMap();
        return runs[logicalToVisualRunsMap[run]].start;
    }

    /**
     * Return the index of the character past the end of the nth logical run in
     * this line, as an offset from the start of the line. For example, this
     * will return the length of the line for the last run on the line.
     *
     * @param run the index of the run, between 0 and <code>countRuns()</code>
     *
     * @return the limit of the run
     *
     * @throws IllegalStateException if this call is not preceded by a successful
     *         call to <code>setPara</code> or <code>setLine</code>
     * @throws IllegalArgumentException if <code>run</code> is not in
     *         the range <code>0&lt;=run&lt;countRuns()</code>
     * @stable ICU 3.8
     */
    public int getRunLimit(int run)
    {
        verifyValidParaOrLine();
        BidiLine.getRuns(this);
        if (runCount == 1) {
            return length;
        }
        verifyIndex(run, 0, runCount);
        getLogicalToVisualRunsMap();
        int idx = logicalToVisualRunsMap[run];
        int len = idx == 0 ? runs[idx].limit :
                                runs[idx].limit - runs[idx-1].limit;
        return runs[idx].start + len;
    }

    /**
     * Return true if the specified text requires bidi analysis. If this returns
     * false, the text will display left-to-right. Clients can then avoid
     * constructing a Bidi object. Text in the Arabic Presentation Forms area of
     * Unicode is presumed to already be shaped and ordered for display, and so
     * will not cause this method to return true.
     *
     * @param text the text containing the characters to test
     * @param start the start of the range of characters to test
     * @param limit the limit of the range of characters to test
     *
     * @return true if the range of characters requires bidi analysis
     *
     * @stable ICU 3.8
     */
    public static boolean requiresBidi(char[] text,
            int start,
            int limit)
    {
        final int RTLMask = (1 << Bidi.DIRECTION_RIGHT_TO_LEFT |
                1 << AL |
                1 << RLE |
                1 << RLO |
                1 << AN);

        if (0 > start || start > limit || limit > text.length) {
            throw new IllegalArgumentException("Value start " + start +
                      " is out of range 0 to " + limit);
        }
        for (int i = start; i < limit; ++i) {
            if (Character.isHighSurrogate(text[i]) && i < (limit-1) &&
                Character.isLowSurrogate(text[i+1])) {
                if (((1 << UCharacter.getDirection(Character.codePointAt(text, i))) & RTLMask) != 0) {
                    return true;
                }
            } else if (((1 << UCharacter.getDirection(text[i])) & RTLMask) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reorder the objects in the array into visual order based on their levels.
     * This is a utility method to use when you have a collection of objects
     * representing runs of text in logical order, each run containing text at a
     * single level. The elements at <code>index</code> from
     * <code>objectStart</code> up to <code>objectStart + count</code> in the
     * objects array will be reordered into visual order assuming
     * each run of text has the level indicated by the corresponding element in
     * the levels array (at <code>index - objectStart + levelStart</code>).
     *
     * @param levels an array representing the bidi level of each object
     * @param levelStart the start position in the levels array
     * @param objects the array of objects to be reordered into visual order
     * @param objectStart the start position in the objects array
     * @param count the number of objects to reorder
     * @stable ICU 3.8
     */
    public static void reorderVisually(byte[] levels,
            int levelStart,
            Object[] objects,
            int objectStart,
            int count)
    {
        if (0 > levelStart || levels.length <= levelStart) {
            throw new IllegalArgumentException("Value levelStart " +
                      levelStart + " is out of range 0 to " +
                      (levels.length-1));
        }
        if (0 > objectStart || objects.length <= objectStart) {
            throw new IllegalArgumentException("Value objectStart " +
                      levelStart + " is out of range 0 to " +
                      (objects.length-1));
        }
        if (0 > count || objects.length < (objectStart+count)) {
            throw new IllegalArgumentException("Value count " +
                      levelStart + " is out of range 0 to " +
                      (objects.length - objectStart));
        }
        byte[] reorderLevels = new byte[count];
        System.arraycopy(levels, levelStart, reorderLevels, 0, count);
        int[] indexMap = reorderVisual(reorderLevels);
        Object[] temp = new Object[count];
        System.arraycopy(objects, objectStart, temp, 0, count);
        for (int i = 0; i < count; ++i) {
            objects[objectStart + i] = temp[indexMap[i]];
        }
    }

    /**
     * Display the bidi internal state, used in debugging.
     */
    public String toString() {
        StringBuffer buf = new StringBuffer(super.toString());

        buf.append("[dir: " + direction);
        buf.append(" baselevel: " + paraLevel);
        buf.append(" length: " + length);
        buf.append(" runs: ");
        if (levels == null) {
            buf.append("null");
        } else {
            buf.append('[');
            buf.append(levels[0]);
            for (int i = 0; i < levels.length; i++) {
                buf.append(' ');
                buf.append(levels[i]);
            }
            buf.append(']');
        }
        buf.append(" text: [0x");
        buf.append(Integer.toHexString(text[0]));
        for (int i = 0; i < text.length; i++) {
            buf.append(" 0x");
            buf.append(Integer.toHexString(text[i]));
        }
        buf.append(']');
        buf.append(']');

        return buf.toString();
    }

    /**
     * A class that provides access to constants defined by
     * java.awt.font.TextAttribute without creating a static dependency.
     */
    private static class TextAttributeConstants {
        private static final Class<?> clazz = getClass("java.awt.font.TextAttribute");

        /**
         * TextAttribute instances (or a fake Attribute type if
         * java.awt.font.TextAttribute is not present)
         */
        static final AttributedCharacterIterator.Attribute RUN_DIRECTION =
            getTextAttribute("RUN_DIRECTION");
        static final Boolean RUN_DIRECTION_LTR =
            (Boolean)getStaticField(clazz, "RUN_DIRECTION_LTR");
        static final AttributedCharacterIterator.Attribute NUMERIC_SHAPING =
            getTextAttribute("NUMERIC_SHAPING");
        static final AttributedCharacterIterator.Attribute BIDI_EMBEDDING =
            getTextAttribute("BIDI_EMBEDDING");

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, null);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Object getStaticField(Class<?> clazz, String name) {
            if (clazz == null) {
                // fake attribute
                return new AttributedCharacterIterator.Attribute(name) { };
            } else {
                try {
                    Field f = clazz.getField(name);
                    return f.get(null);
                } catch (NoSuchFieldException x) {
                    throw new AssertionError(x);
                } catch (IllegalAccessException x) {
                    throw new AssertionError(x);
                }
            }
        }

        private static AttributedCharacterIterator.Attribute
            getTextAttribute(String name)
        {
            return (AttributedCharacterIterator.Attribute)getStaticField(clazz, name);
        }
    }

    /**
     * A class that provides access to java.awt.font.NumericShaping without
     * creating a static dependency.
     */
    private static class NumericShapings {
        private static final Class<?> clazz =
            getClass("java.awt.font.NumericShaper");
        private static final Method shapeMethod =
            getMethod(clazz, "shape", char[].class, int.class, int.class);

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, null);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Method getMethod(Class<?> clazz,
                                        String name,
                                        Class<?>... paramTypes)
        {
            if (clazz != null) {
                try {
                    return clazz.getMethod(name, paramTypes);
                } catch (NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
            } else {
                return null;
            }
        }

        /**
         * Invokes NumericShaping shape(text,start,count) method.
         */
        static void shape(Object shaper, char[] text, int start, int count) {
            if (shapeMethod == null)
                throw new AssertionError("Should not get here");
            try {
                shapeMethod.invoke(shaper, text, start, count);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException)
                    throw (RuntimeException)cause;
                throw new AssertionError(e);
            } catch (IllegalAccessException iae) {
                throw new AssertionError(iae);
            }
        }
    }
}
