/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.util.Locale;

import javax.print.attribute.Attribute;
import javax.print.attribute.URISyntax;
import javax.print.attribute.PrintServiceAttribute;

/**
 * Class PrinterURI is a printing attribute class, a URI, that specifies the
 * globally unique name of a printer.  If it has such a name, an administrator
 * determines a printer's URI and sets this attribute to that name.
 * <P>
 * <B>IPP Compatibility:</B>  This implements the
 * IPP printer-uri attribute. The string form returned by
 * <CODE>toString()</CODE>  gives the IPP printer-uri value.
 * The category name returned by <CODE>getName()</CODE>
 * gives the IPP attribute name.
 *
 * @author  Robert Herriot
 */

public final class PrinterURI extends URISyntax
        implements PrintServiceAttribute {

    private static final long serialVersionUID = 7923912792485606497L;

    /**
     * Constructs a new PrinterURI attribute with the specified URI.
     *
     * @param  uri  URI of the printer
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>uri</CODE> is null.
     */
    public PrinterURI(URI uri) {
        super (uri);
    }

    /**
     * Returns whether this printer name attribute is equivalent to the passed
     * in object. To be equivalent, all of the following conditions must be
     * true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class PrinterURI.
     * <LI>
     * This PrinterURI attribute's underlying URI and
     * <CODE>object</CODE>'s underlying URI are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this PrinterURI
     *          attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) && object instanceof PrinterURI);
    }

   /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterURI and any vendor-defined subclasses, the category is
     * class PrinterURI itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterURI.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterURI and any vendor-defined subclasses, the category
     * name is <CODE>"printer-uri"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-uri";
    }

}
