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

import java.util.Date;
import javax.print.attribute.Attribute;
import javax.print.attribute.DateTimeSyntax;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class DateTimeAtCompleted is a printing attribute class, a date-time
 * attribute, that indicates the date and time at which the Print Job completed
 * (or was canceled or aborted).
 * <P>
 * To construct a DateTimeAtCompleted attribute from separate values of the
 * year, month, day, hour, minute, and so on, use a {@link java.util.Calendar
 * Calendar} object to construct a {@link java.util.Date Date} object, then use
 * the {@link java.util.Date Date} object to construct the DateTimeAtCompleted
 * attribute. To convert a DateTimeAtCompleted attribute to separate values of
 * the year, month, day, hour, minute, and so on, create a {@link
 * java.util.Calendar Calendar} object and set it to the {@link java.util.Date
 * Date} from the DateTimeAtCompleted attribute.
 * <P>
 * <B>IPP Compatibility:</B> The information needed to construct an IPP
 * "date-time-at-completed" attribute can be obtained as described above. The
 * category name returned by <CODE>getName()</CODE> gives the IPP attribute
 * name.
 *
 * @author  Alan Kaminsky
 */
public final class DateTimeAtCompleted extends DateTimeSyntax
    implements PrintJobAttribute {

    private static final long serialVersionUID = 6497399708058490000L;

    /**
     * Construct a new date-time at completed attribute with the given {@link
     * java.util.Date Date} value.
     *
     * @param  dateTime  {@link java.util.Date Date} value.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>dateTime</CODE> is null.
     */
    public DateTimeAtCompleted(Date dateTime) {
        super (dateTime);
    }

    /**
     * Returns whether this date-time at completed attribute is equivalent to
     * the passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class DateTimeAtCompleted.
     * <LI>
     * This date-time at completed attribute's {@link java.util.Date Date} value
     * and <CODE>object</CODE>'s {@link java.util.Date Date} value are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this date-time
     *          at completed attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return(super.equals (object) &&
               object instanceof DateTimeAtCompleted);
    }

// Exported operations inherited and implemented from interface Attribute.

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
         * <P>
         * For class DateTimeAtCompleted, the category is class
         * DateTimeAtCompleted itself.
         *
         * @return  Printing attribute class (category), an instance of class
         *          {@link java.lang.Class java.lang.Class}.
         */
    public final Class<? extends Attribute> getCategory() {
        return DateTimeAtCompleted.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class DateTimeAtCompleted, the category name is
     * <CODE>"date-time-at-completed"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "date-time-at-completed";
    }

}
