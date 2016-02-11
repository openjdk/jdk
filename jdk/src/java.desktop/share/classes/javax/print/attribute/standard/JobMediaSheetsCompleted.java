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
import javax.print.attribute.IntegerSyntax;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class JobMediaSheetsCompleted is an integer valued printing attribute class
 * that specifies the number of media sheets which have completed marking and
 * stacking for the entire job so far, whether those sheets have been processed
 * on one side or on both.
 * <P>
 * The JobMediaSheetsCompleted attribute describes the progress of the job. This
 * attribute is intended to be a counter. That is, the JobMediaSheetsCompleted
 * value for a job that has not started processing must be 0. When the job's
 * {@link JobState JobState} is PROCESSING or PROCESSING_STOPPED, the
 * JobMediaSheetsCompleted value is intended to increase as the job is
 * processed; it indicates the amount of the job that has been processed at the
 * time the Print Job's attribute set is queried or at the time a print job
 * event is reported. When the job enters the COMPLETED, CANCELED, or ABORTED
 * states, the JobMediaSheetsCompleted value is the final value for the job.
 * <P>
 * <B>IPP Compatibility:</B> The integer value gives the IPP integer value. The
 * category name returned by {@code getName()} gives the IPP attribute
 * name.
 *
 * @see JobMediaSheets
 * @see JobMediaSheetsSupported
 * @see JobKOctetsProcessed
 * @see JobImpressionsCompleted
 *
 * @author  Alan Kaminsky
 */
public final class JobMediaSheetsCompleted extends IntegerSyntax
        implements PrintJobAttribute {


    private static final long serialVersionUID = 1739595973810840475L;

    /**
     * Construct a new job media sheets completed attribute with the given
     * integer value.
     *
     * @param  value  Integer value.
     *
     * @exception  IllegalArgumentException
     *   (Unchecked exception) Thrown if {@code value} is less than 0.
     */
    public JobMediaSheetsCompleted(int value) {
        super (value, 0, Integer.MAX_VALUE);
    }

    /**
     * Returns whether this job media sheets completed attribute is equivalent
     * to the passed in object. To be equivalent, all of the following
     * conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class JobMediaSheetsCompleted.
     * <LI>
     * This job media sheets completed attribute's value and
     * {@code object}'s value are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this job media
     *          sheets completed attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals (object) &&
                object instanceof JobMediaSheetsCompleted);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class JobMediaSheetsCompleted, the category is class
     * JobMediaSheetsCompleted itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return JobMediaSheetsCompleted.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class JobMediaSheetsCompleted, the category name is
     * {@code "job-media-sheets-completed"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "job-media-sheets-completed";
    }
}
