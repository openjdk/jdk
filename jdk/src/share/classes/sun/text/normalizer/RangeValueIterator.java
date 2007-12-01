/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * <p>Interface for enabling iteration over sets of <int index, int value>,
 * where index is the sorted integer index in ascending order and value, its
 * associated integer value.</p>
 * <p>The result for each iteration is the consecutive range of
 * <int index, int value> with the same value. Result is represented by
 * <start, limit, value> where</p>
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
 * </p>
 * <p>Most implementations will be created by factory methods, such as the
 * character type iterator in UCharacter.getTypeIterator. See example below.
 * </p>
 * Example of use:<br>
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
    * Return result wrapper for com.ibm.icu.util.RangeValueIterator.
    * Stores the start and limit of the continous result range and the
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
        * Gets the common value of the continous result range
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
    * <p>Gets the next maximal result range with a common value and returns
    * true if we are not at the end of the iteration, false otherwise.</p>
    * <p>If the return boolean is a false, the contents of elements will not
    * be updated.</p>
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
