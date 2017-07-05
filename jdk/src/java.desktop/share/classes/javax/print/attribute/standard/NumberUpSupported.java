/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.print.attribute.standard;

import javax.print.attribute.Attribute;
import javax.print.attribute.SetOfIntegerSyntax;
import javax.print.attribute.SupportedValuesAttribute;

/**
 * Class NumberUpSupported is a printing attribute class, a set of integers,
 * that gives the supported values for a {@link NumberUp NumberUp} attribute.
 * <P>
 * <B>IPP Compatibility:</B> The NumberUpSupported attribute's canonical array
 * form gives the lower and upper bound for each range of number-up to be
 * included in an IPP "number-up-supported" attribute. See class {@link
 * javax.print.attribute.SetOfIntegerSyntax SetOfIntegerSyntax} for an
 * explanation of canonical array form. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class NumberUpSupported    extends SetOfIntegerSyntax
        implements SupportedValuesAttribute {

     private static final long serialVersionUID = -1041573395759141805L;


    /**
     * Construct a new number up supported attribute with the given members.
     * The supported values for NumberUp are specified in "array form;" see
     * class
     * {@link javax.print.attribute.SetOfIntegerSyntax SetOfIntegerSyntax}
     * for an explanation of array form.
     *
     * @param  members  Set members in array form.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code members} is null or
     *     any element of {@code members} is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if any element of
     *   {@code members} is not a length-one or length-two array. Also
     *    thrown if {@code members} is a zero-length array or if any
     *    member of the set is less than 1.
     */
    public NumberUpSupported(int[][] members) {
        super (members);
        if (members == null) {
            throw new NullPointerException("members is null");
        }
        int[][] myMembers = getMembers();
        int n = myMembers.length;
        if (n == 0) {
            throw new IllegalArgumentException("members is zero-length");
        }
        int i;
        for (i = 0; i < n; ++ i) {
            if (myMembers[i][0] < 1) {
                throw new IllegalArgumentException
                    ("Number up value must be > 0");
            }
        }
    }

    /**
     * Construct a new number up supported attribute containing a single
     * integer. That is, only the one value of NumberUp is supported.
     *
     * @param  member  Set member.
     *
     * @exception  IllegalArgumentException
     *     (Unchecked exception) Thrown if {@code member} is less than
     *     1.
     */
    public NumberUpSupported(int member) {
        super (member);
        if (member < 1) {
            throw new IllegalArgumentException("Number up value must be > 0");
        }
    }

    /**
     * Construct a new number up supported attribute containing a single range
     * of integers. That is, only those values of NumberUp in the one range are
     * supported.
     *
     * @param  lowerBound  Lower bound of the range.
     * @param  upperBound  Upper bound of the range.
     *
     * @exception  IllegalArgumentException
     *     (Unchecked exception) Thrown if a null range is specified or if a
     *     non-null range is specified with {@code lowerBound} less than
     *     1.
     */
    public NumberUpSupported(int lowerBound, int upperBound) {
        super (lowerBound, upperBound);
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Null range specified");
        } else if (lowerBound < 1) {
            throw new IllegalArgumentException
                ("Number up value must be > 0");
        }
    }

    /**
     * Returns whether this number up supported attribute is equivalent to the
     * passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class NumberUpSupported.
     * <LI>
     * This number up supported attribute's members and {@code object}'s
     * members are the same.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this number up
     *          supported attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals (object) &&
                object instanceof NumberUpSupported);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class NumberUpSupported, the
     * category is class NumberUpSupported itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return NumberUpSupported.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class NumberUpSupported, the
     * category name is {@code "number-up-supported"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "number-up-supported";
    }

}
