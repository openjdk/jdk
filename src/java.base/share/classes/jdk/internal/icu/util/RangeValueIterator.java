// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
* Copyright (C) 1996-2016, International Business Machines Corporation and   *
* others. All Rights Reserved.                                               *
******************************************************************************
*/

package jdk.internal.icu.util;

/**
 * <p>Interface for enabling iteration over sets of &lt;int index, int value&gt;,
 * where index is the sorted integer index in ascending order and value, its
 * associated integer value.
 * <p>The result for each iteration is the consecutive range of
 * &lt;int index, int value&gt; with the same value. Result is represented by
 * &lt;start, limit, value&gt; where
 * <ul>
 * <li> start is the starting integer of the result range
 * <li> limit is 1 after the maximum integer that follows start, such that
 *      all integers between start and (limit - 1), inclusive, have the same
 *      associated integer value.
 * <li> value is the integer value that all integers from start to (limit - 1)
 *      share in common.
 * </ul>
 * <p>
 * Hence value(start) = value(start + 1) = .... = value(start + n) = .... =
 * value(limit - 1). However value(start -1) != value(start) and
 * value(limit) != value(start).
 * 
 * <p>Most implementations will be created by factory methods, such as the
 * character type iterator in UCharacter.getTypeIterator. See example below.
 * 
 * <p>Example of use:<br>
 * <pre>
 * RangeValueIterator iterator = UCharacter.getTypeIterator();
 * RangeValueIterator.Element result = new RangeValueIterator.Element();
 * while (iterator.next(result)) {
 *     System.out.println("Codepoint \\u" +
 *                        Integer.toHexString(result.start) +
 *                        " to codepoint \\u" +
 *                        Integer.toHexString(result.limit - 1) +
 *                        " has the character type " + result.value);
 * }
 * </pre>
 * @author synwee
 * @stable ICU 2.6
 */
public interface RangeValueIterator
{
    // public inner class ---------------------------------------------

    /**
    * Return result wrapper for jdk.internal.icu.util.RangeValueIterator.
    * Stores the start and limit of the continuous result range and the
    * common value all integers between [start, limit - 1] has.
    * @stable ICU 2.6
    */
    public class Element
    {
        // public data member ---------------------------------------------

        /**
        * Starting integer of the continuous result range that has the same
        * value
        * @stable ICU 2.6
        */
        public int start;
        /**
        * (End + 1) integer of continuous result range that has the same
        * value
        * @stable ICU 2.6
        */
        public int limit;
        /**
        * Gets the common value of the continuous result range
        * @stable ICU 2.6
        */
        public int value;

        // public constructor --------------------------------------------

        /**
         * Empty default constructor to make javadoc happy
         * @stable ICU 2.4
         */
        public Element()
        {
        }
    }

    // public methods -------------------------------------------------

    /**
    * <p>Returns the next maximal result range with a common value and returns
    * true if we are not at the end of the iteration, false otherwise.
    * <p>If this returns a false, the contents of elements will not
    * be updated.
    * @param element for storing the result range and value
    * @return true if we are not at the end of the iteration, false otherwise.
    * @see Element
    * @stable ICU 2.6
    */
    public boolean next(Element element);

    /**
    * Resets the iterator to the beginning of the iteration.
    * @stable ICU 2.6
    */
    public void reset();
}
