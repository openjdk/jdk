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
 * Class JobMediaSheetsSupported is a printing attribute class, a set of
 * integers, that gives the supported values for a {@link JobMediaSheets
 * JobMediaSheets} attribute. It is restricted to a single contiguous range of
 * integers; multiple non-overlapping ranges are not allowed. This gives the
 * lower and upper bounds of the total sizes of print jobs in number of media
 * sheets that the printer will accept.
 * <P>
 * <B>IPP Compatibility:</B> The JobMediaSheetsSupported attribute's canonical
 * array form gives the lower and upper bound for the range of values to be
 * included in an IPP "job-media-sheets-supported" attribute. See class {@link
 * javax.print.attribute.SetOfIntegerSyntax SetOfIntegerSyntax} for an
 * explanation of canonical array form. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class JobMediaSheetsSupported extends SetOfIntegerSyntax
        implements SupportedValuesAttribute {

    private static final long serialVersionUID = 2953685470388672940L;

    /**
     * Construct a new job media sheets supported attribute containing a single
     * range of integers. That is, only those values of JobMediaSheets in the
     * one range are supported.
     *
     * @param  lowerBound  Lower bound of the range.
     * @param  upperBound  Upper bound of the range.
     *
     * @exception  IllegalArgumentException
     *  (Unchecked exception) Thrown if a null range is specified or if a
     *   non-null range is specified with {@code lowerBound} less than
     *    0.
     */
    public JobMediaSheetsSupported(int lowerBound, int upperBound) {
        super (lowerBound, upperBound);
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Null range specified");
        } else if (lowerBound < 0) {
            throw new IllegalArgumentException
                                ("Job K octets value < 0 specified");
        }
    }

    /**
     * Returns whether this job media sheets supported attribute is equivalent
     * to the passed in object. To be equivalent, all of the following
     * conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class JobMediaSheetsSupported.
     * <LI>
     * This job media sheets supported attribute's members and
     * {@code object}'s members are the same.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this job media
     *          sheets supported attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals (object) &&
                object instanceof JobMediaSheetsSupported);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class JobMediaSheetsSupported, the
     * category is class JobMediaSheetsSupported itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return JobMediaSheetsSupported.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class JobMediaSheetsSupported, the
     * category name is {@code "job-media-sheets-supported"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "job-media-sheets-supported";
    }

}
