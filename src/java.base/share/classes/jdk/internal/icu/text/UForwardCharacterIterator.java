// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;

/**
 * Interface that defines an API for forward-only iteration
 * on text objects.
 * This is a minimal interface for iteration without random access
 * or backwards iteration. It is especially useful for wrapping
 * streams with converters into an object for collation or
 * normalization.
 *
 * <p>Characters can be accessed in two ways: as code units or as
 * code points.
 * Unicode code points are 21-bit integers and are the scalar values
 * of Unicode characters. ICU uses the type <code>int</code> for them.
 * Unicode code units are the storage units of a given
 * Unicode/UCS Transformation Format (a character encoding scheme).
 * With UTF-16, all code points can be represented with either one
 * or two code units ("surrogates").
 * String storage is typically based on code units, while properties
 * of characters are typically determined using code point values.
 * Some processes may be designed to work with sequences of code units,
 * or it may be known that all characters that are important to an
 * algorithm can be represented with single code units.
 * Other processes will need to use the code point access functions.
 *
 * <p>ForwardCharacterIterator provides next() to access
 * a code unit and advance an internal position into the text object,
 * similar to a <code>return text[position++]</code>.<br>
 * It provides nextCodePoint() to access a code point and advance an internal
 * position.
 *
 * <p>nextCodePoint() assumes that the current position is that of
 * the beginning of a code point, i.e., of its first code unit.
 * After nextCodePoint(), this will be true again.
 * In general, access to code units and code points in the same
 * iteration loop should not be mixed. In UTF-16, if the current position
 * is on a second code unit (Low Surrogate), then only that code unit
 * is returned even by nextCodePoint().
 *
 * Usage:
 * <code> 
 *  public void function1(UForwardCharacterIterator it) {
 *     int c;
 *     while((c=it.next())!=UForwardCharacterIterator.DONE) {
 *         // use c
 *      }
 *  }
 * </code>
 * @stable ICU 2.4
 *
 */

public interface UForwardCharacterIterator {
      
    /**
     * Indicator that we have reached the ends of the UTF16 text.
     * @stable ICU 2.4
     */
    public static final int DONE = -1;
    /**
     * Returns the UTF16 code unit at index, and increments to the next
     * code unit (post-increment semantics).  If index is out of
     * range, DONE is returned, and the iterator is reset to the limit
     * of the text.
     * @return the next UTF16 code unit, or DONE if the index is at the limit
     *         of the text.
     * @stable ICU 2.4  
     */
    public int next();

    /**
     * Returns the code point at index, and increments to the next code
     * point (post-increment semantics).  If index does not point to a
     * valid surrogate pair, the behavior is the same as
     * <code>next()</code>.  Otherwise the iterator is incremented past
     * the surrogate pair, and the code point represented by the pair
     * is returned.
     * @return the next codepoint in text, or DONE if the index is at
     *         the limit of the text.
     * @stable ICU 2.4  
     */
    public int nextCodePoint();

}
