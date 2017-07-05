/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package javax.print.attribute;

import java.io.Serializable;

/**
 * Class IntegerSyntax is an abstract base class providing the common
 * implementation of all attributes with integer values.
 * <P>
 * Under the hood, an integer attribute is just an integer. You can get an
 * integer attribute's integer value by calling {@link #getValue()
 * getValue()}. An integer attribute's integer value is
 * established when it is constructed (see {@link #IntegerSyntax(int)
 * IntegerSyntax(int)}). Once constructed, an integer attribute's
 * value is immutable.
 * <P>
 *
 * @author  David Mendenhall
 * @author  Alan Kaminsky
 */
public abstract class IntegerSyntax implements Serializable, Cloneable {

    private static final long serialVersionUID = 3644574816328081943L;

    /**
     * This integer attribute's integer value.
     * @serial
     */
    private int value;

    /**
     * Construct a new integer attribute with the given integer value.
     *
     * @param  value  Integer value.
     */
    protected IntegerSyntax(int value) {
        this.value = value;
    }

    /**
     * Construct a new integer attribute with the given integer value, which
     * must lie within the given range.
     *
     * @param  value       Integer value.
     * @param  lowerBound  Lower bound.
     * @param  upperBound  Upper bound.
     *
     * @exception  IllegalArgumentException
     *     (Unchecked exception) Thrown if <CODE>value</CODE> is less than
     *     <CODE>lowerBound</CODE> or greater than
     *     <CODE>upperBound</CODE>.
     */
    protected IntegerSyntax(int value, int lowerBound, int upperBound) {
        if (lowerBound > value || value > upperBound) {
            throw new IllegalArgumentException("Value " + value +
                                               " not in range " + lowerBound +
                                               ".." + upperBound);
        }
        this.value = value;
    }

    /**
     * Returns this integer attribute's integer value.
     * @return the integer value
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns whether this integer attribute is equivalent to the passed in
     * object. To be equivalent, all of the following conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class IntegerSyntax.
     * <LI>
     * This integer attribute's value and <CODE>object</CODE>'s value are
     * equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this integer
     *          attribute, false otherwise.
     */
    public boolean equals(Object object) {

        return (object != null && object instanceof IntegerSyntax &&
                value == ((IntegerSyntax) object).value);
    }

    /**
     * Returns a hash code value for this integer attribute. The hash code is
     * just this integer attribute's integer value.
     */
    public int hashCode() {
        return value;
    }

    /**
     * Returns a string value corresponding to this integer attribute. The
     * string value is just this integer attribute's integer value converted to
     * a string.
     */
    public String toString() {
        return "" + value;
    }
}
