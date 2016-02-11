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

import java.util.Locale;

import javax.print.attribute.Attribute;
import javax.print.attribute.TextSyntax;
import javax.print.attribute.PrintServiceAttribute;

/**
 * Class PrinterName is a printing attribute class, a text attribute, that
 * specifies the name of a printer. It is a name that is more end-user friendly
 * than a URI. An administrator determines a printer's name and sets this
 * attribute to that name. This name may be the last part of the printer's URI
 * or it may be unrelated. In non-US-English locales, a name may contain
 * characters that are not allowed in a URI.
 * <P>
 * <B>IPP Compatibility:</B> The string value gives the IPP name value. The
 * locale gives the IPP natural language. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class PrinterName extends TextSyntax
        implements PrintServiceAttribute {

    private static final long serialVersionUID = 299740639137803127L;

    /**
     * Constructs a new printer name attribute with the given name and locale.
     *
     * @param  printerName  Printer name.
     * @param  locale       Natural language of the text string. null
     * is interpreted to mean the default locale as returned
     * by {@code Locale.getDefault()}
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code printerName} is null.
     */
    public PrinterName(String printerName, Locale locale) {
        super (printerName, locale);
    }

    /**
     * Returns whether this printer name attribute is equivalent to the passed
     * in object. To be equivalent, all of the following conditions must be
     * true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class PrinterName.
     * <LI>
     * This printer name attribute's underlying string and
     * {@code object}'s underlying string are equal.
     * <LI>
     * This printer name attribute's locale and {@code object}'s locale
     * are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this printer
     *          name attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) && object instanceof PrinterName);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterName, the category is
     * class PrinterName itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterName.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterName, the category
     * name is {@code "printer-name"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-name";
    }

}
