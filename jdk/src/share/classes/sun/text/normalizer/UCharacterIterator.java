/*
 * Portions Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.text.CharacterIterator;

/**
 * Abstract class that defines an API for iteration on text objects.This is an
 * interface for forward and backward iteration and random access into a text
 * object. Forward iteration is done with post-increment and backward iteration
 * is done with pre-decrement semantics, while the
 * <code>java.text.CharacterIterator</code> interface methods provided forward
 * iteration with "pre-increment" and backward iteration with pre-decrement
 * semantics. This API is more efficient for forward iteration over code points.
 * The other major difference is that this API can do both code unit and code point
 * iteration, <code>java.text.CharacterIterator</code> can only iterate over
 * code units and is limited to BMP (0 - 0xFFFF)
 * @author Ram
 * @stable ICU 2.4
 */
public abstract class UCharacterIterator
                      implements Cloneable {

    /**
     * Protected default constructor for the subclasses
     * @stable ICU 2.4
     */
    protected UCharacterIterator(){
    }

    /**
     * Indicator that we have reached the ends of the UTF16 text.
     * Moved from UForwardCharacterIterator.java
     * @stable ICU 2.4
     */
    public static final int DONE = -1;

    // static final methods ----------------------------------------------------

    /**
     * Returns a <code>UCharacterIterator</code> object given a
     * source string.
     * @param source a string
     * @return UCharacterIterator object
     * @exception IllegalArgumentException if the argument is null
     * @stable ICU 2.4
     */
    public static final UCharacterIterator getInstance(String source){
        return new ReplaceableUCharacterIterator(source);
    }

    //// for StringPrep
    /**
     * Returns a <code>UCharacterIterator</code> object given a
     * source StringBuffer.
     * @param source an string buffer of UTF-16 code units
     * @return UCharacterIterator object
     * @exception IllegalArgumentException if the argument is null
     * @stable ICU 2.4
     */
    public static final UCharacterIterator getInstance(StringBuffer source){
        return new ReplaceableUCharacterIterator(source);
    }

    /**
     * Returns a <code>UCharacterIterator</code> object given a
     * CharacterIterator.
     * @param source a valid CharacterIterator object.
     * @return UCharacterIterator object
     * @exception IllegalArgumentException if the argument is null
     * @stable ICU 2.4
     */
    public static final UCharacterIterator getInstance(CharacterIterator source){
        return new CharacterIteratorWrapper(source);
    }

    // public methods ----------------------------------------------------------

    /**
     * Returns the code unit at the current index.  If index is out
     * of range, returns DONE.  Index is not changed.
     * @return current code unit
     * @stable ICU 2.4
     */
    public abstract int current();

    /**
     * Returns the length of the text
     * @return length of the text
     * @stable ICU 2.4
     */
    public abstract int getLength();


    /**
     * Gets the current index in text.
     * @return current index in text.
     * @stable ICU 2.4
     */
    public abstract int getIndex();


    /**
     * Returns the UTF16 code unit at index, and increments to the next
     * code unit (post-increment semantics).  If index is out of
     * range, DONE is returned, and the iterator is reset to the limit
     * of the text.
     * @return the next UTF16 code unit, or DONE if the index is at the limit
     *         of the text.
     * @stable ICU 2.4
     */
    public abstract int next();

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
    public int nextCodePoint(){
        int ch1 = next();
        if(UTF16.isLeadSurrogate((char)ch1)){
            int ch2 = next();
            if(UTF16.isTrailSurrogate((char)ch2)){
                return UCharacterProperty.getRawSupplementary((char)ch1,
                                                              (char)ch2);
            }else if (ch2 != DONE) {
                // unmatched surrogate so back out
                previous();
            }
        }
        return ch1;
    }

    /**
     * Decrement to the position of the previous code unit in the
     * text, and return it (pre-decrement semantics).  If the
     * resulting index is less than 0, the index is reset to 0 and
     * DONE is returned.
     * @return the previous code unit in the text, or DONE if the new
     *         index is before the start of the text.
     * @stable ICU 2.4
     */
    public abstract int previous();

    /**
     * Sets the index to the specified index in the text.
     * @param index the index within the text.
     * @exception IndexOutOfBoundsException is thrown if an invalid index is
     *            supplied
     * @stable ICU 2.4
     */
    public abstract void setIndex(int index);

    //// for StringPrep
    /**
     * Fills the buffer with the underlying text storage of the iterator
     * If the buffer capacity is not enough a exception is thrown. The capacity
     * of the fill in buffer should at least be equal to length of text in the
     * iterator obtained by calling <code>getLength()</code>.
     * <b>Usage:</b>
     *
     * <code>
     * <pre>
     *         UChacterIterator iter = new UCharacterIterator.getInstance(text);
     *         char[] buf = new char[iter.getLength()];
     *         iter.getText(buf);
     *
     *         OR
     *         char[] buf= new char[1];
     *         int len = 0;
     *         for(;;){
     *             try{
     *                 len = iter.getText(buf);
     *                 break;
     *             }catch(IndexOutOfBoundsException e){
     *                 buf = new char[iter.getLength()];
     *             }
     *         }
     * </pre>
     * </code>
     *
     * @param fillIn an array of chars to fill with the underlying UTF-16 code
     *         units.
     * @param offset the position within the array to start putting the data.
     * @return the number of code units added to fillIn, as a convenience
     * @exception IndexOutOfBounds exception if there is not enough
     *            room after offset in the array, or if offset < 0.
     * @stable ICU 2.4
     */
    public abstract int getText(char[] fillIn, int offset);

    //// for StringPrep
    /**
     * Convenience override for <code>getText(char[], int)</code> that provides
     * an offset of 0.
     * @param fillIn an array of chars to fill with the underlying UTF-16 code
     *         units.
     * @return the number of code units added to fillIn, as a convenience
     * @exception IndexOutOfBounds exception if there is not enough
     *            room in the array.
     * @stable ICU 2.4
     */
    public final int getText(char[] fillIn) {
        return getText(fillIn, 0);
    }

    //// for StringPrep
    /**
     * Convenience method for returning the underlying text storage as as string
     * @return the underlying text storage in the iterator as a string
     * @stable ICU 2.4
     */
    public String getText() {
        char[] text = new char[getLength()];
        getText(text);
        return new String(text);
    }

    /**
     * Moves the current position by the number of code units
     * specified, either forward or backward depending on the sign
     * of delta (positive or negative respectively).  If the resulting
     * index would be less than zero, the index is set to zero, and if
     * the resulting index would be greater than limit, the index is
     * set to limit.
     *
     * @param delta the number of code units to move the current
     *              index.
     * @return the new index.
     * @exception IndexOutOfBoundsException is thrown if an invalid index is
     *            supplied
     * @stable ICU 2.4
     *
     */
    public int moveIndex(int delta) {
        int x = Math.max(0, Math.min(getIndex() + delta, getLength()));
        setIndex(x);
        return x;
    }

    /**
     * Creates a copy of this iterator, independent from other iterators.
     * If it is not possible to clone the iterator, returns null.
     * @return copy of this iterator
     * @stable ICU 2.4
     */
    public Object clone() throws CloneNotSupportedException{
        return super.clone();
    }

}
