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
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class PrinterResolution is a printing attribute class that specifies an
 * exact resolution supported by a printer or to be used for a print job.
 * This attribute assumes that printers have a small set of device resolutions
 * at which they can operate rather than a continuum.
 * <p>
 * PrinterResolution is used in multiple ways:
 * <OL TYPE=1>
 * <LI>
 * When a client searches looking for a printer that supports the client's
 * desired resolution exactly (no more, no less), the client specifies
 * an instance of class PrinterResolution indicating the exact resolution the
 * client wants. Only printers supporting that exact resolution will match the
 * search.
 *
 * <LI>
 * When a client needs to print a job using the client's desired resolution
 * exactly (no more, no less), the client specifies an instance of class
 * PrinterResolution as an attribute of the Print Job. This will fail if the
 * Print Job doesn't support that exact resolution, and Fidelity is set to
 * true.
 * </OL>
 * If a client wants to locate a printer supporting a resolution
 * greater than some required minimum, then it may be necessary to exclude
 * this attribute from a lookup request and to directly query the set of
 * supported resolutions, and specify the one that most closely meets
 * the client's requirements.
 * In some cases this may be more simply achieved by specifying a
 * PrintQuality attribute which often controls resolution.
 * <P>
 * <B>IPP Compatibility:</B> The information needed to construct an IPP
 * <CODE>"printer-resolution"</CODE> attribute can be obtained by calling
 * methods on the PrinterResolution object. The category name returned by
 * <CODE>getName()</CODE> gives the IPP attribute name.
 *
 * @author  David Mendenhall
 * @author  Alan Kaminsky
 */
public final class PrinterResolution    extends ResolutionSyntax
        implements DocAttribute, PrintRequestAttribute, PrintJobAttribute {

    private static final long serialVersionUID = 13090306561090558L;

    /**
     * Construct a new printer resolution attribute from the given items.
     *
     * @param  crossFeedResolution
     *     Cross feed direction resolution.
     * @param  feedResolution
     *     Feed direction resolution.
     * @param  units
     *    Unit conversion factor, e.g. <code>ResolutionSyntax.DPI</CODE>
     * or <code>ResolutionSyntax.DPCM</CODE>.
     *
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if {@code crossFeedResolution < 1} or
     *     {@code feedResolution < 1} or {@code units < 1}.
     */
    public PrinterResolution(int crossFeedResolution, int feedResolution,
                             int units) {
        super (crossFeedResolution, feedResolution, units);
    }

    /**
     * Returns whether this printer resolution attribute is equivalent to the
     * passed in object. To be equivalent, all of the following conditions
     * must be true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class PrinterResolution.
     * <LI>
     * This attribute's cross feed direction resolution is equal to
     * <CODE>object</CODE>'s cross feed direction resolution.
     * <LI>
     * This attribute's feed direction resolution is equal to
     * <CODE>object</CODE>'s feed direction resolution.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this printer
     *          resolution attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals (object) &&
                object instanceof PrinterResolution);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterResolution, the category is class PrinterResolution itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterResolution.class;
                }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterResolution, the
     * category name is <CODE>"printer-resolution"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-resolution";
    }

}
