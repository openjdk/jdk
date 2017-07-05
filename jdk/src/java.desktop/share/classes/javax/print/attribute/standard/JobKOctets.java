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
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class JobKOctets is an integer valued printing attribute class that specifies
 * the total size of the document(s) in K octets, i.e., in units of 1024 octets
 * requested to be processed in the job. The value must be rounded up, so that a
 * job between 1 and 1024 octets must be indicated as being 1K octets, 1025 to
 * 2048 must be 2K octets, etc. For a multidoc print job (a job with multiple
 * documents), the JobKOctets value is computed by adding up the individual
 * documents' sizes in octets, then rounding up to the next K octets value.
 * <P>
 * The JobKOctets attribute describes the size of the job. This attribute is not
 * intended to be a counter; it is intended to be useful routing and scheduling
 * information if known. The printer may try to compute the JobKOctets
 * attribute's value if it is not supplied in the Print Request. Even if the
 * client does supply a value for the JobKOctets attribute in the Print Request,
 * the printer may choose to change the value if the printer is able to compute
 * a value which is more accurate than the client supplied value. The printer
 * may be able to determine the correct value for the JobKOctets attribute
 * either right at job submission time or at any later point in time.
 * <P>
 * The JobKOctets value must not include the multiplicative factors contributed
 * by the number of copies specified by the {@link Copies Copies} attribute,
 * independent of whether the device can process multiple copies without making
 * multiple passes over the job or document data and independent of whether the
 * output is collated or not. Thus the value is independent of the
 * implementation and indicates the size of the document(s) measured in K octets
 * independent of the number of copies.
 * <P>
 * The JobKOctets value must also not include the multiplicative factor due to a
 * copies instruction embedded in the document data. If the document data
 * actually includes replications of the document data, this value will include
 * such replication. In other words, this value is always the size of the source
 * document data, rather than a measure of the hardcopy output to be produced.
 * <P>
 * The size of a doc is computed based on the print data representation class as
 * specified by the doc's {@link javax.print.DocFlavor DocFlavor}, as
 * shown in the table below.
 *
 * <TABLE BORDER=1 CELLPADDING=2 CELLSPACING=1 SUMMARY="Table showing computation of doc sizes">
 * <TR>
 * <TH>Representation Class</TH>
 * <TH>Document Size</TH>
 * </TR>
 * <TR>
 * <TD>byte[]</TD>
 * <TD>Length of the byte array</TD>
 * </TR>
 * <TR>
 * <TD>java.io.InputStream</TD>
 * <TD>Number of bytes read from the stream</TD>
 * </TR>
 * <TR>
 * <TD>char[]</TD>
 * <TD>Length of the character array x 2</TD>
 * </TR>
 * <TR>
 * <TD>java.lang.String</TD>
 * <TD>Length of the string x 2</TD>
 * </TR>
 * <TR>
 * <TD>java.io.Reader</TD>
 * <TD>Number of characters read from the stream x 2</TD>
 * </TR>
 * <TR>
 * <TD>java.net.URL</TD>
 * <TD>Number of bytes read from the file at the given URL address</TD>
 * </TR>
 * <TR>
 * <TD>java.awt.image.renderable.RenderableImage</TD>
 * <TD>Implementation dependent&#42;</TD>
 * </TR>
 * <TR>
 * <TD>java.awt.print.Printable</TD>
 * <TD>Implementation dependent&#42;</TD>
 * </TR>
 * <TR>
 * <TD>java.awt.print.Pageable</TD>
 * <TD>Implementation dependent&#42;</TD>
 * </TR>
 * </TABLE>
 * <P>
 * &#42; In these cases the Print Service itself generates the print data sent
 * to the printer. If the Print Service supports the JobKOctets attribute, for
 * these cases the Print Service itself must calculate the size of the print
 * data, replacing any JobKOctets value the client specified.
 * <P>
 * <B>IPP Compatibility:</B> The integer value gives the IPP integer value. The
 * category name returned by {@code getName()} gives the IPP attribute
 * name.
 *
 * @see JobKOctetsSupported
 * @see JobKOctetsProcessed
 * @see JobImpressions
 * @see JobMediaSheets
 *
 * @author  Alan Kaminsky
 */
public final class JobKOctets   extends IntegerSyntax
        implements PrintRequestAttribute, PrintJobAttribute {

    private static final long serialVersionUID = -8959710146498202869L;

    /**
     * Construct a new job K octets attribute with the given integer value.
     *
     * @param  value  Integer value.
     *
     * @exception  IllegalArgumentException
     *  (Unchecked exception) Thrown if {@code value} is less than 0.
     */
    public JobKOctets(int value) {
        super (value, 0, Integer.MAX_VALUE);
    }

    /**
     * Returns whether this job K octets attribute is equivalent to the passed
     * in object. To be equivalent, all of the following conditions must be
     * true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class JobKOctets.
     * <LI>
     * This job K octets attribute's value and {@code object}'s value
     * are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this job K
     *          octets attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return super.equals(object) && object instanceof JobKOctets;
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class JobKOctets, the category is class JobKOctets itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return JobKOctets.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class JobKOctets, the category name is {@code "job-k-octets"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "job-k-octets";
    }

}
