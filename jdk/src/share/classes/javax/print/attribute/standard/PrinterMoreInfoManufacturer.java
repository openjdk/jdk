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
package javax.print.attribute.standard;

import java.net.URI;

import javax.print.attribute.Attribute;
import javax.print.attribute.URISyntax;
import javax.print.attribute.PrintServiceAttribute;

/**
 * Class PrinterMoreInfoManufacturer is a printing attribute class, a URI,
 * that is used to obtain more information about this type of device.
 * The information obtained from this URI is intended for end user
 * consumption. Features outside the scope of the Print Service API
 * can be accessed from this URI (e.g.,
 * latest firmware, upgrades, service proxies, optional features available,
 * details on color support). The information is intended to be germane to
 * this kind of printer without regard to site specific modifications or
 * services.
 * <P>
 * In contrast, the {@link PrinterMoreInfo PrinterMoreInfo} attribute is used
 * to find out more information about this specific printer rather than this
 * general kind of printer.
 * <P>
 * <P>
 * <B>IPP Compatibility:</B> The string form returned by
 * <CODE>toString()</CODE> gives the IPP uri value.
 * The category name returned by <CODE>getName()</CODE>
 * gives the IPP attribute name.
 * <P>
 *
 * @author  Alan Kaminsky
 */
public final class PrinterMoreInfoManufacturer extends URISyntax
        implements PrintServiceAttribute {

    private static final long serialVersionUID = 3323271346485076608L;

    /**
     * Constructs a new printer more info manufacturer attribute with the
     * specified URI.
     *
     * @param  uri  URI.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>uri</CODE> is null.
     */
    public PrinterMoreInfoManufacturer(URI uri) {
        super (uri);
    }

    /**
     * Returns whether this printer more info manufacturer attribute is
     * equivalent to the passed in object. To be equivalent, all of the
     * following conditions must be true:
     * <OL TYPE=1>
     * <LI>
     * <CODE>object</CODE> is not null.
     * <LI>
     * <CODE>object</CODE> is an instance of class
     * PrinterMoreInfoManufacturer.
     * <LI>
     * This printer more info manufacturer attribute's URI and
     * <CODE>object</CODE>'s URI are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if <CODE>object</CODE> is equivalent to this printer
     *          more info manufacturer attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) &&
                object instanceof PrinterMoreInfoManufacturer);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterMoreInfoManufacturer, the category is
     * class PrinterMoreInfoManufacturer itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterMoreInfoManufacturer.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterMoreInfoManufacturer, the category name is
     * <CODE>"printer-more-info-manufacturer"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-more-info-manufacturer";
    }

}
