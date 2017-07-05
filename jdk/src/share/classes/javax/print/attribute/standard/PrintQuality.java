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

import javax.print.attribute.Attribute;
import javax.print.attribute.EnumSyntax;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class PrintQuality is a printing attribute class, an enumeration,
 * that specifies the print quality that the printer uses for the job.
 * <P>
 * <B>IPP Compatibility:</B> The category name returned by
 * <CODE>getName()</CODE> is the IPP attribute name.  The enumeration's
 * integer value is the IPP enum value.  The <code>toString()</code> method
 * returns the IPP string representation of the attribute value.
 * <P>
 *
 * @author  David Mendenhall
 * @author  Alan Kaminsky
 */
public class PrintQuality extends EnumSyntax
    implements DocAttribute, PrintRequestAttribute, PrintJobAttribute {

    private static final long serialVersionUID = -3072341285225858365L;
    /**
     * Lowest quality available on the printer.
     */
    public static final PrintQuality DRAFT = new PrintQuality(3);

    /**
     * Normal or intermediate quality on the printer.
     */
    public static final PrintQuality NORMAL = new PrintQuality(4);

    /**
     * Highest quality available on the printer.
     */
    public static final PrintQuality HIGH = new PrintQuality(5);

    /**
     * Construct a new print quality enumeration value with the given integer
     * value.
     *
     * @param  value  Integer value.
     */
    protected PrintQuality(int value) {
        super (value);
    }

    private static final String[] myStringTable = {
        "draft",
        "normal",
        "high"
    };

    private static final PrintQuality[] myEnumValueTable = {
        DRAFT,
        NORMAL,
        HIGH
    };

    /**
     * Returns the string table for class PrintQuality.
     */
    protected String[] getStringTable() {
        return (String[])myStringTable.clone();
    }

    /**
     * Returns the enumeration value table for class PrintQuality.
     */
    protected EnumSyntax[] getEnumValueTable() {
        return (EnumSyntax[])myEnumValueTable.clone();
    }

    /**
     * Returns the lowest integer value used by class PrintQuality.
     */
    protected int getOffset() {
        return 3;
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class PrintQuality and any vendor-defined subclasses, the category is
     * class PrintQuality itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return PrintQuality.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class PrintQuality and any vendor-defined subclasses, the category
     * name is <CODE>"print-quality"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "print-quality";
    }

}
