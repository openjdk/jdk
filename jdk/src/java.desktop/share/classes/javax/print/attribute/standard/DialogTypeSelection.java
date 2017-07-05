/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

import javax.print.attribute.EnumSyntax;
import javax.print.attribute.Attribute;
import javax.print.attribute.PrintRequestAttribute;

/**
 * Class DialogTypeSelection is a printing attribute class, an enumeration,
 * that indicates the user dialog type to be used for specifying
 * printing options.
 * If {@code NATIVE} is specified, then where available, a native
 * platform dialog is displayed.
 * If {@code COMMON} is specified, a cross-platform print dialog is displayed.
 *
 * This option to specify a native dialog for use with an IPP attribute
 * set provides a standard way to reflect back of the setting and option
 * changes made by a user to the calling application, and integrates
 * the native dialog into the Java printing APIs.
 * But note that some options and settings in a native dialog may not
 * necessarily map to IPP attributes as they may be non-standard platform,
 * or even printer specific options.
 * <P>
 * <B>IPP Compatibility:</B> This is not an IPP attribute.
 *
 * @since 1.7
 */
public final class DialogTypeSelection extends EnumSyntax
        implements PrintRequestAttribute {

    private static final long serialVersionUID = 7518682952133256029L;

    /**
     *
     */
    public static final DialogTypeSelection
        NATIVE = new DialogTypeSelection(0);

    /**
     *
     */
    public static final DialogTypeSelection
        COMMON = new DialogTypeSelection(1);

    /**
     * Construct a new dialog type selection enumeration value with the
     * given integer value.
     *
     * @param  value  Integer value.
     */
    protected DialogTypeSelection(int value) {
                super(value);
    }

    private static final String[] myStringTable = {
        "native", "common"};


    private static final DialogTypeSelection[] myEnumValueTable = {
        NATIVE,
        COMMON
    };

    /**
     * Returns the string table for class DialogTypeSelection.
     */
    protected String[] getStringTable() {
        return myStringTable;
    }

    /**
     * Returns the enumeration value table for class DialogTypeSelection.
     */
    protected EnumSyntax[] getEnumValueTable() {
        return myEnumValueTable;
    }


   /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class DialogTypeSelection the category is class
     * DialogTypeSelection itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return DialogTypeSelection.class;
    }


    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class DialogTypeSelection the category name is
     * <CODE>"dialog-type-selection"</CODE>.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "dialog-type-selection";
    }

}
