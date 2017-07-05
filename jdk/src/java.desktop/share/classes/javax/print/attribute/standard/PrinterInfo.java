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
 * Class PrinterInfo is a printing attribute class, a text attribute, that
 * provides descriptive information about a printer. This could include things
 * like: {@code "This printer can be used for printing color transparencies for
 * HR presentations"}, or {@code "Out of courtesy for others, please
 * print only small (1-5 page) jobs at this printer"}, or even
 * {@code "This printer is going away on July 1, 1997, please find a new
 * printer"}.
 * <P>
 * <B>IPP Compatibility:</B> The string value gives the IPP name value. The
 * locale gives the IPP natural language. The category name returned by
 * {@code getName()} gives the IPP attribute name.
 *
 * @author  Alan Kaminsky
 */
public final class PrinterInfo extends TextSyntax
        implements PrintServiceAttribute {

    private static final long serialVersionUID = 7765280618777599727L;

    /**
     * Constructs a new printer info attribute with the given information
     * string and locale.
     *
     * @param  info    Printer information string.
     * @param  locale  Natural language of the text string. null
     * is interpreted to mean the default locale as returned
     * by {@code Locale.getDefault()}
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if {@code info} is null.
     */
    public PrinterInfo(String info, Locale locale) {
        super (info, locale);
    }

    /**
     * Returns whether this printer info attribute is equivalent to the passed
     * in object. To be equivalent, all of the following conditions must be
     * true:
     * <OL TYPE=1>
     * <LI>
     * {@code object} is not null.
     * <LI>
     * {@code object} is an instance of class PrinterInfo.
     * <LI>
     * This printer info attribute's underlying string and
     * {@code object}'s underlying string are equal.
     * <LI>
     * This printer info attribute's locale and {@code object}'s
     * locale are equal.
     * </OL>
     *
     * @param  object  Object to compare to.
     *
     * @return  True if {@code object} is equivalent to this printer
     *          info attribute, false otherwise.
     */
    public boolean equals(Object object) {
        return (super.equals(object) && object instanceof PrinterInfo);
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrinterInfo, the category is class PrinterInfo itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrinterInfo.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrinterInfo, the category name is {@code "printer-info"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "printer-info";
    }

}
