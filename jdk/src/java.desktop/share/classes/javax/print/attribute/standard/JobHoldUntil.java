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
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class JobHoldUntil is a printing attribute class, a date-time attribute, that
 * specifies the exact date and time at which the job must become a candidate
 * for printing.
 * <P>
 * If the value of this attribute specifies a date-time that is in the future,
 * the printer should add the {@link JobStateReason JobStateReason} value of
 * JOB_HOLD_UNTIL_SPECIFIED to the job's {@link JobStateReasons JobStateReasons}
 * attribute, must move the job to the PENDING_HELD state, and must not schedule
 * the job for printing until the specified date-time arrives.
 * <P>
 * When the specified date-time arrives, the printer must remove the {@link
 * JobStateReason JobStateReason} value of JOB_HOLD_UNTIL_SPECIFIED from the
 * job's {@link JobStateReasons JobStateReasons} attribute, if present. If there
 * are no other job state reasons that keep the job in the PENDING_HELD state,
 * the printer must consider the job as a candidate for processing by moving the
 * job to the PENDING state.
 * <P>
 * If the specified date-time has already passed, the job must be a candidate
 * for processing immediately. Thus, one way to make the job immediately become
 * a candidate for processing is to specify a JobHoldUntil attribute constructed
 * like this (denoting a date-time of January 1, 1970, 00:00:00 GMT):
 * <PRE>
 *     JobHoldUntil immediately = new JobHoldUntil (new Date (0L));
 * </PRE>
 * <P>
 * If the client does not supply this attribute in a Print Request and the
 * printer supports this attribute, the printer must use its
 * (implementation-dependent) default JobHoldUntil value at job submission time
 * (unlike most job template attributes that are used if necessary at job
 * processing time).
 * <P>
 * To construct a JobHoldUntil attribute from separate values of the year,
 * month, day, hour, minute, and so on, use a {@link java.util.Calendar
 * Calendar} object to construct a {@link java.util.Date Date} object, then use
 * the {@link java.util.Date Date} object to construct the JobHoldUntil
 * attribute. To convert a JobHoldUntil attribute to separate values of the
 * year, month, day, hour, minute, and so on, create a {@link java.util.Calendar
 * Calendar} object and set it to the {@link java.util.Date Date} from the
 * JobHoldUntil attribute.
 * <P>
 * <B>IPP Compatibility:</B> Although IPP supports a "job-hold-until" attribute
 * specified as a keyword, IPP does not at this time support a "job-hold-until"
 * attribute specified as a date and time. However, the date and time can be
 * converted to one of the standard IPP keywords with some loss of precision;
 * for example, a JobHoldUntil value with today's date and 9:00pm local time
 * might be converted to the standard IPP keyword "night". The category name
 * returned by {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class JobHoldUntil extends DateTimeSyntax
        implements PrintRequestAttribute, PrintJobAttribute {

    private static final long serialVersionUID = -1664471048860415024L;


    /**
     * Construct a new job hold until date-time attribute with the given
     * {@link java.util.Date Date} value.
     *
     * @param  dateTime  {@link java.util.Date Date} value.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code dateTime} is null.
     */
    public JobHoldUntil(Date dateTime) {
        super (dateTime);
    }

    /**
     * Returns whether this job hold until attribute is equivalent to the
     * passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class JobHoldUntil.
     * <LI>
     * This job hold until attribute's {@link java.util.Date Date} value and
     * {@code object}'s {@link java.util.Date Date} value are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this job hold
     *          until attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) && object instanceof JobHoldUntil);
    }


    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class JobHoldUntil, the category is class JobHoldUntil itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return JobHoldUntil.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class JobHoldUntil, the category name is {@code "job-hold-until"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "job-hold-until";
    }

}
